package co.flyver.flyvercore.MainControllers;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Environment;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import co.flyver.IPC.IPCContainers;
import co.flyver.IPC.IPCKeys;
import co.flyver.IPC.JSONUtils;
import co.flyver.androidrc.Server.Server;
import co.flyver.androidrc.Server.Status;
import co.flyver.androidrc.Server.interfaces.ServerCallback;
import co.flyver.dataloggerlib.LoggerService;
import co.flyver.flyvercore.DroneTypes.Drone;
import co.flyver.flyvercore.DroneTypes.QuadCopterX;
import co.flyver.flyvercore.MicroControllers.MicroController;
import co.flyver.flyvercore.PIDControllers.PIDAngleController;
import co.flyver.flyvercore.R;
import co.flyver.flyvercore.StateData.Battery;
import co.flyver.flyvercore.StateData.DroneState;
import co.flyver.flyvercore.StateData.LocationServicesProvider;
import co.flyver.flyvercore.StateData.LocationServicesSubsciber;
import co.flyver.flyvercore.StateData.SensorsWrapper;
import co.flyver.utils.flyverMQ.FlyverMQ;
import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;
import ioio.lib.spi.Log;

import static co.flyver.flyvercore.StateData.Battery.BatteryCells;

/**
 * The MainController is the heart of Flyver
 * The MainController initialized all the controllers and control algorithms
 * TODO: Vertical stabilization, GPS and more
 * TODO: Break the MainController into smaller managable pieces
 * TODO: Extract the MainController as an interface
 */
public class MainController extends Activity {

    /* CONSTANTS*/
    public static final double MAX_MOTOR_POWER = 1023.0;
    public static final long INT_MAX = Integer.MAX_VALUE;
    public static final float MAX_SAFE_PITCH_ROLL = 45; // [deg].
    public static final float PID_DERIV_SMOOTHING = 0.5f;
    private static final String CONTROLLER = "CONTROLLER";
    /* END OF*/

    private static MainController instance;
    private MicroController microController;
    private Battery battery;
    private Drone drone;
    private float meanThrust;
    private float yawAngleTarget;
    private float pitchAngleTarget;
    private float rollAngleTarget;
    private float altitudeTarget;
    private float timeWithoutPcRx;
    private float timeWithoutAdkRx;
    private DroneState droneState;
    private Drone.MotorPowers motorsPowers;
    private boolean regulatorEnabled;
    private boolean altitudeLockEnabled;
    private PIDAngleController yawController;
    private PIDAngleController pitchController;
    private PIDAngleController rollController;
    private DroneState.DroneStateData droneStateData;
    private long previousTime;
    private boolean zeroStateFlag = false; // Flag indicate that droneState should be zeroed
    private SensorsWrapper sensors;
    private Activity mainActivityRef;
    private FlyverMQ messageQueue;
    private FlyverMQConsumer dronestateListener;



    private LoggerService logger;

    /**
     * Helper used to build the instance of the MainController
     * using the provided host activity, microcontroller and drone type
     */
    public static class MainControllerBuilder {
        Activity activity;
        MicroController microController;
        QuadCopterX drone;

        public MainControllerBuilder setMicroController(MicroController microController) {
            this.microController = microController;
            return this;
        }

        public MainControllerBuilder setDrone(QuadCopterX drone) {
            this.drone = drone;
            return this;
        }

        public MainControllerBuilder setActivity(Activity activity) {
            this.activity = activity;
            return this;
        }

        /**
         * Create the first instance of the MainController
         * Throws exception if an instance is already created
         */
        public void build() throws MainControllerInstanceExisting {
            if(instance == null) {
                instance = new MainController(activity, microController, drone);
                try {
                    instance.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else  {
                throw new MainControllerInstanceExisting("Only one instance of the MainController can be existing at a time");
            }
        }
    }

    private enum PIDKeys {
        PID_YAW_PROPORTIONAL("proportionalY"),
        PID_YAW_INTEGRAL("integralY"),
        PID_YAW_DERIVATIVE("derivativeY"),
        PID_PITCH_PROPORTIONAL("proportionalP"),
        PID_PITCH_INTEGRAL("integralP"),
        PID_PITCH_DERIVATIVE("derivativeP"),
        PID_ROLL_PROPORTIONAL("proportionalR"),
        PID_ROLL_INTEGRAL("integralR"),
        PID_ROLL_DERIVATIVE("derivativeR");
        private String key;

        private PIDKeys(String key) {
            this.key = key;
        }

        public String getValue() {
            return key;
        }
    }

    public static MainController getInstance() {
        return instance;
    }

    public FlyverMQ getMessageQueue() {
        return messageQueue;
    }
    public Activity getMainActivityRef() {
        return mainActivityRef;
    }

    public float getYawAngleTarget() {
        return yawAngleTarget;
    }

    public void setYawAngleTarget(float yawAngleTarget) {
        this.yawAngleTarget = yawAngleTarget;
    }

    public float getPitchAngleTarget() {
        return pitchAngleTarget;
    }

    public void setPitchAngleTarget(float pitchAngleTarget) {
        this.pitchAngleTarget = pitchAngleTarget;
    }

    public float getRollAngleTarget() {
        return rollAngleTarget;
    }

    public void setRollAngleTarget(float rollAngleTarget) {
        this.rollAngleTarget = rollAngleTarget;
    }

    public float getMeanThrust() {
        return meanThrust;
    }

    public void setMeanThrust(float meanThrust) {
        this.meanThrust = meanThrust;
    }

    public void setMicroController(MicroController microController) {
        this.microController = microController;
        if (battery != null) {
            battery.setMicroController(microController);
        }
    }

    public int getBatteryPercentage() {
        return battery.getBatteryStatus();
    }

    private void initLogger() {
        logger = new LoggerService(mainActivityRef.getApplicationContext(), messageQueue);
        logger.Start();
        logger.LogData("EV_DEBUG", "MainController", "MainController initialized the Logger.");
    }

    private MainController(Activity activity, MicroController microController, QuadCopterX drone) {
        regulatorEnabled = true;
        this.drone = drone;
        mainActivityRef = activity;


        // altitudeRegulator = new PidRegulator(0.0f,  0.0f,  0.0f, PID_DERIV_SMOOTHING, 0.0f);

        yawAngleTarget = 0.0f;
        pitchAngleTarget = 0.0f;
        rollAngleTarget = 0.0f;
        altitudeTarget = 0.0f;

    }

    public void start() throws Exception {
        // Initializations.
        regulatorEnabled = true;
        altitudeLockEnabled = false;
        meanThrust = 0.0f;
        messageQueue = FlyverMQ.getInstance();
        LocationServicesProvider locationServicesProvider = new LocationServicesProvider();
        LocationServicesSubsciber locationServicesSubsciber = new LocationServicesSubsciber();
//        SimpleMQ.getInstance().registerConsumer(locationServicesSubsciber, LocationServicesSubsciber.TOPIC);

        battery = new Battery(microController, BatteryCells.THREE);
        battery.setStatusChangeCb(new Battery.StatusChanged() {
            @Override
            public void onChange(int status) {
                Log.d(CONTROLLER, "Battery status : " + status + "%");
                IPCContainers.JSONTuple<String, String> batteryInfo = new IPCContainers.JSONTuple<>("battery", String.valueOf(status));
                Type type = new TypeToken<IPCContainers.JSONTuple<String, String>>() {
                }.getType();
                Server.sendMsgToClient(JSONUtils.serialize(batteryInfo, type));
            }
        }).setBatteryCriticalCallback(new Runnable() {
            @Override
            public void run() {
                Log.e(CONTROLLER, "Battery is almost depleted");
                emergencyStop("Low battery!");
            }
        });

        sensors = new SensorsWrapper(getMainActivityRef());
        sensors.start();
        droneState = new DroneState(sensors);
        droneStateData = droneState.new DroneStateData();
        // Start the sensors.
        droneState.resumed();
        //initialize the controller parameters
        init();

        //initLogger();
    }

    public void stop() {
        // Stop the main controller thread.
        // Stop the sensors.
        droneState.paused();
    }

    public DroneState.DroneStateData getSensorsData() {
        return droneStateData;
    }

    public void setMeanTrust(float trust) {
        meanThrust = trust;
    }

    public boolean getRegulatorsState() {
        return regulatorEnabled;
    }

    private int motorSaturation(double val) {
        if (val > MAX_MOTOR_POWER)
            return (int) MAX_MOTOR_POWER;
        else if (val < 0.0)
            return 0;
        else
            return (int) val;
    }

    /**
     * onConnectionEstablished is called when the whole systems starts
     * and int working order
     */
    public void onConnectionEstablished() {
        // Reset the orientation.
        droneState.setCurrentStateAsZero();
        // TODO: droneState.setCurrentStateAsZero works only on initialization, but can't be accessed after
    }

    /**
     * Called when the communication link between the drone and the RC is lost.
     * TODO: Break it into cases and implement algorithms such as Return To Home
     */
    public void onConnectionLost() {
        // Emergency stop of the quadcopter.
        emergencyStop("Connection Lost");
    }

    public void onIoioConnect() {
        Log.e(CONTROLLER, "IOIO Connection established");
        battery.resume();
    }

    public void onIoioDisconnect() {
        Log.e(CONTROLLER, "IOIO Connection lost");
        battery.pause();
    }

    /**
     * Stops the drone.
     * NB! Currently means stopping the motors
     *
     * @param reason for logging the cause of the emergency
     */
    public void emergencyStop(String reason) {
        // TODO: Smart Landing
        Log.w("Emergency", reason);
        yawController.resetIntegrator();
        pitchController.resetIntegrator();
        rollController.resetIntegrator();
        setMeanThrust(0);
    }

    private void init() {

        final Context mainContext = getMainActivityRef().getApplicationContext();
        setPIDSettings(mainContext);
        initPIDControllers(mainContext);

        ServerCallback onValuesChange = new ServerCallback() {
            @Override
            public void run(String json) {
                setMeanTrust(Server.sCurrentStatus.getThrottle());
                setPitchAngleTarget(Server.sCurrentStatus.getPitch());
                setRollAngleTarget(Server.sCurrentStatus.getRoll());
                setYawAngleTarget(Server.sCurrentStatus.getYaw());
                if (Server.sCurrentStatus.isEmergency()) {
                    Log.e(CONTROLLER, "Emergency sequence initiated");
                    emergencyStop("Server command");
                }
            }
        };

        ServerCallback onPidYawChange = new ServerCallback() {
            @Override
            public void run(String json) {
                Log.e("PID", "PID Yaw coefficients have changed!!!");
                Status.PID pid = Server.sCurrentStatus.getPidYaw();
                changePIDSharedPreference(PIDKeys.PID_YAW_PROPORTIONAL, String.valueOf(pid.getP()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_YAW_INTEGRAL, String.valueOf(pid.getI()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_YAW_DERIVATIVE, String.valueOf(pid.getD()), mainContext);
                yawController.setCoefficients(pid.getP(), pid.getI(), pid.getD());
            }
        };

        ServerCallback onPidPitchChange = new ServerCallback() {
            @Override
            public void run(String json) {
                Log.e("PID", "PID Pitch coefficients have changed!!!");
                Status.PID pid = Server.sCurrentStatus.getPidPitch();
                changePIDSharedPreference(PIDKeys.PID_PITCH_PROPORTIONAL, String.valueOf(pid.getP()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_PITCH_INTEGRAL, String.valueOf(pid.getI()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_PITCH_DERIVATIVE, String.valueOf(pid.getD()), mainContext);
                pitchController.setCoefficients(pid.getP(), pid.getI(), pid.getD());
            }
        };

        ServerCallback onPidRollChange = new ServerCallback() {
            @Override
            public void run(String json) {
                Log.e("PID", "PID Roll coefficients have changed!!!");
                Status.PID pid = Server.sCurrentStatus.getPidRoll();
                changePIDSharedPreference(PIDKeys.PID_ROLL_PROPORTIONAL, String.valueOf(pid.getP()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_ROLL_INTEGRAL, String.valueOf(pid.getI()), mainContext);
                changePIDSharedPreference(PIDKeys.PID_ROLL_DERIVATIVE, String.valueOf(pid.getD()), mainContext);
                rollController.setCoefficients(pid.getP(), pid.getI(), pid.getD());
            }
        };

        //Register callbacks to be called when the appropriate JSON is received
        Server.registerCallback(IPCKeys.THROTTLE, onValuesChange);
        Server.registerCallback(IPCKeys.YAW, onValuesChange);
        Server.registerCallback(IPCKeys.COORDINATES, onValuesChange);
        Server.registerCallback(IPCKeys.EMERGENCY, onValuesChange);
        Server.registerCallback(IPCKeys.PIDPITCH, onPidPitchChange);
        Server.registerCallback(IPCKeys.PIDROLL, onPidRollChange);
        Server.registerCallback(IPCKeys.PIDYAW, onPidYawChange);

        dronestateListener = new FlyverMQConsumer() {
            @Override
            public void dataReceived(FlyverMQMessage message) {
                float yawForce, pitchForce, rollForce, altitudeForce, currentYaw, currentPitch, currentRoll;

                if (!zeroStateFlag) {
                    droneState.setCurrentStateAsZero();
                    zeroStateFlag = true;
                }

                // Get the sensors data.
                droneStateData = (DroneState.DroneStateData) message.data;

                currentYaw = droneStateData.yaw;
                currentPitch = droneStateData.pitch;
                currentRoll = droneStateData.roll;

                long currentTime = droneStateData.time;
                float dt = ((float) (currentTime - previousTime)) / 1000000000.0f; // [s].
                previousTime = currentTime;

                // Check for dangerous situations.
                if (regulatorEnabled) {
                    // If the quadcopter is too inclined, emergency stop it.
                    if (Math.abs(currentPitch) > MAX_SAFE_PITCH_ROLL ||
                            Math.abs(currentRoll) > MAX_SAFE_PITCH_ROLL) {
                        //TODO: Setup safety max rol/pitch values
                        emergencyStop("Safe pitch or safe roll exceeded");
                    }
                }

                // Compute the motors powers.
                if (regulatorEnabled && meanThrust > 1.0) {
                    // Compute the "forces" needed to move the quadcopter to the
                    // set point.
                    yawForce = yawController.getInput(yawAngleTarget, currentYaw, dt);
                    pitchForce = pitchController.getInput(pitchAngleTarget, currentPitch, dt);
                    rollForce = rollController.getInput(rollAngleTarget, currentRoll, dt);
                /*
                if(altitudeLockEnabled)
                    altitudeForce = altitudeRegulator.getInput(altitudeTarget, currentAltitude, dt);
                else */
                    altitudeForce = meanThrust;
                    drone.updateSpeeds(yawForce, pitchForce, rollForce, altitudeForce);

                } else {
                    drone.setToZero();
                    yawController.resetIntegrator();
                    pitchController.resetIntegrator();
                    rollController.resetIntegrator();
                    yawForce = 0.0f;
                    pitchForce = 0.0f;
                    rollForce = 0.0f;
                    altitudeForce = 0.0f;
                }
            }

            @Override
            public void unregistered() {

            }

            @Override
            public void paused() {

            }

            @Override
            public void resumed() {

            }
        };
        FlyverMQ.getInstance().registerConsumer(dronestateListener, "dronestate.raw");
        deployWebpage();
    }

    private void setPIDSettings(Context context) {
        final String PREFERENCES = context.getString(R.string.pid_preferences);
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);

        /* Yaw PID Controller preferences */
        if (!sharedPreferences.contains(PIDKeys.PID_YAW_PROPORTIONAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_YAW_PROPORTIONAL, "2.2", context);
        }
        if (!sharedPreferences.contains(PIDKeys.PID_YAW_INTEGRAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_YAW_INTEGRAL, "0", context);
        }
        if (!sharedPreferences.contains(PIDKeys.PID_YAW_DERIVATIVE.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_YAW_DERIVATIVE, "0.2", context);
        }
        /* End of Yaw PID Controller preferences */

        /* Pitch PID Controller preferences */

        if (!sharedPreferences.contains(PIDKeys.PID_PITCH_PROPORTIONAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_PITCH_PROPORTIONAL, "2.4", context);
        }

        if (!sharedPreferences.contains(PIDKeys.PID_PITCH_INTEGRAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_PITCH_INTEGRAL, "0.2", context);
        }

        if (!sharedPreferences.contains(PIDKeys.PID_PITCH_DERIVATIVE.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_PITCH_DERIVATIVE, "0.4", context);
        }

        /* End of Pitch PID controller preferences */

        /* Roll PID controller preferences */
        if (!sharedPreferences.contains(PIDKeys.PID_ROLL_PROPORTIONAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_ROLL_PROPORTIONAL, "2.4", context);
        }

        if (!sharedPreferences.contains(PIDKeys.PID_ROLL_INTEGRAL.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_ROLL_INTEGRAL, "0.2", context);
        }

        if (!sharedPreferences.contains(PIDKeys.PID_ROLL_DERIVATIVE.getValue())) {
            changePIDSharedPreference(PIDKeys.PID_ROLL_INTEGRAL, "0.4", context);
        }

        /* End of Roll PID controller preferences */

        float[][] pidValues = getPidValues(context);
        Server.sCurrentStatus.getPidYaw().setP(pidValues[0][0]);
        Server.sCurrentStatus.getPidYaw().setI(pidValues[0][1]);
        Server.sCurrentStatus.getPidYaw().setD(pidValues[0][2]);
        Server.sCurrentStatus.getPidPitch().setP(pidValues[1][0]);
        Server.sCurrentStatus.getPidPitch().setI(pidValues[1][1]);
        Server.sCurrentStatus.getPidPitch().setD(pidValues[1][2]);
        Server.sCurrentStatus.getPidRoll().setP(pidValues[2][0]);
        Server.sCurrentStatus.getPidRoll().setI(pidValues[2][1]);
        Server.sCurrentStatus.getPidRoll().setD(pidValues[2][2]);
    }

    private void initPIDControllers(Context context) {
        float[][] pidValues = getPidValues(context);

        yawController = new PIDAngleController(pidValues[0][0], pidValues[0][1], pidValues[0][2], PID_DERIV_SMOOTHING);
        pitchController = new PIDAngleController(pidValues[1][0], pidValues[1][1], pidValues[1][2], PID_DERIV_SMOOTHING);
        rollController = new PIDAngleController(pidValues[2][0], pidValues[2][1], pidValues[2][2], PID_DERIV_SMOOTHING);

    }

    private void changePIDSharedPreference(PIDKeys pidKey, String value, Context context) {
        final String PREFERENCES = context.getString(R.string.pid_preferences);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit();
        editor.putString(pidKey.getValue(), value);
        editor.apply();
        Log.d(CONTROLLER, "Preference changed for: " + pidKey.getValue() + " with " + value);
    }

    /**
     * returns 3x3 array of PID values
     * [0][n] - Yaw values
     * [1][n] - Pitch values
     * [2][n] - Roll values
     * [n][0] - proportional values
     * [n][1] - integral values
     * [n][2] - derivative values
     * @param context
     * @return array of PID values
     */
    private float[][] getPidValues(Context context) {
        final String PREFERENCES = context.getString(R.string.pid_preferences);
        final String DEFAULT = "0";
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        String preference;
        float p;
        float i;
        float d;
        float[][] values = new float[3][3];

        preference = sharedPreferences.getString(PIDKeys.PID_YAW_PROPORTIONAL.getValue(), DEFAULT);
        p = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_YAW_INTEGRAL.getValue(), DEFAULT);
        i = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_YAW_DERIVATIVE.getValue(), DEFAULT);
        d = Float.parseFloat(preference);
        values[0][0] = p;
        values[0][1] = i;
        values[0][2] = d;

        preference = sharedPreferences.getString(PIDKeys.PID_PITCH_PROPORTIONAL.getValue(), DEFAULT);
        p = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_PITCH_INTEGRAL.getValue(), DEFAULT);
        i = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_PITCH_DERIVATIVE.getValue(), DEFAULT);
        d = Float.parseFloat(preference);
        values[1][0] = p;
        values[1][1] = i;
        values[1][2] = d;

        preference = sharedPreferences.getString(PIDKeys.PID_ROLL_PROPORTIONAL.getValue(), DEFAULT);
        p = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_ROLL_INTEGRAL.getValue(), DEFAULT);
        i = Float.parseFloat(preference);
        preference = sharedPreferences.getString(PIDKeys.PID_ROLL_DERIVATIVE.getValue(), DEFAULT);
        d = Float.parseFloat(preference);
        values[2][0] = p;
        values[2][1] = i;
        values[2][2] = d;

        return values;
    }

    /**
     * Checks if the flyver webpage files have been copied to sdcard/co.flyver/webpage/
     * Copies them if not
     */
    private void deployWebpage() {
        String path = Environment.getExternalStorageDirectory().toString().concat("/co.flyver/");
        File webpageDir = new File(path.concat("/webpage/"));
        //TODO:: commented for debugging purposes, uncomment later
//        if(!webpageDir.exists()) {
            webpageDir.mkdirs();
            copyWebpage(path);
//        }
    }

    /**
     * Copies the files for the webpage from the assets dir, to the co.flyver/webpage dir on the sdcard
     * @param destinationPath
     */
    private void copyWebpage(String destinationPath) {
        AssetManager assetManager = mainActivityRef.getResources().getAssets();
        String[] files = null;
        try {
            files = assetManager.list("webpage");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (files != null) {
            for (String file : files) {
                InputStream inputStream;
                OutputStream outputStream;

                try {
                    inputStream = assetManager.open("webpage/" + file);
                    outputStream = new FileOutputStream(destinationPath.concat("/webpage/".concat(file)));
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    inputStream.close();
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}