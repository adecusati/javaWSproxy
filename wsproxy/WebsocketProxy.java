package wsproxy;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@ClientEndpoint(subprotocols = "binary")
public class WebsocketProxy {

    private SocketChannel socket;
    private NioServer nioServer;
    private Session session;

    public WebsocketProxy(URI endpointURI, SocketChannel socket, NioServer nioServer) throws IOException, DeploymentException, NoSuchAlgorithmException, KeyManagementException {
        System.out.printf("Websocket connecting to %s\n",endpointURI);
        this.socket = socket;
        this.nioServer = nioServer;
        this.session = ContainerProvider.getWebSocketContainer().connectToServer(this, endpointURI);
        System.out.println("Websocket created");
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        System.out.println("Websocket opened");
        this.session=session;
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("Websocket Closing");
        this.session=null;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Websocket closed");
    }

    @OnError
    public void onError(Session session, Throwable e) {
        System.err.printf("Error on websocket %s\n%s\n",session.getRequestURI(),e);
        e.printStackTrace();
    }

    @OnMessage
    public void onMessage(byte[] bytes) {
        //System.out.println("Websocket receiving bytes");
        nioServer.sendBytes(socket,bytes);
        //System.out.printf("Websocket received %d bytes\n", bytes.length);
    }

    /**
     * Send a message.
     *
     * @param bytes
     */
    public void sendBytes(byte[] bytes) {
        //System.out.println("Websocket sending bytes");
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        this.session.getAsyncRemote().sendBinary(bb);
        //System.out.printf("Websocket sent %d bytes\n",bytes.length);
    }

    public void close() throws IOException {
        this.session.close();
    }

}
