package test.com.bluesky;

import com.bluesky.JitterBuffer;
import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

public class JitterBufferTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();

    }

    public void tearDown() throws Exception {

    }

    public void testOfferObjectsWithSameSequence() throws Exception {
        JitterBuffer<MyClass>   mJitterBuffer;
        mJitterBuffer = new JitterBuffer<MyClass>(DEPTH, INTERVAL, UNIT);
        // offer 1st object
        // try offer another object with the same sequence
        // verify that the 2nd object is discarded/ not in queue

        MyClass obj1 = new MyClass(1, (short)20);
        MyClass obj2 = new MyClass(2, (short)20);

        assertTrue(mJitterBuffer.offer(obj1, (short) 20));
        assertFalse(mJitterBuffer.offer(obj2, (short)20));

        // offer with expired object
        MyClass obj3 = new MyClass(3, (short)19);
        MyClass obj4 = new MyClass(4, (short)21);
        assertFalse(mJitterBuffer.offer(obj3, (short)19));
        assertTrue(mJitterBuffer.offer(obj4, (short)21));
    }

    public void testDequeueFirstObject() throws Exception {
        JitterBuffer<MyClass>   mJitterBuffer;
        mJitterBuffer = new JitterBuffer<MyClass>(DEPTH, INTERVAL, UNIT);


        MyClass obj1 = new MyClass(1, (short)50);
        MyClass obj2 = new MyClass(2, (short)52);
        MyClass obj3 = new MyClass(3, (short)51);

        assertTrue(mJitterBuffer.offer(obj1, (short)50));
        assertTrue(mJitterBuffer.offer(obj2, (short)52));
        assertTrue(mJitterBuffer.offer(obj3, (short)51));

        // now, let's dequeue
        MyClass o;
        long nano0, nano1;
        long seconds;

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj1);
        seconds  = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL * DEPTH) <= MARGIN );

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj3);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);


        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj2);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);

        //// now, try to packet lost in middle
        assertTrue(mJitterBuffer.offer(obj1, (short)53));
        assertTrue(mJitterBuffer.offer(obj2, (short)55));
        assertTrue(mJitterBuffer.offer(obj3, (short)56));

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj1);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, null);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj2);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);

        nano0 = System.nanoTime();
        o = mJitterBuffer.poll();
        nano1 = System.nanoTime();
        assertEquals(o, obj3);
        seconds = calSpentTime(nano0, nano1);
        assertTrue(Math.abs(seconds - INTERVAL) <= MARGIN);

    }

    private class MyClass{
        private final int id;
        public final short seq;

        public MyClass(int id,short seq){
            this.id = id;
            this.seq = seq;
        }

        public String toString() {
            return new String("MyClass: id = " + id + ", seq=" + seq);
        }
    }

    private long calSpentTime(long startNano, long stopNano){
        long cost = (UNIT.convert((stopNano - startNano), TimeUnit.NANOSECONDS));
        System.out.println("spent " + UNIT +" = " + cost);
        return cost;
    }

    static final int DEPTH = 6;
    static final long INTERVAL = 20;
    static final TimeUnit UNIT  = TimeUnit.MILLISECONDS;

    static final long MARGIN = INTERVAL * 20 / 100;
}