package co.flyver.flyvercore.DroneTypes;

import android.util.Log;

/**
 * This is a Drone with configuration of Quadcopter X type of frame
 * It implements the specific control algorithm for this type of drone
 */
public class QuadCopterX implements Drone{
    MotorsPowers motorPowers = new MotorsPowers();
    private final String TAG = "QUADX";

    @Override
    public void updateSpeeds(float yawForce, float pitchForce, float rollForce, float altitudeForce) {
        // Compute the power of each motor.
        double tempPowerFCW;
        double tempPowerFCCW;
        double tempPowerRCW;
        double tempPowerRCCW;

        tempPowerFCW = altitudeForce; // Vertical "force".
        tempPowerFCCW = altitudeForce; //
        tempPowerRCW = altitudeForce; //
        tempPowerRCCW = altitudeForce; //

        tempPowerFCW += pitchForce; // Pitch "force".
        tempPowerFCCW += pitchForce; //
        tempPowerRCW -= pitchForce; //
        tempPowerRCCW -= pitchForce; //

        tempPowerFCW += rollForce; // Roll "force".
        tempPowerFCCW -= rollForce; //
        tempPowerRCW -= rollForce; //
        tempPowerRCCW += rollForce; //

        tempPowerFCW += yawForce; // Yaw "force".
        tempPowerFCCW -= yawForce; //
        tempPowerRCW += yawForce; //
        tempPowerRCCW -= yawForce; //

        // Saturate the values
        Log.d(TAG,String.format("Front CW: %f Front CCW: %f Rear CW: %f Rear CCW: %f \n ", tempPowerFCW, tempPowerFCCW, tempPowerRCW, tempPowerRCCW));
        motorPowers.fc = motorPowers.motorSaturation(tempPowerFCW);
        motorPowers.fcc = motorPowers.motorSaturation(tempPowerFCCW);
        motorPowers.rc = motorPowers.motorSaturation(tempPowerRCW);
        motorPowers.rcc = motorPowers.motorSaturation(tempPowerRCCW);


    }
    public void setToZero(){
        motorPowers.fc = 0;
        motorPowers.fcc = 0;
        motorPowers.rc = 0;
        motorPowers.rcc = 0;
    }

    public String getDebugText(){
        String debugText = (Integer.toString(motorPowers.fc + 1000) + "| " + Integer.toString(motorPowers.fcc + 1000) + "| "
                + Integer.toString(motorPowers.rc + 1000) + "| " + Integer.toString(motorPowers.rcc + 1000));
        return debugText;
    }
    @Override
    public float[] getMotorPowers() {
        float[] powers = {motorPowers.fc,motorPowers.fcc,motorPowers.rc,motorPowers.rcc};
        return powers;
    }

    private class MotorsPowers extends MotorPowers {
        int MAX_MOTOR_POWER = 1023;
        public int fc, fcc, rc, rcc; // 0-1023 (10 bits values).

         public MotorsPowers(){
            this.fc = fc;
            this.fcc = fc;
            this.rc = rc;
            this.rcc = rcc;
        }
        private int getMean() {
            return (fc + fcc + rc + rcc) / 4;
        }

        private int motorSaturation(double val) {
            if (val > MAX_MOTOR_POWER)
                return MAX_MOTOR_POWER;
            else if (val < 0.0)
                return 0;
            else
                return (int) val;
        }
    }


}
