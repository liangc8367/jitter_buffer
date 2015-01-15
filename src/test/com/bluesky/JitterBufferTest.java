package test.com.bluesky;

import com.bluesky.JitterBuffer;
import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

public class JitterBufferTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();

        mJitterBuffer = new JitterBuffer<MyClass>(DEPTH, INTERVAL, UNIT);
    }

    public void tearDown() throws Exception {

    }

    public void testOfferObjectsWithSameSequence() throws Exception {
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

    public void testPoll() throws Exception {

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

    JitterBuffer<MyClass>   mJitterBuffer;

    static final int DEPTH = 6;
    static final long INTERVAL = 20;
    static final TimeUnit UNIT  = TimeUnit.SECONDS;
}