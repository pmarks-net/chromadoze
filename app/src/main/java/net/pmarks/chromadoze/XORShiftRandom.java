// XORShift is supposedly better and faster than java.util.Random.
// This algorithm is from:
// http://www.javamex.com/tutorials/random_numbers/xorshift.shtml

package net.pmarks.chromadoze;

class XORShiftRandom {
    private long mState = System.nanoTime();

    public long nextLong() {
        long x = mState;
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        mState = x;
        return x;
    }

    // Get a random number from [0, limit), for small values of limit.
    public int nextInt(int limit) {
        return ((int) nextLong() & 0x7FFFFFFF) % limit;
    }
}
