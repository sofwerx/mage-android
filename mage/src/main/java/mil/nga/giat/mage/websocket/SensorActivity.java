package mil.nga.giat.mage.websocket;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

import mil.nga.giat.mage.R;

public class SensorActivity extends Activity implements OnMapReadyCallback {

    public static final String SENSOR_DATA = "SensorData";
    private GoogleMap map;
    private String singleUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);
        singleUrl = getIntent().getStringExtra(SensorActivity.SENSOR_DATA);

        ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);

// http://sensiasoft.net:8181/
// &offering=urn:android:device:a0e0eac2fea3f614-sos
// &observedProperty=http://sensorml.com/ont/swe/property/Location
// &observedProperty=http://sensorml.com/ont/swe/property/VideoFrame
// 		&temporalFilter=phenomenonTime,2016-04-25T21:16:36.731Z/2016-04-27T17:00:38Z
// 		&replaySpeed=2

        String videoUrl = singleUrl;
        String [] tokens = StringUtils.split(videoUrl, '&');
        videoUrl = tokens[0];
        //sensorUrl = sensorUrl.replaceFirst("http://","ws://");

        String sensorOffering = tokens[1];
        videoUrl = videoUrl + "sensorhub/sos?service=SOS&version=2.0&request=GetResult&" + sensorOffering;
        videoUrl = videoUrl + "&" + tokens[3] + "&" + tokens[4] + "&" + tokens[5];


        Uri sensorUri = null;
        try {
            sensorUri = Uri.parse(videoUrl);
            //VideoView sensorVideo = (VideoView) findViewById(R.id.videoView);
            //sensorVideo.setVideoURI(sensorUri);
            //sensorVideo.start();

            WebView sensorVideo = (WebView) findViewById(R.id.webView);
            sensorVideo.getSettings().setLoadWithOverviewMode(true);
            sensorVideo.getSettings().setUseWideViewPort(true);
            sensorVideo.loadUrl(videoUrl);

        } catch (Exception ex) {
            ex.printStackTrace();
        }


    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        String locationUrl = singleUrl;
        //Update the sensor URL
        String [] tokens = StringUtils.split(locationUrl, '&');
        locationUrl = tokens[0];
        locationUrl = locationUrl.replaceFirst("http://","ws://");

        String sensorOffering = tokens[1];
        locationUrl = locationUrl + "sensorhub/sos?service=SOS&version=2.0&request=GetResult&" + sensorOffering;
        locationUrl = locationUrl + "&" + tokens[2] + "&" + tokens[4] + "&" + tokens[5];


        URI sensorUri = null;
        try {
            sensorUri =  URI.create(locationUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SensorWebSocket sensorWebSocket = new SensorWebSocket(sensorUri, map, this, true);

    }


}
