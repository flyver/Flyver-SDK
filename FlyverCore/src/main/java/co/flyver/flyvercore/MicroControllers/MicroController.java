package co.flyver.flyvercore.MicroControllers;

import ioio.lib.api.exception.ConnectionLostException;

/**
 * All microcontrollers used with Flyver shall implement this interface
 * A new MicroController shall be made for each Drone type
 *
 */
public interface MicroController {
    /**
     * Initialization Setup
     * @throws ConnectionLostException
     */
    public void setup() throws  ConnectionLostException;

    /**
     * Loops the microcontroller.
     * @throws InterruptedException
     * @throws ConnectionLostException
     */
    public void loop() throws  InterruptedException, ConnectionLostException;
    // TODO: Fix the excetions
}
