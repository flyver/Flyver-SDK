package co.flyver.flyvercore.DroneTypes;

/**
 * This is a Drone with configuration of Quadcopter X type of frame
 * It implements the specific control algorithm for this type of drone
 */
public class QuadCopterX implements Drone{
    MotorsPowers motorPowers = new MotorsPowers();

    @Override
    public void updateSpeeds(float yawForce, float pitchForce, float rollForce, float altitudeForce) {
        // Compute the power of each motor.
        double tempPowerFC;
        double tempPowerFCC;
        double tempPowerRC;
        double tempPowerRCC;

        tempPowerFC = altitudeForce; // Vertical "force".
        tempPowerFCC = altitudeForce; //
        tempPowerRC = altitudeForce; //
        tempPowerRCC = altitudeForce; //

        tempPowerFC += pitchForce; // Pitch "force".
        tempPowerFCC += pitchForce; //
        tempPowerRC -= pitchForce; //
        tempPowerRCC -= pitchForce; //

        tempPowerFC += rollForce; // Roll "force".
        tempPowerFCC -= rollForce; //
        tempPowerRC -= rollForce; //
        tempPowerRCC += rollForce; //

        tempPowerFC += yawForce; // Yaw "force".
        tempPowerFCC -= yawForce; //
        tempPowerRC += yawForce; //
        tempPowerRCC -= yawForce; //

        // Saturate the values
        motorPowers.fc = motorPowers.motorSaturation(tempPowerFC);
        motorPowers.fcc = motorPowers.motorSaturation(tempPowerFCC);
        motorPowers.rc = motorPowers.motorSaturation(tempPowerRC);
        motorPowers.rcc = motorPowers.motorSaturation(tempPowerRCC);


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
                return (int) MAX_MOTOR_POWER;
            else if (val < 0.0)
                return 0;
            else
                return (int) val;
        }
    }


}
