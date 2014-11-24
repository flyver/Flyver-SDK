package co.flyver.androidrc.Server;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
import co.flyver.dataloggerlib.LoggerService;

import static co.flyver.IPC.IPCContainers.JSONQuadruple;
import static co.flyver.IPC.IPCContainers.JSONTriple;
import static co.flyver.IPC.IPCContainers.JSONTuple;
import static co.flyver.IPC.IPCContainers.JSONUtils;


/**
 * Created by Petar Petrov on 10/2/14.
 */
public class Server extends IntentService {

    /**
     * Interface for defining custom callbacks
     * which are associated with keys, and are run
     * when a JSON with the appropriate key is received
     */
    public interface ServerCallback {
        public void run(String json);
    }

    /**
     * Container for the current drone status
     * Holds the speed/positioning of the drone and also the coefficients of the PID controllers
     * Parameters:
     * Roll - Orientation of the drone on the X axis - values vary between 0 and 360 deg
     * Pitch - Orientation of the drone on the Y axis - values vary between 0 and 360 deg
     * Yaw - Orientation of the drone on the Z axis - values vary between -180 and 180 deg
     * Throttle - Combined speed of the drone's motors - varies between 0 and 100%
     * Emergency - Denotes if the drone is in emergency mode - boolean
     */
    public class Status {

        private float MAX_THROTTLE = 1023;
        private float MAX_YAW = 180;
        private float MIN_YAW = -180;

        float mAzimuth = 0;
        float mPitch = 0;
        float mRoll = 0;
        float mYaw = 0;
        float mThrottle = 0;
        boolean mEmergency = false;
        PID mPidYaw = new PID();
        PID mPidPitch = new PID();
        PID mPidRoll = new PID();

        public PID getPidRoll() {
            return mPidRoll;
        }

        public PID getPidPitch() {
            return mPidPitch;
        }

        public PID getPidYaw() {
            return mPidYaw;
        }

        public class PID {
            float mP;
            float mI;
            float mD;

            public float getP() {
                return mP;
            }

            public void setP(float mP) {
                this.mP = mP;
            }

            public float getI() {
                return mI;
            }

            public void setI(float mI) {
                this.mI = mI;
            }

            public float getD() {
                return mD;
            }

            public void setD(float mD) {
                this.mD = mD;
            }

            private PID() {
                //empty constructor
            }

            private PID(float p, float i, float d) {
                this.mP = p;
                this.mI = i;
                this.mD = d;
            }

        }

        public boolean isEmergency() {
            return mEmergency;
        }

        public void setEmergency(JSONTuple JSONAction) {
            if(JSONAction.value.equals("stop")) {
                this.mEmergency = false;
            } else if (JSONAction.value.equals("start")) {
                this.mRoll = 0;
                this.mPitch = 0;
                this.mThrottle = 0;
                this.mYaw = 0;
                this.mEmergency = true;
            }
        }


        private Status() {
        }

        public float getAzimuth() {
            return mAzimuth;
        }

        public void setAzimuth(float mAzimuth) {
            this.mAzimuth = mAzimuth;
        }

        public float getPitch() {
            return mPitch;
        }

        public void setPitch(float mPitch) {
            this.mPitch = mPitch;
        }

        public float getRoll() {
            return mRoll;
        }

        public void setRoll(float mRoll) {
            this.mRoll = mRoll * (-1);
        }

        public float getYaw() {
            return mYaw;
        }

        /**
         * Changes the current yaw based on steps
         * If the yaw exceeds 180 deg, it overflows to -180 deg or lower, and vice versa
         * @param jsonTriple - generic JSONTriple<String, String, Float>
         */
        public void setYaw(JSONTriple<String, String, Float> jsonTriple) {
            float newYaw = this.mYaw + (MAX_YAW * (jsonTriple.getValue() / 100));
            if(newYaw > MAX_YAW) {
                newYaw *= (-1);
                if(newYaw < MIN_YAW) {
                    newYaw = MIN_YAW;
                }
                this.mYaw = 0;
            }
            if(jsonTriple.action.equals(IPCKeys.INCREASE)) {
                this.mYaw = newYaw;
            } else if(jsonTriple.action.equals(IPCKeys.DECREASE)) {
                if(newYaw < MIN_YAW) {
                    newYaw *= 1;
                    if(newYaw > MAX_YAW) {
                        newYaw = MAX_YAW;
                    }
                }
                this.mYaw = newYaw;
            }
        }

        public float getThrottle() {
            return mThrottle;
        }

        /**
         * Changes the throttle based on steps
         * Steps can vary between 0 and 100, percentage based
         * Floating point steps are allowed
         * @param jsonTriple - generic JSONTriple<String, String, Float>
         */
        public void setThrottle(JSONTriple<String, String, Float> jsonTriple) {
            float newThrottle = mThrottle;
            if(jsonTriple.action.equals(IPCKeys.INCREASE)) {
                newThrottle += MAX_THROTTLE * (jsonTriple.getValue() / 100);
                if(newThrottle > MAX_THROTTLE) {
                    this.mThrottle = MAX_THROTTLE;
                } else {
                    this.mThrottle = newThrottle;
                }
            } else if(jsonTriple.action.equals(IPCKeys.DECREASE)) {
                newThrottle -= MAX_THROTTLE * (jsonTriple.getValue() / 100);
                if(newThrottle < 0) {
                    this.mThrottle = 0;
                } else {
                    this.mThrottle = newThrottle;
                }
            }
        }
    }

    private static final String SERVER = "SERVER";
    public static Status sCurrentStatus;
    private static boolean mInstantiated = false;

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

    public Server() {
        super("Server");
        if(mInstantiated) {
            throw new IllegalStateException("Could only be instantiated once!");
        }
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(SERVER, "OnStart executed");
        super.onStartCommand(intent, flags, startId);
        mInstantiated = true;
        return START_NOT_STICKY;
    }

    @Override
    /**
     * Entry point of the server, starts a socket server, initializes the connection and starts an event loop
     * Provides a pictureReady callback to the CameraProvider
     */
    protected void onHandleIntent(Intent intent) {
        try {
            Log.d(SERVER, "Server Started");
            sCurrentStatus = new Status();
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
        //LoggerService.startLogData(this, "http://u.ftpd.biz/logger/call/json/store/", "POST", "a1a635bf-b51f-4cd9-a69a-c7578792d598", "DEBUG", SERVER, "Streams opened");
        LoggerService logger = new LoggerService(this.getApplicationContext());
        logger.Start();
        logger.LogData("DEBUG111", SERVER, "Streams opened");
        logger.Stop();

        mStreamFromClient = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        mStreamToClient = new PrintWriter(connection.getOutputStream(), true);
//        if(mCameraProvider instanceof VideoStreamProvider) {
//            mCameraProvider.associateSocket(connection);
//            mCameraProvider.init();
//        }
        mCameraProvider.init();
        //initialize a heartbeat to the client
        mHeartbeat.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                JSONTuple<String, String> jsonTuple = new JSONTuple<>(IPCKeys.HEARTBEAT,
                        String.valueOf(currentTime / 1000));
                Type type = new TypeToken<JSONTuple<String, String>>() {}.getType();
                String json = mGson.toJson(jsonTuple, type);
                Log.d(SERVER, "Heartbeat");
                mStreamToClient.println(json);
                mStreamToClient.flush();
            }
        }, 1, 1000);
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
        if(validateJson(json) && matcher.matches()) {
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
                mJsonAction = JSONUtils.deserialize(json, type);
                sCurrentStatus.setAzimuth(mJsonCoordinates.getValue1());
                sCurrentStatus.setPitch(mJsonCoordinates.getValue2());
                sCurrentStatus.setRoll(mJsonCoordinates.getValue3());
                fireCallbacks(mKey, json);
                mStreamToClient.println("Current coordinates are: Pitch:" + sCurrentStatus.getPitch() + " Roll: " + sCurrentStatus.getRoll());
                mStreamToClient.flush();
            }
            break;
            case IPCKeys.YAW: {
                Type type = new TypeToken<JSONTriple<String, String, Float>>() {}.getType();
                mJsonAction = JSONUtils.deserialize(json, type);
                sCurrentStatus.setYaw(mJsonAction);
                fireCallbacks(mKey, json);
                mStreamToClient.println("Current Yaw is: " + sCurrentStatus.getYaw());
                mStreamToClient.flush();
            }
            break;
            case IPCKeys.THROTTLE: {
                Type type = new TypeToken<JSONTriple<String, String, Float>>() {}.getType();
                mJsonAction = JSONUtils.deserialize(json, type);
                sCurrentStatus.setThrottle(mJsonAction);
                fireCallbacks(mKey, json);
                mStreamToClient.println("Current Throttle is: " + sCurrentStatus.getThrottle());
                mStreamToClient.flush();
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
                mStreamToClient.println("PID Yaw: Proportional:" + sCurrentStatus.mPidYaw.getP() + " Integral: " + sCurrentStatus.mPidYaw.getI() + " Derivative: " + sCurrentStatus.mPidYaw.getD());
                mStreamToClient.flush();
            }
            break;
            case IPCKeys.PIDPITCH: {
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonPid = JSONUtils.deserialize(json, type);
                sCurrentStatus.mPidPitch.setP(mJsonPid.getValue1());
                sCurrentStatus.mPidPitch.setI(mJsonPid.getValue2());
                sCurrentStatus.mPidPitch.setD(mJsonPid.getValue3());
                fireCallbacks(mKey, json);
                mStreamToClient.println("PID Yaw: Proportional:" + sCurrentStatus.mPidPitch.getP() + " Integral: " + sCurrentStatus.mPidPitch.getI() + " Derivative: " + sCurrentStatus.mPidPitch.getD());
                mStreamToClient.flush();
            }
            break;
            case IPCKeys.PIDROLL: {
                Type type = new TypeToken<JSONQuadruple<String, Float, Float, Float>>() {}.getType();
                mJsonPid = JSONUtils.deserialize(json, type);
                sCurrentStatus.mPidRoll.setP(mJsonPid.getValue1());
                sCurrentStatus.mPidRoll.setI(mJsonPid.getValue2());
                sCurrentStatus.mPidRoll.setD(mJsonPid.getValue3());
                fireCallbacks(mKey, json);
                mStreamToClient.println("PID Yaw: Proportional:" + sCurrentStatus.mPidRoll.getP() + " Integral: " + sCurrentStatus.mPidRoll.getI() + " Derivative: " + sCurrentStatus.mPidRoll.getD());
                mStreamToClient.flush();
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
            mConnection = mServerSocket.accept();
            initConnection(mConnection);
            loop();
        }
        return mJson;
    }

    /**
     * Validates if a string is a valid JSON object
     * @param json - String to be validated
     * @return - boolean, true if the string is a valid JSON object
     */
    private boolean validateJson(String json)  {
        try {
            mGson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            Log.d(SERVER, "Invalid JSON:" + json);
            return false;
        }
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
        if(msg != null && !msg.isEmpty()) {
            mStreamToClient.println(msg);
            mStreamToClient.flush();
            return true;
        } else {
            return false;
        }
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
            fireCallbacks(mKey, mJson);
        }
    }
}
