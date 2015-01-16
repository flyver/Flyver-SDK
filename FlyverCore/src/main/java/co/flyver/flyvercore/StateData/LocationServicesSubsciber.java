package co.flyver.flyvercore.StateData;

import co.flyver.flyvercore.MainControllers.MainController;
import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;

/**
 * Created by Tihomir Nedev on 15-1-9.
 * This class is for test purposes only
 */
public class LocationServicesSubsciber implements FlyverMQConsumer {

    /* Constants */
    public static String TOPIC = "LocationServices";
    DroneLocation droneLocation;
    /* End of */

    public LocationServicesSubsciber(){
        MainController.getInstance().getMessageQueue().registerConsumer(this, TOPIC);
    }
    @Override
    public void dataReceived(FlyverMQMessage message) {
        droneLocation = (DroneLocation) message.data;
       //Uncomment to log location Log.i("location", droneLocation.toString());
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
}
