package mil.nga.giat.mage.websocket;

import com.google.android.gms.maps.GoogleMap;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.FrameBuilder;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;

public class SensorWebSocket extends WebSocketClient {

    GoogleMap map;

    public SensorWebSocket(URI uri, GoogleMap map) {
        super(uri);
        this.map = map;
    }

    @Override
    public void onMessage(String message) {
        System.out.println("onMesasge(String): " + message);
       // send(message);
    }

    @Override
    public void onMessage(ByteBuffer blob) {
        System.out.println("onMessage(blob): " + blob.toString());
        //getConnection().send(blob);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("Error: ");
        ex.printStackTrace();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Closed: " + code + " " + reason);
    }

    @Override
    public void onWebsocketMessageFragment(WebSocket conn, Framedata frame) {
        FrameBuilder builder = (FrameBuilder) frame;
        builder.setTransferemasked(true);
        getConnection().sendFrame(frame);
    }


}
