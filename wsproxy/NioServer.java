package wsproxy;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;


/**
 * Created by adecusati on 3/4/2016.
 */
public class NioServer implements Runnable {
    // Websocket URI
    private URI wsURI;

    // The host:port combination to listen on
    private int port;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;

    // The selector we'll be monitoring
    private Selector selector;

    // The buffer into which we'll read data when it's available
    private ByteBuffer readBuffer = ByteBuffer.allocate(10240);

    // A list of ChangeRequest instances
    private List changeRequests = new LinkedList();

    // Maps a SocketChannel to a list of ByteBuffer instances
    private Map pendingData = new HashMap();

    public NioServer(URI wsURI, int port) throws IOException {
        this.wsURI = wsURI;
        this.port = port;
        this.selector = this.initSelector();
    }

    private Selector initSelector() throws IOException {
        // Create a new selector
        Selector socketSelector = SelectorProvider.provider().openSelector();

        // Create a new non-blocking server socket channel
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // Bind the server socket to the port on the loopback address
        InetSocketAddress isa = new InetSocketAddress(InetAddress.getLoopbackAddress(), this.port);
        serverChannel.socket().bind(isa);

        // Register the server socket channel, indicating an interest in
        // accepting new connections
        serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        return socketSelector;
    }

    private void accept(SelectionKey key) throws IOException, DeploymentException, NoSuchAlgorithmException, KeyManagementException {
        System.out.println("Socket accepting connection");
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        Socket socket = socketChannel.socket();
        socketChannel.configureBlocking(false);

        // Attach the web socket proxy
        // Create a trust manager that does not validate certificate chains
        WebsocketProxy wsp = new WebsocketProxy(this.wsURI ,socketChannel, this);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ, wsp);
        System.out.printf("Socket connected: %s:%d<=>%s:%d\n",socket.getLocalAddress(), socket.getLocalPort(), socket.getInetAddress(), socket.getPort());
    }

    private void read(SelectionKey key) throws IOException {
        //System.out.println("Socket reading data");
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Clear out our read buffer so it's ready for new data
        this.readBuffer.clear();

        // Attempt to read off the channel
        int numRead;
        byte[] bytes;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            System.err.println("Socket remote forced closed connection");
            ((WebsocketProxy) key.attachment()).close();

            synchronized(this.changeRequests) {
                this.changeRequests.clear();
            }
            key.cancel();
            socketChannel.close();
            return;
        }
        //System.out.printf("Socket reading data: %s\n", Base64.encode(bytes));
        //System.out.printf("Socket read %d bytes\n", bytes.length);

        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            System.out.println("Socket remote closed clean");
            ((WebsocketProxy) key.attachment()).close();
            synchronized(this.changeRequests) {
                this.changeRequests.clear();
            }
            key.channel().close();
            key.cancel();
            return;
        }

        // Hand the data off to our worker thread
        if( key.attachment() != null ) {
            //System.out.println("Socket sending bytes to websocket");
            WebsocketProxy wp = (WebsocketProxy) key.attachment();
            bytes = Arrays.copyOf(this.readBuffer.array(),numRead);
            wp.sendBytes(bytes);
            //System.out.println("Socket sending complete");
        } else {
            System.err.println("Socket key detached!");
        }
    }

    private void write(SelectionKey key) throws IOException {
        //System.out.println("Socket writing data");
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            int numBytes = 0;
            List queue = (List) this.pendingData.get(socketChannel);

            // Write until there's not more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                numBytes += socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }
            //System.out.printf("Socket wrote %d bytes\n", numBytes);
        }//

    }

    public void sendBytes(SocketChannel socket, byte[] bytes) {
        synchronized (this.changeRequests) {
            // Indicate we want the interest ops set changed
            this.changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData) {
                List queue = (List) this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(bytes));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    public void run() {
        while (true) {
            try {
                // Process any pending changes
                synchronized(this.changeRequests) {
                    Iterator changes = this.changeRequests.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch(change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.changeRequests.clear();
                }

                // Wait for an event one of the registered channels
                this.selector.select();

                // Iterate over the set of keys for which events are available
                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class ChangeRequest {
        public static final int REGISTER = 1;
        public static final int CHANGEOPS = 2;

        public SocketChannel socket;
        public int type;
        public int ops;

        public ChangeRequest(SocketChannel socket, int type, int ops) {
            this.socket = socket;
            this.type = type;
            this.ops = ops;
        }
    }
}