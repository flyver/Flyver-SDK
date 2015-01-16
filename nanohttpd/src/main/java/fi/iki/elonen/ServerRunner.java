package fi.iki.elonen;

import android.util.Log;

import java.io.IOException;

public class ServerRunner {
    public static void run(Class serverClass) {
        try {
            executeInstance((NanoHTTPD) serverClass.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executeInstance(NanoHTTPD server) {
        try {
            Log.d("SERVER_RUNNER", "WebServer started");
            server.start();
        } catch (IOException ioe) {
            Log.e("SERVER_RUNNER", "Couldn't start server:\n" + ioe);
            System.exit(-1);
        }
    }
}
