package mil.nga.giat.mage.websocket;

import android.app.Activity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public class SensorWebSocket {

    GoogleMap map;
    Marker sensorMarker;
    Activity activity;
    WebSocket webSocket;
    boolean followOnMarker = false;

    public SensorWebSocket(URI uri, GoogleMap map, final Activity activity, boolean followOnMarker) {
        this.activity = activity;
        this.followOnMarker = followOnMarker;
        this.map = map;

        sensorMarker = map.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Sensor").visible(false));

        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), "", new AsyncHttpClient.WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
               socketCreated(webSocket);

            }
        });

    }

    public void socketCreated(WebSocket webSocket) {

        this.webSocket = webSocket;

        webSocket.setDataCallback(new DataCallback() {
            public void onDataAvailable(DataEmitter emitter, ByteBufferList byteBufferList) {
                String[] results = StringUtils.split(byteBufferList.readString(), ",");
                String timestamp = results[0];
                final double lat = Double.parseDouble(results[1]);
                final double lng = Double.parseDouble(results[2]);
                final double alt = Double.parseDouble(results[3]);

                //Add marker to the map
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            sensorMarker.setVisible(true);
                            sensorMarker.setPosition(new LatLng(lat, lng));
                            sensorMarker.showInfoWindow();
                            System.out.print("lat:" + lat + " lng:" + lng);
                            if (followOnMarker) {
                                LatLng location = new LatLng(lat, lng);
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18));
                            }

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                    }
                });

                // note that this data has been read
                byteBufferList.recycle();
            }
        });
    }

    public void closeSocket() {
        webSocket.close();
    }


    public Marker getMarker() {
        return sensorMarker;
    }


}
