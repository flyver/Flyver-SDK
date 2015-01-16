package co.flyver.utils.flyverMQ;

import java.util.LinkedList;

/**
 * Created by Petar Petrov on 1/13/15.
 */

/**
 * Common interface to some of the most used MQ functions
 */
public class FlyverMQCtl {
    FlyverMQ mq;
    static FlyverMQCtl instance;

    private FlyverMQCtl() {
        mq = FlyverMQ.getInstance();
    }

    public static FlyverMQCtl getInstance() {
        if(instance == null) {
            instance = new FlyverMQCtl();
        }
        return instance;
    }

    public LinkedList<String> listAllTopics() {
        return mq.listTopics(null);
    }

    public LinkedList<String> listTopics(String regex) {
        return mq.listTopics(regex);
    }

    public boolean topicExists(String topic) {
        return mq.topics.containsKey(topic);
    }

    public void removeProducer(String topic, int index) {
        if(mq.producers.containsKey(topic) && mq.producers.get(topic).size() <= index) {
            mq.producers.get(topic).remove(index);
        }
    }

    public void removeAsyncConsumer(String topic, int index) {
        if(mq.asyncConsumers.containsKey(topic) && mq.asyncConsumers.get(topic).size() <= index) {
            mq.asyncConsumers.get(topic).remove(index);
        }
    }

    public void removeRtConsumer(String topic, int index) {
        if(mq.rtConsumers.containsKey(topic) && mq.rtConsumers.get(topic).size() <= index) {
            mq.rtConsumers.get(topic).remove(index);
        }
    }

    public void pauseProducer(String topic, int index) {
        if(mq.producers.containsKey(topic) && mq.producers.get(topic).size() <= index) {
            mq.producers.get(topic).get(index).onPause();
        }

    }

    public void resumeProducer(String topic, int index) {
        if(mq.producers.containsKey(topic) && mq.producers.get(topic).size() <= index) {
            mq.producers.get(topic).get(index).onResume();
        }
    }

    public void pauseConsumer(String topic, int index) {
        if(mq.asyncConsumers.containsKey(topic) && mq.asyncConsumers.get(topic).size() <= index) {
            mq.asyncConsumers.get(topic).get(index).getConsumer().paused();
        }

    }

    public void resumeConsumer(String topic, int index) {
        if(mq.asyncConsumers.containsKey(topic) && mq.asyncConsumers.get(topic).size() <= index) {
            mq.asyncConsumers.get(topic).get(index).getConsumer().resumed();
        }

    }
}
