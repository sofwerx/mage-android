package mil.nga.giat.mage.websocket;

import com.google.android.gms.maps.GoogleMap;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public class SensorWebSocket {

    GoogleMap map;

    public SensorWebSocket(URI uri, GoogleMap map) {

        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), "", new AsyncHttpClient.WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }

                webSocket.setDataCallback(new DataCallback() {
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList byteBufferList) {
                        String[] results = StringUtils.split(byteBufferList.readString(), ",");
                        String timestamp = results[0];
                        double lat = Double.parseDouble(results[1]);
                        double lng = Double.parseDouble(results[2]);
                        double alt = Double.parseDouble(results[3]);

                        // note that this data has been read
                        byteBufferList.recycle();
                    }
                });
            }
        });

    }



}
