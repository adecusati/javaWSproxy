/**
 * Created by adecusati on 3/1/2016.
 */

import wsproxy.NioServer;
import wsproxy.WebsocketProxy;

import java.io.IOException;
import java.net.*;

public class main {
    public static void main(String[] args) throws URISyntaxException {
        if( args.length < 2 ) {
            System.out.println("Usage: dewebsockify ws://someserver/path <port>");
            return;
        }

        URI uri = new URI(args[0]);
        Integer port = Integer.parseInt(args[1]) ;

        try {
            new Thread(new NioServer(uri, port)).start();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }

    }

}
