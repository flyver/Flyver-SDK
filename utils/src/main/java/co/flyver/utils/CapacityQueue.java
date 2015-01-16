package co.flyver.utils;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Petar Petrov on 12/9/14.
 */
/**
 * Fifo queue that has limited capacity, passed via the constructor.
 * When the capacity is reached, the oldest element of the queue is being removed
 * and the new one is added
 * @param <E>
 */
public class CapacityQueue<E> extends LinkedBlockingQueue<E> {
        private int limit;

        public CapacityQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean add(E e) {
            boolean added = super.add(e);
            while(added && size() > limit) {
                super.remove();
            }
            return added;
        }
    }
