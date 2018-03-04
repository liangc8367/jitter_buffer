package com.bluesky;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JitterBuffer, simple, to mitigate network jitter, able to absorb
 * up to (Interval * N) variation.
 *
 * Created by liangc on 14/01/15.
 */
public class JitterBuffer<E> {
    private final int mDepth;
    private final long mIntervalNanos;

    private long mDequeueSequence;

    private long mFirstSequence;
    private long mFirstArrivalNanos;
    private boolean mAwaitFirstObject = true;

    private final DelayQueue<SequenceObject> mQueue = new DelayQueue<SequenceObject>();
    private final Lock mLock = new ReentrantLock();

    /** object that can be accepted by JitterBuffer */
    private static class SequenceObject<E> implements Delayed {
        private short mSequence;
        private long mExpectedDequeueNanos;
        private final E mObject;

        public SequenceObject(final E object, short sequence, long expectedDequeueNanos ){
            mObject = object;
            mSequence = sequence;
            mExpectedDequeueNanos = expectedDequeueNanos;
        }

        public E getObject(){
            return mObject;
        }

        /**
         * Returns the remaining delay associated with this object, in the
         * given time unit.
         *
         * @param unit the time unit
         * @return the remaining delay; zero or negative values indicate
         * that the delay has already elapsed
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert( mExpectedDequeueNanos - System.nanoTime(), TimeUnit.NANOSECONDS );
        }

        /**
        * @param   o the object to be compared.
        * @return  a negative integer, zero, or a positive integer as this object
        *          is less than, equal to, or greater than the specified object.
        *
        * @throws NullPointerException if the specified object is null
        * @throws ClassCastException if the specified object's type prevents it
        *         from being compared to this object.
         */
        @Override
        public int compareTo(Delayed o) {
            if( o == this ) {
                return 0;
            }

            long compareeSequence = ((SequenceObject) o).mSequence;
            if( mSequence == compareeSequence ){
                return 0;
            } else if( mSequence < compareeSequence ){
                return -1;
            } else {
                return 1;
            }
        }

        /** method used by container to check the existance of same object
         * TODO: add hashCode()
         * @param obj
         * @return
         */
        @Override
        public boolean equals(Object obj) {
            return compareTo((Delayed)obj) == 0;
        }
    }

    public JitterBuffer (int depth, long interval, TimeUnit unit){
        mDepth = depth;
        mIntervalNanos = unit.toNanos(interval);
    }


    /** offer SequenceObject into JitterBuffer. JitterBuffer may discard/reject the offer
     *  sliently, if the object is earlier than the last dequenced object, orif the same object
     *  had already enqueued.
     *
     *  TODO: right now, to simpilfy the imp, I didn't consider to handle seq# overflow yet,
     *  so, the 1st seq would be better being a small value, i.e. 0.
     *
     * @return true if JitterBuffer accepted the offer
     */
    public boolean offer(E object, short sequence){
        mLock.lock();
        try {
            if (mAwaitFirstObject == true) {
                mAwaitFirstObject = false;
                mFirstArrivalNanos = System.nanoTime();
                mDequeueSequence = mFirstSequence = sequence;

                long exp = mFirstArrivalNanos + mIntervalNanos * (sequence + mDepth - mFirstSequence);

                SequenceObject<E> wrapper = new SequenceObject<E>(object, sequence, exp);
                return mQueue.offer(wrapper);

            } else {
                if (sequence < mDequeueSequence) {
                    return false;
                }

                long exp = mFirstArrivalNanos + mIntervalNanos * (sequence + mDepth - mFirstSequence);
                SequenceObject<E> wrapper = new SequenceObject<E>(object, sequence, exp);
                if (mQueue.contains(wrapper)) {
                    return false;
                }

                return mQueue.offer(wrapper);
            }
        } finally {
            mLock.unlock();
        }
    }

    /**
     * try to poll an expired object from the buffer.
     *  caller may be blocked until timeout expires.
     *
     *  NOTE: if we can't get expected object from the queue, we still increment sequence#,
     *        because that means the expected object had never been enqueued in time. Because
     *        of this reason, caller shall set parameters carefully.
     *
     * @param timeout
     * @param unit
     * @return the oldest sequenceObject if it has expired, otherwise return null
     */
    private E poll(long timeout, TimeUnit unit){
        try {
            SequenceObject<E> wrapper = mQueue.poll(timeout, unit);
            if( wrapper == null ){
                return null;
            } else {
                return wrapper.getObject();
            }
        } catch (InterruptedException e){
            return null;  //TODO: throw exception
        } finally {
            ++mDequeueSequence;
        }
    }

    public E poll(){
        long timeout;
        if( mDequeueSequence == mFirstSequence ){
            timeout = mIntervalNanos * mDepth;
        } else {
            timeout = mIntervalNanos;
        }
        return poll(timeout, TimeUnit.NANOSECONDS);
    }

    /**
     * reset the jitter buffer, and discard any containees.
     */
    public void reset() {
        mLock.lock();
        try {
            mQueue.clear();
            mAwaitFirstObject = true;
        } finally {
            mLock.unlock();
        }
    }

    public long getDequeueSequence(){
        return mDequeueSequence;
    }
}
