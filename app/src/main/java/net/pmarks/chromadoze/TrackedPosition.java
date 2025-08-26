package net.pmarks.chromadoze;

// TrackedPosition keeps track of a single position within
// an ArrayList, even as rows get rearranged by the DSLV.

public class TrackedPosition {
    // Start at -1, so the scratch position can be tracked as well.
    private static final int MINVAL = -1;

    // NOWHERE must be larger than any other value, for the math to work.
    public static final int NOWHERE = Integer.MAX_VALUE;

    private int mPos = NOWHERE;

    void setPos(int p) {
        if (!(MINVAL <= p && p < NOWHERE)) {
            throw new IllegalArgumentException("Out of range");
        }
        mPos = p;
    }

    int getPos() {
        return mPos;
    }

    // Move some item in the list.
    // If this position moved to nowhere, throw Deleted.
    // Otherwise, return true if this position moved at all.
    boolean move(int from, int to) throws Deleted {
        // from to result
        // ---- -- ------
        //   =  =  noop
        //   =  *  omg!
        //   <  >= -1
        //   <  <  noop
        //   >  <= +1
        //   >  >  noop
        if (mPos == NOWHERE) {
            throw new IllegalStateException();
        }
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException();
        }
        if (from == mPos) {
            if (to != mPos) {
                mPos = to;
                if (mPos == NOWHERE) {
                    throw new Deleted();
                }
                return true;
            }
        } else if (from < mPos) {
            if (to >= mPos) {
                mPos -= 1;
                if (mPos < MINVAL) {
                    throw new IllegalStateException();
                }
                return true;
            }
        } else if (from > mPos) {
            if (to <= mPos) {
                mPos += 1;
                if (mPos >= NOWHERE) {
                    throw new IllegalStateException();
                }
                return true;
            }
        } else {
            throw new RuntimeException();
        }
        return false;
    }

    public static class Deleted extends Exception {
    }
}
