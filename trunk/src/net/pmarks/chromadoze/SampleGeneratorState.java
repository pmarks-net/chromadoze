// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
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

public class SampleGeneratorState {
    // List of possible chunk stages.
    public static final int S_FIRST_SMALL = 0;
    public static final int S_OTHER_SMALL = 1;
    public static final int S_FIRST_VOLUME = 2;
    public static final int S_OTHER_VOLUME = 3;
    public static final int S_LAST_VOLUME = 4;
    public static final int S_LARGE_NOCLIP = 5;

    // How many small preview chunks to generate at first.
    private static final int N_SMALL_CHUNKS = 4;
    
    // How many final full-size chunks to generate.
    private static final int N_LARGE_CHUNKS = 20;
    
    // How many large chunks to use for estimating the global volume.
    private static final int N_VOLUME_CHUNKS = 4;
    
    // Size of small/large chunks, in samples.
    private static final int SMALL_CHUNK_SIZE = 8192;
    private static final int LARGE_CHUNK_SIZE = 65536;
    
    private int mChunkNumber = 0;
    
    public void reset() {
        mChunkNumber = 0;
    }
    
    public void advance() {
        mChunkNumber++;
    }
    
    public boolean done() {
        return mChunkNumber >= N_SMALL_CHUNKS + N_LARGE_CHUNKS;
    }
    
    public int getStage() {
        if (mChunkNumber < N_SMALL_CHUNKS) {
            // Small chunk.
            switch (mChunkNumber) {
            case 0:
                return S_FIRST_SMALL;
            default:
                return S_OTHER_SMALL;
            }
        } else if (mChunkNumber < N_SMALL_CHUNKS + N_VOLUME_CHUNKS) {
            // Large chunk, with volume computation.
            switch (mChunkNumber) {
            case N_SMALL_CHUNKS:
                return S_FIRST_VOLUME;
            case N_SMALL_CHUNKS + N_VOLUME_CHUNKS - 1:
                return S_LAST_VOLUME;
            default:
                return S_OTHER_VOLUME;
            }
        } else {
            // Large chunk, volume already set.
            return S_LARGE_NOCLIP;
        }
    }
    
    public int getChunkSize() {
        return mChunkNumber < N_SMALL_CHUNKS ? SMALL_CHUNK_SIZE : LARGE_CHUNK_SIZE;
    }
}
