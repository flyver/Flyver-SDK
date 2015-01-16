package co.flyver.utils.flyverMQ.interfaces;

/**
 * Created by Petar Petrov on 12/17/14.
 */
public interface FlyverMQCallback {
    /**
     * Called every time a producer is registered to the message queue
     * Used to notify components for new producers that they might be interested in
     * @param topic String, the topic of the messages sent by the producer
     */
    public void producerRegistered(String topic);

    /**
     * Called every time a producer is unregistered or replaced from the system
     * Used to notify components that a producer has disappeared, so they should unregister
     * for it's messages
     * @param topic String, the topic of the messages of the unregistered producer
     */
    public void producerUnregistered(String topic);
}
