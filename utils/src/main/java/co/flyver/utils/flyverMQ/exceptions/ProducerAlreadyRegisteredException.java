package co.flyver.utils.flyverMQ.exceptions;

/**
 * Created by Petar Petrov on 12/16/14.
 */
public class ProducerAlreadyRegisteredException extends Exception {
    public ProducerAlreadyRegisteredException(String detailMessage) {
        super(detailMessage);
    }
}
