package co.flyver.Client;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Created by flyver on 11/26/14.
 */
public class ServerListener implements Runnable {

    public interface Callback {
        public String onDataReceived(String json);
    }

    private final static String LISTENER = "ServerListener";
    private String json;
    private Callback callback;
    BufferedReader bufferedReader;
    private boolean stopped = false;

    ServerListener() {
        //empty on purpose
    }

    ServerListener(Callback callback) {
        this.callback = callback;
    }

    ServerListener(BufferedReader bufferedReader, Callback callback) {
        this.bufferedReader = bufferedReader;
        this.callback = callback;
    }

    public ServerListener registerCallback(Callback callback) {
        this.callback = callback;
        return this;
    }

    public ServerListener registerInputStream(BufferedReader bufferedReader) {
        this.bufferedReader = bufferedReader;
        return this;
    }

    public void stop() {
        stopped = true;
    }

    @Override
    public void run() {
        Log.d(LISTENER, "Started");
        //noinspection InfiniteLoopStatement
        while(true) {
            if(stopped) {
                return;
            }
            try {
                json = bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            callback.onDataReceived(json);
        }
    }
}
