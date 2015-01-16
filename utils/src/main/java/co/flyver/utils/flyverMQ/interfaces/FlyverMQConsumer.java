package co.flyver.utils.flyverMQ.interfaces;

import co.flyver.utils.flyverMQ.FlyverMQMessage;

/**
 * Created by Petar Petrov on 12/9/14.
 */
public interface FlyverMQConsumer {
    /**
     * Data recieved callback for the consumer.
     * Called when a message with the associated topic
     * for the consumer is received in the message queue
     * @param message SimpleMQMessage data container
     */
    public void dataReceived(FlyverMQMessage message);

    /**
     * Consumer unregister hook
     * Called when the consumer is removed from the message queue
     */
    public void unregistered();

    //TODO: clarify if consumers need onPause/onResume functionallity
    public void paused();
    public void resumed();
}
