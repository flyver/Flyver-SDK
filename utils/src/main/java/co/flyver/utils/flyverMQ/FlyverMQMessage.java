package co.flyver.utils.flyverMQ;

/**
 * Created by Petar Petrov on 12/16/14.
 */

/**
 * Container for the Messages passed around via SimpleMQ
 */
public class FlyverMQMessage {

    public static class MessageBuilder {

        public MessageBuilder setTtl(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public MessageBuilder setPriority(short priority) {
            this.priority = priority;
            return this;
        }

        public MessageBuilder setMessageId(long messageId) {
            this.messageId = messageId;
            return this;
        }

        public MessageBuilder setTopic(String topic) {
            this.topic = topic;
            return this;
        }

        public MessageBuilder setCreationTime(long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        public MessageBuilder setData(Object data) {
            this.data = data;
            return this;
        }

        public FlyverMQMessage build() {
            return new FlyverMQMessage(ttl, priority, messageId, topic, creationTime, data);
        }

        Long ttl;
        Short priority;
        Long messageId;
        String topic;
        Long creationTime;
        Object data;
    }

    /**
     * Time to live of the message in milliseconds
     * milliseconds = seconds / 1000
     * Compare with the creationTime timestamp,
     * and discard the message if the difference is
     * above a predefined threshold
     */
    public Long ttl;

    /**
     * Priority of the message
     * Dispatch messages with higher priorities first
     */
    public Short priority;

    /**
     * Message ID
     */
    public Long messageId;

    /**
     * Topic/subject of the message
     * Defined by the producer
     * Clients subscribe for messages based on this topic
     */
    public String topic;

    /**
     * Creation time in milliseconds.
     * milliseconds = seconds / 1000
     * Used to discard outlived messages
     * based on the ttl
     */
    public Long creationTime;

    /**
     * User-provided data carried by the message
     */
    public Object data;

    /**
     * Private constructor, message should be created via Builder
     */
    private FlyverMQMessage(long ttl, short priority, long messageId, String topic, long creationTime, Object data) {
        this.ttl = ttl;
        this.priority = priority;
        this.messageId = messageId;
        this.topic = topic;
        this.creationTime = creationTime;
        this.data = data;
    }
}
