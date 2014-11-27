package co.flyver.Client;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.flyver.IPC.JSONUtils;


/**
 * Created by Petar Petrov on 10/1/14.
 */
public class Client extends IntentService implements ServerListener.Callback {

    public class LocalBinder extends Binder {
        public Client getInstance() {
            return Client.this;
        }
    }

    public interface ConnectionHooks {
        public void onConnect();
        public void onDisconnect();
    }

    public interface ClientCallback {
        public void run(String json);
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static final String CLIENT = "CLIENT";
    private static final String INTENT = "INTENT";
    private static PrintWriter sStreamToServer;
    private static BufferedReader sStreamFromServer;
    private static Toast sLastToast;
    private static String sServerIP;
    private static Context sMainContext;
    private static HashMap<String, ClientCallback> sCallbacks = new HashMap<>();
    private static ServerListener mServerListener = new ServerListener();
    private static ConnectionHooks sConnectionHooks;
    private static Thread sHeartbeatMonitorThread;
    private static HeartbeatMonitor sHeartbeatMonitor = new HeartbeatMonitor();
    private static Socket mConnection;
    private static Thread sServerListenerThread;
    private static boolean mConnected = false;
    private IBinder mBinder = new LocalBinder();

    public Client() {
        super("Client");
    }

    public static void setServerIP(String sServerIP) {
        Client.sServerIP = sServerIP;
    }

    public static void setLastToast(Toast sLastToast) {
        Client.sLastToast = sLastToast;
    }

    public static void setMainContext(Context sMainContext) {
        Client.sMainContext = sMainContext;
    }

    public static void registerCallback(String key, ClientCallback callback) {
        if (callback != null) {
            sCallbacks.put(key, callback);
        }
    }

    public static void registerConnectionHooks(ConnectionHooks hooks) {
        sConnectionHooks = hooks;
    }

    public static void sendMsg(String json) {
        if(!JSONUtils.validateJson(json)) {
            Log.e(CLIENT, "Invalid JSON provided to sendMsg");
        }
        if (sStreamToServer == null) {
            return;
        }
        sStreamToServer.println(json);
        sStreamToServer.flush();
    }

    private static void displayToast(String message) {
        if (sLastToast != null) {
            sLastToast.cancel();
        }
        sLastToast = Toast.makeText(sMainContext, message, Toast.LENGTH_SHORT);
        sLastToast.show();
    }

    /**
     * Opens the sockets and streams to the server
     */
    private void init() {
        try {
            mConnection = new Socket(sServerIP, 51342);
            sStreamToServer = new PrintWriter(mConnection.getOutputStream(), true);
            sStreamFromServer = new BufferedReader(new InputStreamReader(mConnection.getInputStream()));
            Log.d(CLIENT, "Connection initialized");
            mServerListener.registerCallback(this).registerInputStream(sStreamFromServer);

            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    displayToast("Connected");
                    //Run the onConnect hook on the main thread, to allow altering the UI from it
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            sConnectionHooks.onConnect();
                        }
                    });
                    Looper.loop();
                }
            }.start();

            sServerListenerThread = new Thread(mServerListener);
            sServerListenerThread.start();

            sHeartbeatMonitorThread = new Thread(sHeartbeatMonitor.setOnHeartbeatMissedHook(new Runnable() {
                @Override
                public void run() {
                    disconnect();
                }
            }));
            sHeartbeatMonitorThread.start();

        } catch (IOException e) {

            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    displayToast("Server is not started");
                    mConnected = false;
                    Looper.loop();
                }
            }.start();

            e.printStackTrace();
        }
    }

    private void disconnect() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                displayToast("Server has disconnected");
            }
        });
        try {
            mConnection.shutdownInput();
            mConnection.shutdownOutput();
            //Perform read on closed stream to raise exception and close the socket
            //noinspection StatementWithEmptyBody
            while (mConnection.getInputStream().read() >= 0) ;
            mConnection.close();
            mConnection = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        mServerListener.stop();
        mServerListener = new ServerListener();
        sStreamToServer = null;
        sStreamFromServer = null;
        mConnection = null;
        sServerListenerThread = null;
        sServerListenerThread = new Thread();
        mConnected = false;
        //Run the onDisconnect hook on the UI thread to allow modifying the UI from it
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                sConnectionHooks.onDisconnect();
            }
        });
        stopSelf();
    }

    private static void onHeartbeatReceived(String json) {
        sHeartbeatMonitor.heartbeatReceived();
    }

    @Override
    public String onDataReceived(String json) {
        if (json == null) {
            return null;
        }
        Pattern mPattern = Pattern.compile("\\{.+(?=key)\\w+\":(\"\\w+\")\\}?.+\\}?$");
        Matcher matcher = mPattern.matcher(json);
        if (JSONUtils.validateJson(json) && matcher.matches()) {
            String mKey = matcher.group(1);
            mKey = mKey.replace('"', ' ').trim();
            if (mKey.equals("heartbeat")) {
                onHeartbeatReceived(json);
                return json;
            }
            if (sCallbacks.containsKey(mKey)) {
                sCallbacks.get(mKey).run(json);
            }
        } else {
            Log.w(CLIENT, "Invalid or malformed JSON: " + json);
        }
        return json;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    /**
     * Entry point for the client
     */
    protected void onHandleIntent(Intent intent) {
        if (mConnected) {
            return;
        }
        mConnected = true;
        Log.d(INTENT, "Intent received ");
        init();
    }
}
