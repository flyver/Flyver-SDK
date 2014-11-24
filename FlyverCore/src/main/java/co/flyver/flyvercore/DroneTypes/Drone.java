package co.flyver.flyvercore.DroneTypes;

/**
 * All types of drones shall implement this interface
 * TODO: Drone specific control abstraction here
 */
public interface Drone{

    public void updateSpeeds(float yawForce, float pitchForce, float rollForce, float altitudeForce);
    public void setToZero();
    public String getDebugText();
    public float[] getMotorPowers();
    class MotorPowers {};
    // TODO: Fix working only with IOIO
}
