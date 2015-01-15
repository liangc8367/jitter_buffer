package com.bluesky;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * JitterBuffer, simple, to mitigate network jitter, able to absorb
 * up to (Interval * N) variation.
 *
 * Created by liangc on 14/01/15.
 */
public class JitterBuffer<E> {
    private final int mDepth;
    private final long mIntervalNanos;

    private short mDequeueSequence;

    private short mFirstSequence;
    private long mFirstArrivalNanos;
    private boolean mAwaitFirstObject = true;

    private final DelayQueue<SequenceObject> mQueue = new DelayQueue<SequenceObject>();;

    /** object that can be accepted by JitterBuffer */
    private class SequenceObject<E> implements Delayed {
        private short mSequence;
        private long mExpectedDequeueNanos;
        private final E mObject;

        public SequenceObject(final E object, short sequence ){
            mObject = object;
            mSequence = sequence;
            mExpectedDequeueNanos =
                    mFirstArrivalNanos + mIntervalNanos * (mSequence + mDepth - mFirstSequence);
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

            long compareeExpectedDequeueNanos = ((SequenceObject) o).mExpectedDequeueNanos;
            if( mExpectedDequeueNanos == compareeExpectedDequeueNanos ){
                return 0;
            } else if( mExpectedDequeueNanos < compareeExpectedDequeueNanos ){
                return -1;
            } else {
                return 1;
            }
        }

        /** method used by container to check the existance of same object
         *
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
     * @return true if JitterBuffer accepted the offer
     */
    public boolean offer(E object, short sequence){
        if( mAwaitFirstObject == true ){
            mAwaitFirstObject = false;
            mFirstArrivalNanos = System.nanoTime();
            mFirstSequence = sequence;
            SequenceObject<E> wrapper = this.new SequenceObject<E>(object, sequence);
            return mQueue.offer(wrapper);

        } else {
            if( sequence <= mDequeueSequence ){
                return false;
            }

            SequenceObject<E> wrapper = this.new SequenceObject<E>(object, sequence);
            if(mQueue.contains(wrapper)){
                return false;
            }

            return mQueue.offer(wrapper);
        }
    }

    /**
     * try to poll an expired object from the buffer.
     *  caller may be blocked until timeout expires.
     *
     *  NOTE: if we can't get expected object from the queue, we still increment sequence#,
     *        because that means the expected object had never been enqueued in time.
     *
     * @param timeout
     * @param unit
     * @return the oldest sequenceObject if it has expired, otherwise return null
     */
    public E poll(long timeout, TimeUnit unit){
        try {
            SequenceObject<E> wrapper = mQueue.poll(timeout, unit);
            return wrapper.getObject();
        } catch (InterruptedException e){
            return null;
        } finally {
            ++mDequeueSequence;
        }
    }

}