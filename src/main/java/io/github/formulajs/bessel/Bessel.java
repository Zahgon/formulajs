/*
 * bessel.js (C) 2013-present SheetJS -- http://sheetjs.com
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.github.formulajs.bessel;

import io.github.formulajs.js.Js;
import java.util.function.DoubleUnaryOperator;

/**
 * bessel 1.0.2: the Bessel functions J, Y, I and K by polynomial approximations of orders 0 and 1 and recurrences for
 * higher orders, with its handling of non-integer, negative and non-finite arguments (Math.* is fdlibm, as in V8).
 */
public final class Bessel {
  private Bessel() {}

  /** 2 / Math.PI. */
  private static final double W = 0.636619772;

  private static double[] reverse(double... arr) {
    double[] out = new double[arr.length];
    for (int i = 0; i < arr.length; i++) {
      out[i] = arr[arr.length - 1 - i];
    }
    return out;
  }

  private static double horner(double[] arr, double v) {
    double z = 0;
    for (int i = 0; i < arr.length; ++i) {
      z = v * z + arr[i];
    }
    return z;
  }

  private static double besselIter(double x, double n, double f0, double f1, double sign) {
    if (n == 0) {
      return f0;
    }
    if (n == 1) {
      return f1;
    }
    double tdx = 2 / x;
    double f2 = f1;
    for (double o = 1; o < n; ++o) {
      f2 = f1 * o * tdx + sign * f0;
      f0 = f1;
      f1 = f2;
    }
    return f2;
  }

  private static double besselWrap(
      DoubleUnaryOperator bessel0, DoubleUnaryOperator bessel1, int nonzero, double sign, double x, double n) {
    if (nonzero != 0) {
      if (x == 0) {
        return nonzero == 1 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
      } else if (x < 0) {
        return Double.NaN;
      }
    }
    if (n == 0) {
      return bessel0.applyAsDouble(x);
    }
    if (n == 1) {
      return bessel1.applyAsDouble(x);
    }
    if (n < 0) {
      return Double.NaN;
    }
    n = Js.toInt32(n);
    double b0 = bessel0.applyAsDouble(x);
    double b1 = bessel1.applyAsDouble(x);
    return besselIter(x, n, b0, b1, sign);
  }

  /** The truthiness of {@code n % 2}. */
  private static boolean odd(double n) {
    double r = n % 2;
    return r != 0 && !Double.isNaN(r);
  }

  // besselj

  private static final double[] J0_A1A =
      reverse(57568490574.0, -13362590354.0, 651619640.7, -11214424.18, 77392.33017, -184.9052456);
  private static final double[] J0_A2A =
      reverse(57568490411.0, 1029532985.0, 9494680.718, 59272.64853, 267.8532712, 1.0);
  private static final double[] J0_A1B =
      reverse(1.0, -0.1098628627e-2, 0.2734510407e-4, -0.2073370639e-5, 0.2093887211e-6);
  private static final double[] J0_A2B =
      reverse(-0.1562499995e-1, 0.1430488765e-3, -0.6911147651e-5, 0.7621095161e-6, -0.934935152e-7);

  private static double j0(double x) {
    double y = x * x;
    if (x < 8) {
      double a1 = horner(J0_A1A, y);
      double a2 = horner(J0_A2A, y);
      return a1 / a2;
    }
    double xx = x - 0.785398164;
    y = 64 / y;
    double a1 = horner(J0_A1B, y);
    double a2 = horner(J0_A2B, y);
    return Math.sqrt(W / x) * (StrictMath.cos(xx) * a1 - StrictMath.sin(xx) * a2 * 8 / x);
  }

  private static final double[] J1_A1A =
      reverse(72362614232.0, -7895059235.0, 242396853.1, -2972611.439, 15704.48260, -30.16036606);
  private static final double[] J1_A2A =
      reverse(144725228442.0, 2300535178.0, 18583304.74, 99447.43394, 376.9991397, 1.0);
  private static final double[] J1_A1B =
      reverse(1.0, 0.183105e-2, -0.3516396496e-4, 0.2457520174e-5, -0.240337019e-6);
  private static final double[] J1_A2B =
      reverse(0.04687499995, -0.2002690873e-3, 0.8449199096e-5, -0.88228987e-6, 0.105787412e-6);

  private static double j1(double x) {
    double y = x * x;
    double xx = Math.abs(x) - 2.356194491;
    if (Math.abs(x) < 8) {
      double a1 = x * horner(J1_A1A, y);
      double a2 = horner(J1_A2A, y);
      return a1 / a2;
    }
    y = 64 / y;
    double a1 = horner(J1_A1B, y);
    double a2 = horner(J1_A2B, y);
    double a = Math.sqrt(W / Math.abs(x)) * (StrictMath.cos(xx) * a1 - StrictMath.sin(xx) * a2 * 8 / Math.abs(x));
    return x < 0 ? -a : a;
  }

  /** besselj(x, n): the Bessel function of the first kind Jn(x). */
  public static double besselj(double x, double n) {
    n = Js.round(n);
    if (!Double.isFinite(x)) {
      return Double.isNaN(x) ? x : 0;
    }
    if (n < 0) {
      return (odd(n) ? -1 : 1) * besselj(x, -n);
    }
    if (x < 0) {
      return (odd(n) ? -1 : 1) * besselj(-x, n);
    }
    if (n == 0) {
      return j0(x);
    }
    if (n == 1) {
      return j1(x);
    }
    if (x == 0) {
      return 0;
    }
    if (x > n) {
      return besselIter(x, n, j0(x), j1(x), -1);
    }
    double ret = 0.0;
    double m = 2 * Math.floor((n + Math.floor(Math.sqrt(40 * n))) / 2);
    boolean jsum = false;
    double bjp = 0.0;
    double sum = 0.0;
    double bj = 1.0;
    double tox = 2 / x;
    for (double j = m; j > 0; j--) {
      double bjm = j * tox * bj - bjp;
      bjp = bj;
      bj = bjm;
      if (Math.abs(bj) > 1E10) {
        bj *= 1E-10;
        bjp *= 1E-10;
        ret *= 1E-10;
        sum *= 1E-10;
      }
      if (jsum) {
        sum += bj;
      }
      jsum = !jsum;
      if (j == n) {
        ret = bjp;
      }
    }
    sum = 2.0 * sum - bj;
    return ret / sum;
  }

  // bessely

  private static final double[] Y0_A1A =
      reverse(-2957821389.0, 7062834065.0, -512359803.6, 10879881.29, -86327.92757, 228.4622733);
  private static final double[] Y0_A2A =
      reverse(40076544269.0, 745249964.8, 7189466.438, 47447.26470, 226.1030244, 1.0);
  private static final double[] Y0_A1B =
      reverse(1.0, -0.1098628627e-2, 0.2734510407e-4, -0.2073370639e-5, 0.2093887211e-6);
  private static final double[] Y0_A2B =
      reverse(-0.1562499995e-1, 0.1430488765e-3, -0.6911147651e-5, 0.7621095161e-6, -0.934945152e-7);

  private static double y0(double x) {
    double y = x * x;
    double xx = x - 0.785398164;
    if (x < 8) {
      double a1 = horner(Y0_A1A, y);
      double a2 = horner(Y0_A2A, y);
      return a1 / a2 + W * besselj(x, 0) * StrictMath.log(x);
    }
    y = 64 / y;
    double a1 = horner(Y0_A1B, y);
    double a2 = horner(Y0_A2B, y);
    return Math.sqrt(W / x) * (StrictMath.sin(xx) * a1 + StrictMath.cos(xx) * a2 * 8 / x);
  }

  private static final double[] Y1_A1A = reverse(
      -0.4900604943e13, 0.1275274390e13, -0.5153438139e11, 0.7349264551e9, -0.4237922726e7, 0.8511937935e4);
  private static final double[] Y1_A2A = reverse(
      0.2499580570e14, 0.4244419664e12, 0.3733650367e10, 0.2245904002e8, 0.1020426050e6, 0.3549632885e3, 1);
  private static final double[] Y1_A1B =
      reverse(1.0, 0.183105e-2, -0.3516396496e-4, 0.2457520174e-5, -0.240337019e-6);
  private static final double[] Y1_A2B =
      reverse(0.04687499995, -0.2002690873e-3, 0.8449199096e-5, -0.88228987e-6, 0.105787412e-6);

  private static double y1(double x) {
    double y = x * x;
    double xx = x - 2.356194491;
    if (x < 8) {
      double a1 = x * horner(Y1_A1A, y);
      double a2 = horner(Y1_A2A, y);
      return a1 / a2 + W * (besselj(x, 1) * StrictMath.log(x) - 1 / x);
    }
    y = 64 / y;
    double a1 = horner(Y1_A1B, y);
    double a2 = horner(Y1_A2B, y);
    return Math.sqrt(W / x) * (StrictMath.sin(xx) * a1 + StrictMath.cos(xx) * a2 * 8 / x);
  }

  /** bessely(x, n): the Bessel function of the second kind Yn(x). */
  public static double bessely(double x, double n) {
    return besselWrap(Bessel::y0, Bessel::y1, 1, -1, x, n);
  }

  // besseli

  private static final double[] I0_A =
      reverse(1.0, 3.5156229, 3.0899424, 1.2067492, 0.2659732, 0.360768e-1, 0.45813e-2);
  private static final double[] I0_B = reverse(0.39894228, 0.1328592e-1, 0.225319e-2, -0.157565e-2, 0.916281e-2,
      -0.2057706e-1, 0.2635537e-1, -0.1647633e-1, 0.392377e-2);

  private static double i0(double x) {
    if (x <= 3.75) {
      return horner(I0_A, x * x / (3.75 * 3.75));
    }
    return StrictMath.exp(Math.abs(x)) / Math.sqrt(Math.abs(x)) * horner(I0_B, 3.75 / Math.abs(x));
  }

  private static final double[] I1_A =
      reverse(0.5, 0.87890594, 0.51498869, 0.15084934, 0.2658733e-1, 0.301532e-2, 0.32411e-3);
  private static final double[] I1_B = reverse(0.39894228, -0.3988024e-1, -0.362018e-2, 0.163801e-2, -0.1031555e-1,
      0.2282967e-1, -0.2895312e-1, 0.1787654e-1, -0.420059e-2);

  private static double i1(double x) {
    if (x < 3.75) {
      return x * horner(I1_A, x * x / (3.75 * 3.75));
    }
    return (x < 0 ? -1 : 1) * StrictMath.exp(Math.abs(x)) / Math.sqrt(Math.abs(x)) * horner(I1_B, 3.75 / Math.abs(x));
  }

  /** besseli(x, n): the modified Bessel function of the first kind In(x). */
  public static double besseli(double x, double n) {
    n = Js.round(n);
    if (n == 0) {
      return i0(x);
    }
    if (n == 1) {
      return i1(x);
    }
    if (n < 0) {
      return Double.NaN;
    }
    if (Math.abs(x) == 0) {
      return 0;
    }
    if (x == Double.POSITIVE_INFINITY) {
      return Double.POSITIVE_INFINITY;
    }
    double ret = 0.0;
    double tox = 2 / Math.abs(x);
    double bip = 0.0;
    double bi = 1.0;
    double m = 2 * Js.round((n + Js.round(Math.sqrt(40 * n))) / 2);
    for (double j = m; j > 0; j--) {
      double bim = j * tox * bi + bip;
      bip = bi;
      bi = bim;
      if (Math.abs(bi) > 1E10) {
        bi *= 1E-10;
        bip *= 1E-10;
        ret *= 1E-10;
      }
      if (j == n) {
        ret = bip;
      }
    }
    ret *= besseli(x, 0) / bi;
    return x < 0 && odd(n) ? -ret : ret;
  }

  // besselk

  private static final double[] K0_A =
      reverse(-0.57721566, 0.42278420, 0.23069756, 0.3488590e-1, 0.262698e-2, 0.10750e-3, 0.74e-5);
  private static final double[] K0_B =
      reverse(1.25331414, -0.7832358e-1, 0.2189568e-1, -0.1062446e-1, 0.587872e-2, -0.251540e-2, 0.53208e-3);

  private static double k0(double x) {
    if (x <= 2) {
      return -StrictMath.log(x / 2) * besseli(x, 0) + horner(K0_A, x * x / 4);
    }
    return StrictMath.exp(-x) / Math.sqrt(x) * horner(K0_B, 2 / x);
  }

  private static final double[] K1_A =
      reverse(1.0, 0.15443144, -0.67278579, -0.18156897, -0.1919402e-1, -0.110404e-2, -0.4686e-4);
  private static final double[] K1_B =
      reverse(1.25331414, 0.23498619, -0.3655620e-1, 0.1504268e-1, -0.780353e-2, 0.325614e-2, -0.68245e-3);

  private static double k1(double x) {
    if (x <= 2) {
      return StrictMath.log(x / 2) * besseli(x, 1) + (1 / x) * horner(K1_A, x * x / 4);
    }
    return StrictMath.exp(-x) / Math.sqrt(x) * horner(K1_B, 2 / x);
  }

  /** besselk(x, n): the modified Bessel function of the second kind Kn(x). */
  public static double besselk(double x, double n) {
    return besselWrap(Bessel::k0, Bessel::k1, 2, 1, x, n);
  }
}
