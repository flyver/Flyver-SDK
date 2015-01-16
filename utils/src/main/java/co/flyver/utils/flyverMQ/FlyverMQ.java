package co.flyver.utils.flyverMQ;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.flyver.utils.CapacityQueue;
import co.flyver.utils.flyverMQ.exceptions.NoSuchTopicException;
import co.flyver.utils.flyverMQ.exceptions.ProducerAlreadyRegisteredException;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQCallback;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;

/**
 * Created by Petar Petrov on 12/9/14.
 */
public class FlyverMQ {


    /**
     * Helper class
     * Used to easily associate producers with an internal queue for their events
     * Constructor can be passed only producer, and the queue will be dynamic LinkedList,
     * or producer and size, and the queue will be CapacityQueue, which automatically removes
     * the oldest objects, when it's capacity is reached
     */
    private class SimpleMQProducerWrapper {
        private FlyverMQProducer producer;

        public FlyverMQProducer getProducer() {
            return producer;
        }

        public BlockingQueue<FlyverMQMessage> getQueue() {
            return queue;
        }

        private BlockingQueue<FlyverMQMessage> queue;

        SimpleMQProducerWrapper(FlyverMQProducer producer) {
            this.producer = producer;
            queue = new LinkedBlockingQueue<>();
        }

        SimpleMQProducerWrapper(FlyverMQProducer producer, int size) {
            this.producer = producer;
            queue = new CapacityQueue<>(size);
        }

        //prevent instantiating with an empty constructor
        private SimpleMQProducerWrapper() {
        }

        public int getEventCount() {
            return queue.size();
        }
    }


    /**
     * Helper class wrapping around asynchronous consumers
     * Used to associate them with an internal semaphore
     * as to avoid concurency issues
     */
    protected class SimpleMQAsyncConsumerWrapper {
        private Semaphore semaphore = new Semaphore(1);
        private FlyverMQConsumer consumer;
        private String topic;

        public Semaphore getSemaphore() {
            return semaphore;
        }

        public FlyverMQConsumer getConsumer() {
            return consumer;
        }

        public SimpleMQAsyncConsumerWrapper(FlyverMQConsumer consumer, String topic) {
            this.consumer = consumer;
            this.topic = topic;
        }

        public String getTopic() {
            return topic;
        }
    }

    /**
     * Helper class, that wraps the real-time consumers
     * with an internal queue for the messages they are subscribed to,
     * as to guarantee delivery and processing of every message received
     */
    private class SimpleMQRTConsumerWrapper {
        private FlyverMQConsumer consumer;
        private Thread worker;
        private LinkedList<FlyverMQMessage> queue;
        private ExecutorService executorService = Executors.newSingleThreadExecutor();
        boolean empty = true;

        private SimpleMQRTConsumerWrapper() {
            //prevent instantiating with empty constructor
        }

        SimpleMQRTConsumerWrapper(FlyverMQConsumer consumer) {
            this.consumer = consumer;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    doWork();
                }
            });
            worker.start();
        }

        /**
         * Adds a message to the queue, and if it's empty,
         * notifies the worker thread, associated with the wrapper,
         * that there are messages ready for processing
         *
         * @param message
         */
        public void addMessage(FlyverMQMessage message) {
            queue.add(message);
            if (empty) {
                empty = false;
                this.notify();
            }
        }

        public int getEventCount() {
            return queue.size();
        }

        /**
         * Loops until all messages in the queue have been processed
         * If no more messages are left, it waits until notified
         * that more messages have been added
         */
        private void doWork() {
            //noinspection InfiniteLoopStatement
            while (true) {
                final FlyverMQMessage message = queue.pop();
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        consumer.dataReceived(message);
                    }
                });
                if (queue.isEmpty()) {
                    empty = true;
                }
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Helper class intended for running in a worker thread
     * it polls the producers and gets
     * the messages that are on top of their respective queue,
     * then broadcasts them to every client, that is registered
     * for the message's topic. The client's dataReceived(SimpleMQMessage)
     * method is executed in a different thread, managed by a ThreadPool implemented
     * via ExecutorService
     */
    private class SimpleMQWorker implements Runnable {
        FlyverMQMessage message;
        ExecutorService executorService = Executors.newFixedThreadPool(50);

        @Override
        public void run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //noinspection InfiniteLoopStatement
            while (true) {
                //loop over all registered producers
                for (String topic : topics.keySet()) {
                    //get the topmost event of the queue, associated with the current producer
                    //sometimes tries to poll past the index of the queue
                    //swallow the exception, and try on the next iteration again
                    try {
                        message = topics.get(topic).poll();
                        if (message == null) {
                            continue;
                        }
                    } catch (NoSuchElementException e) {
                        //e.printStackTrace();
                        continue;
                    }
                    //get the linked list containing all consumers registered for the topic
                    //and broadcast the message to all of them
                    if (asyncConsumers.get(message.topic) == null) {
                        Log.e("MQ", "No consumers present for topic ".concat(message.topic));
                        continue;
                    }
                    for (SimpleMQAsyncConsumerWrapper wrapper : asyncConsumers.get(message.topic)) {
                        //post the client's dataReceived method to the ThreadPool
                        if(!wrapper.topic.equals(message.topic)) {
                            Log.e(TAG, "message intended for topic ".concat(message.topic).concat(" is sent to ".concat(wrapper.getTopic())));
                        }
                        sendMsg(wrapper, message);
                    }
                }
            }
        }

        private void sendMsg(final SimpleMQAsyncConsumerWrapper wrapper, final FlyverMQMessage msg) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        wrapper.getSemaphore().acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    wrapper.getConsumer().dataReceived(msg);
                    wrapper.getSemaphore().release();
                }
            });
        }
    }

    private static FlyverMQ instance;
    private final String TAG = "SimpleMQ";
    private final String REGISTEREDMSG = "Producer already registered: ";
    private final String NOTOPICMSG = "No such topic exists : ";
    protected Map<String, LinkedList<FlyverMQProducer>> producers = new HashMap<>();
    protected Map<String, LinkedList<SimpleMQAsyncConsumerWrapper>> asyncConsumers = new HashMap<>();
    protected Map<String, LinkedList<FlyverMQConsumer>> rtConsumers = new HashMap<>();
    protected LinkedList<FlyverMQCallback> registeredComponentCallbacks = new LinkedList<>();
    protected Map<String, BlockingQueue<FlyverMQMessage>> topics = new HashMap<>();
    private FlyverMQSocketServer socketServer;

    public FlyverMQ() {
    }

    public static FlyverMQ getInstance() {
        if (instance == null) {
            instance = new FlyverMQ().init();
        }
        return instance;
    }

    /**
     * Initializes the internals of the message queue and starts it's worker threads
     *
     * @return this, as to allow chaining
     */
    public FlyverMQ init() {
        final FlyverMQ flyverMQref = this;
        Thread networkIO = new Thread(new Runnable() {
            @Override
            public void run() {
                socketServer = new FlyverMQSocketServer(flyverMQref);
            }
        });
        networkIO.start();
        Thread worker = new Thread(new SimpleMQWorker());
        worker.setDaemon(true);
        worker.start();
        return this;
    }

    /**
     * Register an object, implementing the SimpleMQCallback interface
     * which is used to monitor changes in the producers registration and deregistration
     * from the components
     *
     * @param callback SimpleMQCallback, implementing onRegistered and onUnregistered methods
     * @return this, as to allow chaining
     */
    public FlyverMQ producersStatusCallback(FlyverMQCallback callback) {
        registeredComponentCallbacks.add(callback);
        return this;
    }

    /**
     * Register an Object implementing the SimpleMQProducer interface to a topic
     * All messages by this producer are enqueued for later processing
     *
     * @param producer       Class implementing SimpleMQProducer interface
     * @param topic          String defining the topic of the producer
     * @param removeExisting Set to true to remove previously registered producer for the topic
     * @return this, to allow chaining
     * @throws ProducerAlreadyRegisteredException - Throws the already registered exception if a producer is already registered,
     *                                            and the removeExisting flag is set to false
     */
    public FlyverMQ registerProducer(FlyverMQProducer producer, String topic, boolean removeExisting) throws ProducerAlreadyRegisteredException {
        if (topicExists(topic) && !removeExisting) {
            throw new ProducerAlreadyRegisteredException(REGISTEREDMSG.concat(topic));
        }
        if (topicExists(topic)) {
            try {
                unregisterProducer(topic);
            } catch (NoSuchTopicException e) {
                e.printStackTrace();
            }
        }

        if (!producers.containsKey(topic)) {
            producers.put(topic, new LinkedList<FlyverMQProducer>());
        } else {
            producers.get(topic).add(producer);
        }
        if (!topics.containsKey(topic)) {
            topics.put(topic, new LinkedBlockingQueue<FlyverMQMessage>());
        }
        for (FlyverMQCallback callback : registeredComponentCallbacks) {
            callback.producerRegistered(topic);
        }
        Log.d(TAG, "Producer registered ".concat(topic));
        return this;
    }

    /**
     * Register an Object implementing the SimpleMQProducer interface to a topic
     * All messages by this producer are enqueued for later processing
     * The internal queue of the SimpleMQ implementing object is limited to the number
     * passed as the fourth parameter. Any events that are passed over that limit
     * discard the oldest events, and are enqueued
     *
     * @param producer       Class implementing SimpleMQProducer interface
     * @param topic          String defining the topic of the producer
     * @param removeExisting Set to true to remove previously registered producer for the topic
     * @param maxQueueEvents int, the limit of the queue
     * @return this, to allow chaining
     * @throws ProducerAlreadyRegisteredException - Throws the already registered exception if a producer is already registered,
     *                                            and the removeExisting flag is set to false
     */
    public FlyverMQ registerProducer(FlyverMQProducer producer, String topic, boolean removeExisting, int maxQueueEvents) throws ProducerAlreadyRegisteredException {
        if (!removeExisting && topicExists(topic)) {
            throw new ProducerAlreadyRegisteredException(REGISTEREDMSG.concat(topic));
        }
        if (topicExists(topic)) {
            try {
                unregisterProducer(topic);
            } catch (NoSuchTopicException e) {
                e.printStackTrace();
            }
        }
        if (!producers.containsKey(topic)) {
            producers.put(topic, new LinkedList<FlyverMQProducer>());
        } else {
            producers.get(topic).add(producer);
        }
        if (!topics.containsKey(topic)) {
            topics.put(topic, new LinkedBlockingQueue<FlyverMQMessage>());
        }
        for (FlyverMQCallback callback : registeredComponentCallbacks) {
            callback.producerRegistered(topic);
        }
        Log.d(TAG, "Producer registered ".concat(topic));
        return this;
    }

    /**
     * Removes the producer for a supplied topic from the list of producers
     * If no producer is present for the supplied topic, an exception is thrown
     *
     * @param topic the topic for which the producer is to be removed
     * @return this, to allow chaining
     * @throws NoSuchTopicException - thrown if no producer is registered for the requested topic
     */
    public FlyverMQ unregisterProducer(String topic) throws NoSuchTopicException {
        if (!topicExists(topic)) {
            throw new NoSuchTopicException(NOTOPICMSG.concat(topic));
        } else {
//            producers.get(topic).unregistered();
            for (FlyverMQProducer producer : producers.get(topic)) {
                producer.unregistered();
            }
            producers.remove(topic);
            for (FlyverMQCallback callback : registeredComponentCallbacks) {
                callback.producerUnregistered(topic);
            }
            return this;
        }
    }

    /**
     * Register a consumer for a topic.
     * More than one consumer may be registered for a single topic
     *
     * @param consumer Object implementing the SimpleMQConsumer interface
     * @param topic    String defining the topic that the consumer should receive
     * @return - this, to allow chaining
     */
    public FlyverMQ registerConsumer(FlyverMQConsumer consumer, String topic) {
        if (!asyncConsumers.containsKey(topic)) {
            asyncConsumers.put(topic, new LinkedList<SimpleMQAsyncConsumerWrapper>());
            asyncConsumers.get(topic).add(new SimpleMQAsyncConsumerWrapper(consumer, topic));
        } else {
            asyncConsumers.get(topic).add(new SimpleMQAsyncConsumerWrapper(consumer, topic));
        }
        return this;
    }

    /**
     * Removes all enqueued message for the supplied topic.
     * Throws exception if no producers are registered for the supplied topic
     *
     * @param topic string defining the topic that is to be cleared
     * @return this, to allow chaining
     * @throws NoSuchTopicException - thrown if the topic has no registered producers
     */
    public FlyverMQ clearTopic(String topic) throws NoSuchTopicException {
        if (topicExists(topic)) {
            topics.get(topic).clear();
        } else {
            throw new NoSuchTopicException(NOTOPICMSG.concat(topic));
        }
        return this;
    }

    /**
     * Returns the count of all events in the queue, associated with the topic
     * Throws exception if no such topic is present
     *
     * @param topic String defining the topic for which the event count is requested
     * @return int, count of the events in the queue
     * @throws NoSuchTopicException - thrown if no such topic is registered
     */
    public int getEventCount(String topic) throws NoSuchTopicException {
        if (!topicExists(topic)) {
            throw new NoSuchTopicException(NOTOPICMSG.concat(topic));
        } else {
            return topics.get(topic).size();
        }
    }

    /**
     * Adds message to the message queue associated with a topic
     *
     * @param producer - producer of the data
     * @param data     string with the data contained in the message
     * @return boolean, true if adding the message to the queue is successful, false otherwise
     */
    protected boolean addMessage(FlyverMQProducer producer, FlyverMQMessage data) throws NoSuchTopicException {
        if (!topics.containsKey(producer.topic)) {
            throw new NoSuchTopicException(NOTOPICMSG);
        }
        return topics.get(producer.topic).add(data);
    }

    /**
     * Get the first message of the queue for a topic
     *
     * @param topic    String defining the topic that should provide the message
     * @param blocking boolean, if processing the message is synchronous or not
     * @return SimpleMQMessage, the message on top of the queue
     * @throws NoSuchTopicException - thrown if no such  topic is in existence
     */
    public FlyverMQMessage getMessage(String topic, boolean blocking) throws NoSuchTopicException {
        if (!topicExists(topic)) {
            throw new NoSuchTopicException(NOTOPICMSG.concat(topic));
        }
        return topics.get(topic).poll();
    }

    /**
     * Get the first N messages on the top of the queue
     *
     * @param topic    String defining the topic that should provide the messages
     * @param blocking boolean, if processing the message is synchronous or not
     * @param count    int, the number N of the requested messages
     * @return LinkedList containing the requested messages
     * @throws NoSuchTopicException - thrown if no such topic is in existence
     */
    public LinkedList<FlyverMQMessage> getMessages(String topic, boolean blocking, int count) throws NoSuchTopicException {
        if (!topicExists(topic)) {
            throw new NoSuchTopicException(NOTOPICMSG + topic);
        }
        LinkedList<FlyverMQMessage> messages = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            FlyverMQMessage message = topics.get(topic).poll();
            if (message == null) {
                return messages;
            }
            messages.add(message);
        }
        return messages;
    }

    /**
     * Get all the topics, that have registered producers to them
     * can match against strings and java-formatted regular expressions alike
     *
     * @param topic String for the topic that is being searched.
     *              If passed null value or wildcard "*" returns all registered topics
     *              else returns all topics starting with the passed string
     * @return - LinkedList containing the matched topics
     */
    protected LinkedList<String> listTopics(String topic) {
        LinkedList<String> topics = new LinkedList<>();
        if (topic == null || topic.equals("*")) {
            for (String s : producers.keySet()) {
                topics.add(s);
            }
        } else {
            Pattern pattern = Pattern.compile("\\^".concat(topic).concat(".+"));
            Matcher matcher;
            for (String s : producers.keySet()) {
                matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    topics.add(s);
                }
            }
        }
        return topics;
    }

    /**
     * Check if the supplied topic has any registered producers to it
     *
     * @param topic string defining the topic
     * @return boolean, true if the topic has any active producers, false otherwise
     */
    public boolean topicExists(String topic) {
        return producers.containsKey(topic);
    }

}
