// Copyright (C) 2011  Paul Marks  http://www.pmarks.net/
//
// This file is part of ChromaDoze.
//
// ChromaDoze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ChromaDoze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ChromaDoze.  If not, see <http://www.gnu.org/licenses/>.

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
