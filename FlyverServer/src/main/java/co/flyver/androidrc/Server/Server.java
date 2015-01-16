package co.flyver.androidrc.Server;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.flyver.IPC.IPCKeys;
import co.flyver.IPC.JSONUtils;
import co.flyver.androidrc.Server.interfaces.ServerCallback;
import co.flyver.dataloggerlib.LoggerService;
import co.flyver.utils.NanoHTTPDServer;
import fi.iki.elonen.ServerRunner;

import static co.flyver.IPC.IPCContainers.JSONQuadruple;
import static co.flyver.IPC.IPCContainers.JSONTriple;
import static co.flyver.IPC.IPCContainers.JSONTuple;


/**
 * Created by Petar Petrov on 10/2/14.
 */
public class Server extends IntentService {

    private static final String SERVER = "SERVER";
    private static final String EV_DEBUG = "DEBUG";
    private static final String EV_RAWDATA = "RAWDATA";

    public static Status sCurrentStatus = new Status();
    Gson mGson = new Gson();
    static BufferedReader mStreamFromClient;
    static PrintWriter mStreamToClient;
    JSONQuadruple<String, Float, Float, Float> mJsonCoordinates = new JSONQuadruple<>();
    JSONTriple<String, String, Float> mJsonAction = new JSONTriple<>();
    JSONQuadruple<String, Float, Float, Float> mJsonPid = new JSONQuadruple<>();
    JSONTuple<String, String> mJsonTuple = new JSONTuple<>();
    static HashMap<String, ServerCallback> mCallbacks = new HashMap<>();
    IBinder mBinder = new LocalBinder();
    CameraProvider mCameraProvider;
    byte[] mPicture;
    ServerSocket mServerSocket;
    Socket mConnection;
    Timer mHeartbeat = new Timer();
    LoggerService logger;

    public Server() {
        super("Server");
    }

    public void setCameraProvider(CameraProvider cameraProvider) {
        this.mCameraProvider = cameraProvider;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public Server getServerInstance() {
            return Server.this;
        }
    }

    /**
     * Supply a runnable to be executed when a JSON with the appropriate key is received
     * Refer to the IPC/IPCKeys class for the valid keys
     * @param key - String, key associated with the runnable
     * @param callback - Runnable to be executed
     */
    public static void registerCallback(String key, ServerCallback callback) {
        if(callback == null) {
            Log.d(SERVER, "Runnable provided as callback is null");
            return;
        }
        mCallbacks.put(key, callback);
    }

    private void initLogger() {
        logger = new LoggerService(this.getApplicationContext());
        logger.Start();
        logger.LogData("EV_DEBUG", "Server", "Server initialized the Logger.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(SERVER, "OnStart executed");
        super.onStartCommand(intent, flags, startId);

        //initLogger();
        return START_NOT_STICKY;
    }

    @Override
    /**
     * Entry point of the server, starts a socket server, initializes the connection and starts an event loop
     * Provides a pictureReady callback to the CameraProvider
     */
    protected void onHandleIntent(Intent intent) {
        try {
            ServerRunner.run(NanoHTTPDServer.class);
            Log.d(SERVER, "Server Started");
            openSockets();
            initConnection(mConnection);
            mCameraProvider.setCallback(new Runnable() {
                @Override
                public void run() {
                    mPicture = mCameraProvider.getLastPicture();
                    newPictureReady();
                }
            });
            loop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens a socket associated with the ServerSocket
     * @throws IOException
     */
    private void openSockets() throws IOException {
        if(mServerSocket != null && mServerSocket.isBound()) {
            mServerSocket.close();
        }
        mServerSocket = new ServerSocket(51342);
        mServerSocket.setSoTimeout(0);
        mServerSocket.setReuseAddress(true);
        mConnection = mServerSocket.accept();
        Log.d(SERVER, "Socket opened");
    }

    /**
     * Opens the input/output streams with a Socket
     * as a BufferedReader/PrintWriter
     * @param connection - Socket
     * @throws IOException
     */
    private void initConnection(Socket connection) throws IOException {
        Log.d(SERVER, "Streams opened");

        //logger.LogData(EV_DEBUG, SERVER, "Streams opened");

        mStreamFromClient = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        mStreamToClient = new PrintWriter(connection.getOutputStream(), true);
        mCameraProvider.init();
        sCurrentStatus.setEmergency(new JSONTuple<>("emergency", "stop"));
        //initialize a heartbeat to the client
        mHeartbeat.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                JSONTuple<String, String> jsonTuple = new JSONTuple<>(IPCKeys.HEARTBEAT,
                        String.valueOf(currentTime / 1000));
                Type type = new TypeToken<JSONTuple<String, String>>() {}.getType();
                String json = mGson.toJson(jsonTuple, type);
                mStreamToClient.println(json);
                mStreamToClient.flush();
            }
        }, 1, 1000);
        onClientConnected();
    }

    /**
     * Deserializes a JSON string in a JSONCoordinates/JsonAction class, depending on the key of the JSON
     * Checks if the key has a callback associated with it, and runs it.
     * @param json - String describing a JSON object
     */
    private String deserialize(String json) {

        if(json.isEmpty()) {
            Log.w(SERVER, "JSON is empty");
            return null;
        }
        String mKey;
        Log.d(SERVER, json);
        //returns the next string encountered after "key" in the JSON
        Pattern mPattern = Pattern.compile("\\{.+(?=key)\\w+\":(\"\\w+\")\\}?.+\\}?$");
        Matcher matcher = mPattern.matcher(json);
        if(JSONUtils.validateJson(json) && matcher.matches()) {
            mKey = matcher.group(1);
        } else {
            Log.e(SERVER, "Invalid JSON!");
            mStreamToClient.println("Invalid JSON!");
            mStreamToClient.flush();
            return null;
        }

        mKey = mKey.replace('"', ' ').trim();
        switch (mKey) {
            case IPCKeys.COORDINATES: {
                //TypeToken must be passed to the fromJson method to avoid type erasure problems
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonCoordinates = JSONUtils.deserialize(json, type);
                sCurrentStatus.setAzimuth(mJsonCoordinates.getValue1());
                sCurrentStatus.setPitch(mJsonCoordinates.getValue2());
                sCurrentStatus.setRoll(mJsonCoordinates.getValue3());
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.YAW: {
                Type type = new TypeToken<JSONTriple<String, String, Float>>() {}.getType();
                mJsonAction = JSONUtils.deserialize(json, type);
                sCurrentStatus.setYaw(mJsonAction);
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.THROTTLE: {
                Type type = new TypeToken<JSONTriple<String, String, Float>>() {}.getType();
                mJsonAction = JSONUtils.deserialize(json, type);
                sCurrentStatus.setThrottle(mJsonAction);
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.EMERGENCY: {
                Type type = new TypeToken<JSONTuple<String, String>>() {}.getType();
                mJsonTuple = JSONUtils.deserialize(json, type);
                sCurrentStatus.setEmergency(mJsonTuple);
                fireCallbacks(mKey, json);
                mStreamToClient.println("Emergency " + mJsonTuple.value);
                mStreamToClient.flush();
            }
            break;
            case IPCKeys.PIDYAW: {
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonPid = JSONUtils.deserialize(json, type);
                sCurrentStatus.mPidYaw.setP(mJsonPid.getValue1());
                sCurrentStatus.mPidYaw.setI(mJsonPid.getValue2());
                sCurrentStatus.mPidYaw.setD(mJsonPid.getValue3());
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.PIDPITCH: {
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonPid = JSONUtils.deserialize(json, type);
                sCurrentStatus.mPidPitch.setP(mJsonPid.getValue1());
                sCurrentStatus.mPidPitch.setI(mJsonPid.getValue2());
                sCurrentStatus.mPidPitch.setD(mJsonPid.getValue3());
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.PIDROLL: {
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonPid = JSONUtils.deserialize(json, type);
                sCurrentStatus.mPidRoll.setP(mJsonPid.getValue1());
                sCurrentStatus.mPidRoll.setI(mJsonPid.getValue2());
                sCurrentStatus.mPidRoll.setD(mJsonPid.getValue3());
                fireCallbacks(mKey, json);
            }
            break;
            case IPCKeys.PICTURE: {
                mCameraProvider.snapIt();
                fireCallbacks(mKey, json);
            }
            break;
            default: {
                if(mCallbacks.containsKey(mKey)) {
                    fireCallbacks(mKey, json);
                } else {
                    mStreamToClient.println("Unknown key " + mKey);
                    mStreamToClient.flush();
                }
            }
            break;
        }
        return mKey;
    }

    /**
     * Waits for input on the socket, and returns it as a string
     * If the string representing the JSON object is null
     * Resets the socketServer and waits for a new connection
     * Also resets the heartbeat
     * @return - String in JSON notation, describing a command
     * @throws java.io.IOException
     */
    private String readStream() throws IOException {
        String mJson;
        if((mJson = mStreamFromClient.readLine()) == null) {
            mHeartbeat.cancel();
            mHeartbeat.purge();
            mHeartbeat = new Timer();
            sCurrentStatus.setEmergency(new JSONTuple<>("emergency", "start"));
            mConnection = mServerSocket.accept();
            initConnection(mConnection);
            loop();
        }
        return mJson;
    }

    /**
     * Camera provider callback, called every time a new picture is ready
     * Sends a JSON with picture metadata and base64 encoded byte array of the picture
     */
    private void newPictureReady() {
        Log.d(SERVER, "New picture is ready");
        mPicture = mCameraProvider.getLastPicture();
        String base64Pic = Base64.encodeToString(mPicture, Base64.DEFAULT);
        JSONTriple<String, String, String> mJsonBitmap = new JSONTriple<>(IPCKeys.PICTURE, IPCKeys.PICREADY, base64Pic);
        String mJson = JSONUtils.serialize(mJsonBitmap, new TypeToken<JSONTriple<String, String, String>>() {}.getType());
        mStreamToClient.println(mJson);
        Log.d(SERVER, mJson);
        mStreamToClient.flush();
    }

    private void fireCallbacks(String key, String json) {
        if(mCallbacks.containsKey(key)) {
            mCallbacks.get(key).run(json);
        } else {
            Log.w(SERVER, "Key: " + key + " has no associated callback");
        }
    }

    /**
     * Used to send custom messages to the client app
     * Intended for usage with custom callbacks.
     * @param msg - String
     * @return - true, if successful, false otherwise
     */
    public static boolean sendMsgToClient(String msg) {
        if(msg != null && !msg.isEmpty() && mStreamToClient != null) {
            Log.d(SERVER, "Sent to client: " + msg);
            mStreamToClient.println(msg);
            mStreamToClient.flush();
            return true;
        } else {
            return false;
        }
    }

    private void onClientConnected() {

        String json;
        Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();

        mJsonPid = new JSONQuadruple<>("pidyaw", sCurrentStatus.getPidYaw().getP(), sCurrentStatus.getPidYaw().getI(), sCurrentStatus.getPidYaw().getD());
        json = mGson.toJson(mJsonPid, type);
        mStreamToClient.println(json);
        mStreamToClient.flush();

        mJsonPid = new JSONQuadruple<>("pidpitch", sCurrentStatus.getPidPitch().getP(), sCurrentStatus.getPidPitch().getI(), sCurrentStatus.getPidPitch().getD());
        json = mGson.toJson(mJsonPid, type);
        mStreamToClient.println(json);
        mStreamToClient.flush();

        mJsonPid = new JSONQuadruple<>("pidroll", sCurrentStatus.getPidRoll().getP(), sCurrentStatus.getPidRoll().getI(), sCurrentStatus.getPidRoll().getD());
        json = mGson.toJson(mJsonPid, type);
        mStreamToClient.println(json);
        mStreamToClient.flush();
    }

    /**
     * Main worker, deserializes the JSON data and notifies the registered components for changes
     * @throws java.io.IOException
     */
    private void loop() throws IOException {
        while(true) {
            Log.d(SERVER, "Waiting for input");
            String mJson = readStream();
            String mKey;
            if(mJson == null) {
                Log.w(SERVER, "JSON is null");
                break;
            }
            mKey = deserialize(mJson);
//            fireCallbacks(mKey, mJson);
        }
    }
}
