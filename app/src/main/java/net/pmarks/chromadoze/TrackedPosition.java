// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
//
// This file is part of Chroma Doze.
//
// Chroma Doze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Chroma Doze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Chroma Doze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

// TrackedPosition keeps track of a single position within
// an ArrayList, even as rows get rearranged by the DSLV.

public class TrackedPosition {
    // Start at -1, so the scratch position can be tracked as well.
    public static final int MINVAL = -1;

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
        private static final long serialVersionUID = 5670022571402210462L;
    }
}
