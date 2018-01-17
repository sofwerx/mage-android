package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.os.AsyncTask;
import android.support.annotation.MainThread;

import com.google.android.gms.maps.GoogleMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Created by wnewman on 2/11/16.
 */
@MainThread
public class CacheManager {

    /**
     * Dynamically provide a list of standard locations to search for available caches.
     * This is primarily intended to support changing external SD cards and refreshing
     * caches from there, but the concept could be extended to remote URLs as well or
     * other file sources.
     */
    public interface CacheLocationProvider {
        List<File> getLocalSearchDirs();
    }

    /**
     * Implement this interface and {@link #registerCacheOverlayListener(CacheOverlaysUpdateListener) register}
     * an instance to receive {@link #onCacheOverlaysUpdated(CacheOverlayUpdate) notifications} when the set of caches changes.
     */
    public interface CacheOverlaysUpdateListener {
        void onCacheOverlaysUpdated(CacheOverlayUpdate update);
    }

    // TODO: will need this to restore functionality of identifying an explicitly added cache
    // through the sharing/open-with mechanism and zooming the map to it
    public static final class CacheOverlayUpdate {
        public final Set<MapCache> added;
        public final Set<MapCache> updated;
        public final Set<MapCache> removed;
        public final Set<MapCache> allAvailable;

        private CacheOverlayUpdate(Set<MapCache> added, Set<MapCache> updated, Set<MapCache> removed, Set<MapCache> allAvailable) {
            this.added = added;
            this.updated = updated;
            this.removed = removed;
            this.allAvailable = allAvailable;
        }
    }

    private static final String LOG_NAME = CacheManager.class.getName();

    private static CacheManager instance = null;

    public static class Config {
        private Application context;
        private CacheLocationProvider cacheLocations;
        private List<CacheProvider> providers = new ArrayList<>();
        private Executor executor = AsyncTask.SERIAL_EXECUTOR;

        public Config context(Application x) {
            context = x;
            return this;
        }

        /**
         * Paths to directories to search for cache files
         * @param x
         * @return
         */
        public Config cacheLocations(CacheLocationProvider x) {
            cacheLocations = x;
            return this;
        }

        public Config providers(CacheProvider... x) {
            providers.addAll(Arrays.asList(x));
            return this;
        }

        public Config executor(Executor x) {
            executor = x;
            return this;
        }
    }

    public static synchronized void initialize(Config config) {
        if (instance == null) {
            instance = new CacheManager(config);
            return;
        }
        throw new Error("attempt to initialize " + CacheManager.class + " singleton more than once");
    }

    public static CacheManager getInstance() {
        return instance;
    }

    private final Application context;
    private final Executor executor;
    private final CacheLocationProvider cacheLocations;
    private final List<CacheProvider> providers = new ArrayList<>();
    private final Collection<CacheOverlaysUpdateListener> cacheOverlayListeners = new ArrayList<>();
    private Set<MapCache> caches = Collections.emptySet();
    private RefreshAvailableCachesTask refreshTask;
    private FindNewCacheFilesInProvidedLocationsTask findNewCacheFilesTask;
    private ImportCacheFileTask importCacheFilesForRefreshTask;

    public CacheManager(Config config) {
        context = config.context;
        executor = config.executor;
        cacheLocations = config.cacheLocations;
        providers.addAll(config.providers);
    }

    public void tryImportCacheFile(File cacheFile) {
        new ImportCacheFileTask().executeOnExecutor(executor, cacheFile);
    }

    public void registerCacheOverlayListener(CacheOverlaysUpdateListener listener) {
        cacheOverlayListeners.add(listener);
    }

    public void removeCacheOverlay(String name) {
        // TODO: rename to delete, implement CacheProvider.deleteCache()
    }

    public void unregisterCacheOverlayListener(CacheOverlaysUpdateListener listener) {
        cacheOverlayListeners.remove(listener);
    }

    public Set<MapCache> getCaches() {
        return caches;
    }

    /**
     * Discover new caches available in standard {@link #cacheLocations locations}, then remove defunct caches.
     * Asynchronous notifications to {@link #registerCacheOverlayListener(CacheOverlaysUpdateListener) listeners}
     * will result, one notification per refresh, per listener.  Only one refresh can be active at any moment.
     */
    public void refreshAvailableCaches() {
        if (refreshTask != null) {
            return;
        }
        findNewCacheFilesTask = new FindNewCacheFilesInProvidedLocationsTask();
        importCacheFilesForRefreshTask = new ImportCacheFileTask();
        refreshTask = new RefreshAvailableCachesTask();
        findNewCacheFilesTask.executeOnExecutor(executor);
    }

    public OverlayOnMapManager createMapManager(GoogleMap map) {
        return new OverlayOnMapManager(this, providers, map);
    }

    private void findNewCacheFilesFinished(FindNewCacheFilesInProvidedLocationsTask task) {
        if (task != findNewCacheFilesTask) {
            throw new IllegalStateException(FindNewCacheFilesInProvidedLocationsTask.class.getSimpleName() + " task finished but did not match stored task");
        }
        try {
            importCacheFilesForRefreshTask.executeOnExecutor(executor, task.get());
        }
        catch (Exception e) {
            throw new IllegalStateException("interrupted while retrieving new cache files to import");
        }
    }

    private void cacheFileImportFinished(ImportCacheFileTask task) {
        if (task == importCacheFilesForRefreshTask) {
            if (refreshTask == null) {
                throw new IllegalStateException("import task for refresh finished but refresh task is null");
            }
            refreshTask.executeOnExecutor(executor, caches);
        }
        else {
            updateCaches(task, null);
        }
    }

    private void refreshFinished(RefreshAvailableCachesTask task) {
        if (task != refreshTask) {
            throw new IllegalStateException(RefreshAvailableCachesTask.class.getSimpleName() + " task completed but did not match stored task");
        }

        ImportCacheFileTask localImportTask = importCacheFilesForRefreshTask;
        RefreshAvailableCachesTask localRefreshTask = refreshTask;
        importCacheFilesForRefreshTask = null;
        findNewCacheFilesTask = null;
        refreshTask = null;

        updateCaches(localImportTask, localRefreshTask);
    }

    private void updateCaches(ImportCacheFileTask importTask, RefreshAvailableCachesTask refreshTask) {
        Set<MapCache> allIncoming;
        try {
            CacheImportResult importResult = importTask.get();
            allIncoming = importResult.imported;
            if (refreshTask != null) {
                allIncoming.addAll(refreshTask.get());
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("unexpected error retrieving cache update results", e);
        }

        Map<MapCache, MapCache> incomingIndex = new HashMap<>();
        for (MapCache cache : allIncoming) {
            incomingIndex.put(cache, cache);
        }
        Set<MapCache> added = new HashSet<>(allIncoming);
        added.removeAll(caches);
        Set<MapCache> removed = new HashSet<>();
        Set<MapCache> updated = new HashSet<>();
        for (MapCache existing : caches) {
            MapCache incoming = incomingIndex.get(existing);
            if (incoming == null) {
                removed.add(existing);
            }
            else if (incoming != existing) {
                updated.add(incoming);
            }
        }

        caches = Collections.unmodifiableSet(new HashSet<>(incomingIndex.keySet()));

        CacheOverlayUpdate update = new CacheOverlayUpdate(
            Collections.unmodifiableSet(added),
            Collections.unmodifiableSet(updated),
            Collections.unmodifiableSet(removed),
            caches);
        for (CacheOverlaysUpdateListener listener : cacheOverlayListeners) {
            listener.onCacheOverlaysUpdated(update);
        }
    }

    private static class CacheImportResult {
        private final Set<MapCache> imported;
        // TODO: propagate failed imports to user somehow
        private final List<CacheImportException> failed;

        private CacheImportResult(Set<MapCache> imported, List<CacheImportException> failed) {
            this.imported = imported;
            this.failed = failed;
        }
    }

    private class ImportCacheFileTask extends AsyncTask<File, Void, CacheImportResult> {

        private MapCache importFromFirstCapableProvider(File cacheFile) throws CacheImportException {
            for (CacheProvider provider : providers) {
                if (!cacheFile.canRead()) {
                    throw new CacheImportException(cacheFile, "cache file is not readable or does not exist: " + cacheFile.getName());
                }
                if (provider.isCacheFile(cacheFile)) {
                    return provider.importCacheFromFile(cacheFile);
                }
            }
            throw new CacheImportException(cacheFile, "no cache provider could handle file " + cacheFile.getName());
        }

        @Override
        protected CacheImportResult doInBackground(File... files) {
            Set<MapCache> caches = new HashSet<>(files.length);
            List<CacheImportException> fails = new ArrayList<>(files.length);
            for (File cacheFile : files) {
                MapCache imported = null;
                try {
                    imported = importFromFirstCapableProvider(cacheFile);
                    caches.add(imported);
                }
                catch (CacheImportException e) {
                    fails.add(e);
                }
            }
            return new CacheImportResult(caches, fails);
        }

        @Override
        protected void onPostExecute(CacheImportResult result) {
            cacheFileImportFinished(this);
        }
    }

    private final class RefreshAvailableCachesTask extends AsyncTask<Set<MapCache>, Void, Set<MapCache>> {

        @Override
        protected Set<MapCache> doInBackground(Set<MapCache>... params) {
            Map<Class<? extends CacheProvider>, Set<MapCache>> cachesByProvider = new HashMap<>(providers.size());
            Set<MapCache> existingCaches = params[0];
            for (MapCache cache : existingCaches) {
                Set<MapCache> providerCaches = cachesByProvider.get(cache.getType());
                if (providerCaches == null) {
                    providerCaches = new HashSet<>();
                    cachesByProvider.put(cache.getType(), providerCaches);
                }
                providerCaches.add(cache);
            }
            Set<MapCache> caches = new HashSet<>();
            for (CacheProvider provider : providers) {
                Set<MapCache> providerCaches = cachesByProvider.get(provider.getClass());
                if (providerCaches == null) {
                    providerCaches = Collections.emptySet();
                }
                caches.addAll(provider.refreshCaches(providerCaches));
            }
            return caches;

            // TODO: move this to OverlayOnMapManager, or some such map-specific linkage
            // but for now i think just save the set of cache files to preferences to re-create
            // after next launch.  at some point switch to urls instead of file paths to maybe
            // support more than local files for cached overlays, which at that point i suppose
            // would not be cached.
            // TODO: later maybe store to a Room database

            // Set what should be enabled based on preferences.
//            boolean update = false;
//            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
//            Set<String> updatedEnabledOverlays = new HashSet<>();
//            updatedEnabledOverlays.addAll(preferences.getStringSet(context.getString(R.string.tileOverlaysKey), Collections.<String>emptySet()));
//            Set<String> enabledOverlays = new HashSet<>();
//            enabledOverlays.addAll(updatedEnabledOverlays);
//
//            // Determine which caches are enabled
//            for (CacheOverlay cacheOverlay : overlays) {
//
//                // Check and enable the cache
//                String cacheName = cacheOverlay.getOverlayName();
//                if (enabledOverlays.remove(cacheName)) {
//                    cacheOverlay.setEnabled(true);
//                }
//
//                // Check the child caches
//                for (CacheOverlay childCache : cacheOverlay.getChildren()) {
//                    if (enabledOverlays.remove(childCache.getOverlayName())) {
//                        childCache.setEnabled(true);
//                        cacheOverlay.setEnabled(true);
//                    }
//                }
//
//                // Check for new caches to enable in the overlays and preferences
//                if (overlaysToEnable.contains(cacheName)) {
//
//                    update = true;
//                    cacheOverlay.setEnabled(true);
//                    cacheOverlay.setAdded(true);
//                    if (cacheOverlay.isSupportsChildren()) {
//                        for (CacheOverlay childCache : cacheOverlay.getChildren()) {
//                            childCache.setEnabled(true);
//                            updatedEnabledOverlays.add(childCache.getOverlayName());
//                        }
//                    } else {
//                        updatedEnabledOverlays.add(cacheName);
//                    }
//                }
//            }
//
//            // Remove overlays in the preferences that no longer exist
//            if (!enabledOverlays.isEmpty()) {
//                updatedEnabledOverlays.removeAll(enabledOverlays);
//                update = true;
//            }
//
//            // If new enabled cache overlays, update them in the preferences
//            if (update) {
//                SharedPreferences.Editor editor = preferences.edit();
//                editor.putStringSet(context.getString(R.string.tileOverlaysKey), updatedEnabledOverlays);
//                editor.apply();
//            }
        }

        @Override
        protected void onPostExecute(Set<MapCache> caches) {
            refreshFinished(this);
        }
    }

    private final class FindNewCacheFilesInProvidedLocationsTask extends AsyncTask<Void, Void, File[]> {

        @Override
        protected File[] doInBackground(Void... voids) {
            List<File> searchDirs = cacheLocations.getLocalSearchDirs();
            List<File> potentialCaches = new ArrayList<>();
            for (File dir : searchDirs) {
                File[] files = dir.listFiles();
                potentialCaches.addAll(Arrays.asList(files));
            }
            return potentialCaches.toArray(new File[potentialCaches.size()]);
        }

        @Override
        protected void onPostExecute(File[] files) {
            findNewCacheFilesFinished(this);
        }
    }
}
