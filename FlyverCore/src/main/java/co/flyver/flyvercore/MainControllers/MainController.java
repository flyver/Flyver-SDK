package co.flyver.flyvercore.MainControllers;


import android.app.Activity;
import android.os.SystemClock;

import co.flyver.IPC.IPCKeys;
import co.flyver.androidrc.Server.Server;
import co.flyver.flyvercore.DroneTypes.Drone;
import co.flyver.flyvercore.DroneTypes.QuadCopterX;
import co.flyver.flyvercore.MicroControllers.MicroController;
import co.flyver.flyvercore.PIDControllers.PIDAngleController;
import co.flyver.flyvercore.StateData.Battery;
import co.flyver.flyvercore.StateData.DroneState;
import ioio.lib.spi.Log;

import static co.flyver.androidrc.Server.Server.ServerCallback;
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
    public static final long INT_MAX = 2147483648L;
    public static final float MAX_SAFE_PITCH_ROLL = 45; // [deg].
    public static final float PID_DERIV_SMOOTHING = 0.5f;
    private static final String CONTROLLER = "CONTROLLER";
    /* END OF*/

    private Activity activity;
    private MicroController microController;
    private Battery battery;
    private Drone drone;
    private float meanThrust;
    private float yawAngleTarget;
    private float pitchAngleTarget;
    private float rollAngleTarget;
    private float altitudeTarget;
    private float batteryVoltage;
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
    private ControllerThread controllerThread;
    private long previousTime;
    private boolean zeroStateFlag = false; // Flag indicate that droneState should be zeroed
    private int batteryPercentage = 0;
    private BatteryStatusCallback batteryStatusCallback;
    private static MainController instance;
    String debugText = "";

    public interface BatteryStatusCallback {
        public void onChange(int status);
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
    private void setBatteryStatusCallback(BatteryStatusCallback batteryStatusCallback) {
        this.batteryStatusCallback = batteryStatusCallback;
    }

    public void setMicroController(MicroController microController) {
        this.microController = microController;
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    public MainController(Activity activity, MicroController microController, QuadCopterX drone) {

        regulatorEnabled = true;
        this.activity = activity;
        this.drone = drone;
//        microController = activity.getMicroController();
        battery = new Battery(microController, BatteryCells.THREE);
        battery.setStatusChangeCb(new Battery.onStatusChanged() {
            @Override
            public void onChange(int status) {
                if(status < 33) {
                    Log.w(CONTROLLER, "Battery at critical level");
                }
                batteryPercentage = status;
                Log.d(CONTROLLER, "Battery status : " + batteryPercentage + "%");
                if (batteryStatusCallback != null) {
                    batteryStatusCallback.onChange(status);
                }
            }
        });

        yawController = new PIDAngleController(1f, 0.0f, 0.0f, PID_DERIV_SMOOTHING);
        pitchController = new PIDAngleController(1f, 0.0f, 0.0f, PID_DERIV_SMOOTHING);
        rollController = new PIDAngleController(1f, 0.0f, 0.0f, PID_DERIV_SMOOTHING);
        // altitudeRegulator = new PidRegulator(0.0f,  0.0f,  0.0f, PID_DERIV_SMOOTHING, 0.0f);

        yawAngleTarget = 0.0f;
        pitchAngleTarget = 0.0f;
        rollAngleTarget = 0.0f;
        altitudeTarget = 0.0f;

        droneState = new DroneState(activity);
        droneStateData = droneState.new DroneStateData();
    }

    public void start() throws Exception {
        // Initializations.
        regulatorEnabled = true;
        altitudeLockEnabled = false;
        meanThrust = 0.0f;

        // Start the sensors.
        droneState.resume();

        // Start the main controller thread.
        controllerThread = new ControllerThread();
        controllerThread.start();

        new Thread(new DebugThread()).start();
    }

    public void stop() {
        // Stop the main controller thread.
        controllerThread.requestStop();

        // Stop the sensors.
        droneState.pause();
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

    public class ControllerThread extends Thread {
        float yawForce, pitchForce, rollForce, altitudeForce, currentYaw, currentPitch, currentRoll;

        ControllerThread() {

        }

        public String getDebugText() {
            debugText = "Y:" + (yawForce) + " P:" + (pitchForce) +
                    " R:" + (rollForce) + " T:" + meanThrust + " yawZero:" + droneState.getZeroStates()[0];
            return debugText;
        }

        /**
         * This is the heartbeat of the system.
         * All the control calls are made in here.
         */
        @Override
        public void run() {
            again = true;
            long testLastTime = SystemClock.elapsedRealtimeNanos();
            ServerCallback onValuesChange = new ServerCallback() {
                @Override
                public void run(String json) {
                    setMeanTrust(Server.sCurrentStatus.getThrottle());
                    setPitchAngleTarget(Server.sCurrentStatus.getPitch());
                    setRollAngleTarget(Server.sCurrentStatus.getRoll());
                    setYawAngleTarget(Server.sCurrentStatus.getYaw());
                    if(Server.sCurrentStatus.isEmergency()) {
                        Log.e(CONTROLLER, "Emergency sequence initiated");
                        emergencyStop("Server command");
                    }
                }
            };

            ServerCallback onPidChange = new ServerCallback() {
                @Override
                public void run(String json) {
                    Log.e("PID", "PID coefficients have changed!!!");
                    yawController.setCoefficients(Server.sCurrentStatus.getPidYaw().getP(), Server.sCurrentStatus.getPidYaw().getI(), Server.sCurrentStatus.getPidYaw().getD());
                    pitchController.setCoefficients(Server.sCurrentStatus.getPidPitch().getP(), Server.sCurrentStatus.getPidPitch().getI(), Server.sCurrentStatus.getPidPitch().getD());
                    rollController.setCoefficients(Server.sCurrentStatus.getPidRoll().getP(), Server.sCurrentStatus.getPidRoll().getI(), Server.sCurrentStatus.getPidRoll().getD());
                }
            };
//
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            //Register callbacks to be called when the appropriate JSON is received
            Server.registerCallback(IPCKeys.THROTTLE, onValuesChange);
            Server.registerCallback(IPCKeys.YAW, onValuesChange);
            Server.registerCallback(IPCKeys.COORDINATES, onValuesChange);
            Server.registerCallback(IPCKeys.EMERGENCY, onValuesChange);
            Server.registerCallback(IPCKeys.PIDPITCH, onPidChange);
            Server.registerCallback(IPCKeys.PIDROLL, onPidChange);
            Server.registerCallback(IPCKeys.PIDYAW, onPidChange);

            while (again) {
                // This loop runs at a very high frequency (1kHz), but all the
                // control only occurs when new measurements arrive.
                if (!droneState.newMeasurementsReady()) {
                    SystemClock.sleep(1);
                    continue;
                }

                if (!zeroStateFlag) {
                    droneState.setCurrentStateAsZero();
                    zeroStateFlag = true;
                }

                // Get the sensors data.
                droneStateData = droneState.getState();

                currentYaw = droneStateData.yaw;
                currentPitch = droneStateData.pitch;
                currentRoll =  droneStateData.roll;
                float currentAltitude = droneStateData.baroElevation;

                long currentTime = droneStateData.time;
                float dt = ((float) (currentTime - previousTime)) / 1000000000.0f; // [s].
                previousTime = currentTime;

                if (Math.abs(dt) > 1.0) // In case of the counter has wrapped around.
                    continue;

                // Check for dangerous situations.
                if (regulatorEnabled) {
                    // If the quadcopter is too inclined, emergency stop it.
                    if (Math.abs(currentPitch) > MAX_SAFE_PITCH_ROLL ||
                            Math.abs(currentRoll) > MAX_SAFE_PITCH_ROLL) {
                        //TODO: Setup safety max rol/pitch values
//                        emergencyStop("Safe pitch or safe roll exceeded");
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
                    drone.updateSpeeds(yawForce,pitchForce,rollForce,altitudeForce);

                } else {
                    drone.setToZero();
                    yawForce = 0.0f;
                    pitchForce = 0.0f;
                    rollForce = 0.0f;
                    altitudeForce = 0.0f;
                }
            }
        }

        public void requestStop() {
            again = false;
        }

        private boolean again;
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

    /**
     * Thread used for debugging purposes only.
     * Logging debug data without flooding the ADT
     * To be replaced by the Flyver Logger
     * TODO: Delete and replace with Flyver Logger
     */
    public class DebugThread implements Runnable {

        public void run() {
            String debugText;
            Drone.MotorPowers motorPowers;
            while (true) {
                 debugText = drone.getDebugText();

             //   debugText = (Integer.toString(motorPowers.fc + 1000) + "| " + Integer.toString(motorPowers.fcc + 1000) + "| "
              //          + Integer.toString(motorPowers.rc + 1000) + "| " + Integer.toString(motorPowers.rcc + 1000));
              //  debugText = activity.mainController.controllerThread.getDebugText();
               //ioio.lib.spi.Log.i("Data", debugText);
                //Log.i("battery", Integer.toString(battery.getBatteryStatus()));
                //Log.i("battery v", Float.toString(battery.getBatteryVoltage()));
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    ioio.lib.spi.Log.e("Data", e.toString());
                }
            }
        }
    }
}