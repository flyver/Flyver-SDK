package co.flyver.androidrc.Server;

import co.flyver.IPC.IPCContainers;
import co.flyver.IPC.IPCKeys;

/**
 * Created by Petar Petrov on 1/6/15.
 */

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

    Status() {
        //empty on purpose
    }

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

    public void setEmergency(IPCContainers.JSONTuple JSONAction) {
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
        if(mEmergency) {
            return;
        }
        this.mPitch = mPitch / 5;
    }

    public float getRoll() {
        return mRoll;
    }

    public void setRoll(float mRoll) {
        if(mEmergency) {
            return;
        }
        //TODO: division by five for testing purposes
        this.mRoll = (mRoll * (-1)) / 5;
    }

    public float getYaw() {
        return mYaw;
    }

    /**
     * Changes the current yaw based on steps
     * If the yaw exceeds 180 deg, it overflows to -180 deg or lower, and vice versa
     * @param jsonTriple - JSONTriple<String, String, Float>
     */
    public void setYaw(IPCContainers.JSONTriple<String, String, Float> jsonTriple) {
        if(mEmergency) {
            return;
        }
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
     * @param jsonTriple - JSONTriple<String, String, Float>
     */
    public void setThrottle(IPCContainers.JSONTriple<String, String, Float> jsonTriple) {
        if(mEmergency) {
            return;
        }
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
