/* ***** BEGIN LICENSE BLOCK *****
 * JTransforms
 * Copyright (c) 2007 onward, Piotr Wendykier
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK ***** */
package org.jtransforms.dct;

import org.jtransforms.utils.CommonUtils;
import static java.lang.Math.ceil;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

// For Chroma Doze, all code unrelated to the IDCT operation has been deleted.

/**
 * Computes 1D Discrete Cosine Transform (DCT) of single precision data. The
 * size of data can be an arbitrary number. This is a parallel implementation of
 * split-radix and mixed-radix algorithms optimized for SMP systems. <br>
 * <br>
 * Part of the code is derived from General Purpose FFT Package written by Takuya Ooura
 * (http://www.kurims.kyoto-u.ac.jp/~ooura/fft.html)
 *  
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 */
public class FloatDCT_1D
{

    private int n;

    private int[] ip;

    private float[] w;

    private int nw;

    private int nc;

    private boolean isPowerOfTwo = false;

    /**
     * Creates new instance of FloatDCT_1D.
     *  
     * @param n size of data
     *
     */
    public FloatDCT_1D(long n)
    {
        if (n < 1) {
            throw new IllegalArgumentException("n must be greater than 0");
        }

        this.n = (int) n;
        if (true) {
            if (n > (1 << 28)) {
                throw new IllegalArgumentException("n must be smaller or equal to " + (1 << 28) + " when useLargeArrays argument is set to false");
            }
            if (CommonUtils.isPowerOf2(n)) {
                this.isPowerOfTwo = true;
                this.ip = new int[(int) ceil(2 + (1 << (int) (log(n / 2 + 0.5) / log(2)) / 2))];
                this.w = new float[this.n * 5 / 4];
                nw = ip[0];
                if (n > (nw << 2)) {
                    nw = this.n >> 2;
                    CommonUtils.makewt(nw, ip, w);
                }
                nc = ip[1];
                if (n > nc) {
                    nc = this.n;
                    CommonUtils.makect(nc, w, nw, ip);
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Computes 1D inverse DCT (DCT-III) leaving the result in <code>a</code>.
     *  
     * @param a
     *              data to transform
     * @param scale
     *              if true then scaling is performed
     */
    public void inverse(float[] a, boolean scale)
    {
        inverse(a, 0, scale);
    }

    /**
     * Computes 1D inverse DCT (DCT-III) leaving the result in <code>a</code>.
     *  
     * @param a
     *              data to transform
     * @param offa
     *              index of the first element in array <code>a</code>
     * @param scale
     *              if true then scaling is performed
     */
    public void inverse(final float[] a, final int offa, boolean scale)
    {
        if (n == 1)
            return;
        if (isPowerOfTwo) {
            float xr;
            if (scale) {
                CommonUtils.scale(n, (float) sqrt(2.0 / n), a, offa, false);
                a[offa] = a[offa] / (float) sqrt(2.0);
            }
            CommonUtils.dctsub(n, a, offa, nc, w, nw);
            if (n > 4) {
                CommonUtils.cftfsub(n, a, offa, ip, nw, w);
                rftfsub(n, a, offa, nc, w, nw);
            } else if (n == 4) {
                CommonUtils.cftfsub(n, a, offa, ip, nw, w);
            }
            xr = a[offa] - a[offa + 1];
            a[offa] += a[offa + 1];
            for (int j = 2; j < n; j += 2) {
                a[offa + j - 1] = a[offa + j] - a[offa + j + 1];
                a[offa + j] += a[offa + j + 1];
            }
            a[offa + n - 1] = xr;
        } else {
            throw new IllegalStateException();
        }
    }

    private static void rftfsub(int n, float[] a, int offa, int nc, float[] c, int startc)
    {
        int k, kk, ks, m;
        float wkr, wki, xr, xi, yr, yi;
        int idx1, idx2;
        m = n >> 1;
        ks = 2 * nc / m;
        kk = 0;
        for (int j = 2; j < m; j += 2) {
            k = n - j;
            kk += ks;
            wkr = 0.5f - c[startc + nc - kk];
            wki = c[startc + kk];
            idx1 = offa + j;
            idx2 = offa + k;
            xr = a[idx1] - a[idx2];
            xi = a[idx1 + 1] + a[idx2 + 1];
            yr = wkr * xr - wki * xi;
            yi = wkr * xi + wki * xr;
            a[idx1] -= yr;
            a[idx1 + 1] -= yi;
            a[idx2] += yr;
            a[idx2 + 1] -= yi;
        }
    }
}
