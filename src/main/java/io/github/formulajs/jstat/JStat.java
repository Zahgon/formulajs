/*
 * Copyright (c) 2013 jStat; ported in formulajs/formulajs
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.formulajs.jstat;

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsMath;
import java.util.ArrayList;
import java.util.List;

/**
 * The parts of jStat 1.9.6 the library uses, with jStat's arithmetic and its handling of JavaScript values. Functions
 * that can answer {@code false} (jStat.ibeta outside [0, 1], jStat.negbin.pdf off the integers) return Object.
 */
public final class JStat {
  private JStat() {}

  /** jStat.rows: {@code arr.length || 1}. */
  public static Object rows(Object arr) {
    Object length = Js.length(arr);
    return Js.truthy(length) ? length : (Object) 1.0;
  }

  /** jStat.cols: {@code arr[0].length || 1}. */
  public static Object cols(Object arr) {
    Object length = Js.length(Js.get(arr, 0.0));
    return Js.truthy(length) ? length : (Object) 1.0;
  }

  /** jStat.row: {@code arr[index]}, or one row per index of an array of indexes. */
  public static Object row(Object arr, Object index) {
    if (index instanceof List<?> indexes) {
      List<Object> out = new ArrayList<>();
      for (Object i : indexes) {
        out.add(row(arr, i));
      }
      return out;
    }
    return Js.get(arr, index);
  }

  /** jStat.col for a single index: {@code [[arr[0][index]], [arr[1][index]], ...]}. */
  public static Object col(Object arr, Object index) {
    List<Object> column = new ArrayList<>();
    double length = Js.toNumber(Js.length(arr));
    for (int i = 0; i < length; i++) {
      column.add(Js.list(Js.get(Js.get(arr, (double) i), index)));
    }
    return column;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Vectors

  /** jStat.sum: {@code sum += arr[i]} from the last element to the first. */
  public static Object sum(List<?> arr) {
    Object sum = 0.0;
    for (int i = arr.size() - 1; i >= 0; i--) {
      Object v = arr.get(i);
      sum = sum instanceof Double s && v instanceof Double d ? (Object) (s + d) : Js.add(sum, v);
    }
    return sum;
  }

  public static double mean(List<?> arr) {
    return Js.toNumber(sum(arr)) / arr.size();
  }

  /** jStat.variance (flag: sample instead of population), from the sum of squared errors summed from the end. */
  public static double variance(List<?> arr, boolean flag) {
    double mean = mean(arr);
    double sum = 0;
    for (int i = arr.size() - 1; i >= 0; i--) {
      double tmp = Js.toNumber(arr.get(i)) - mean;
      sum += tmp * tmp;
    }
    return sum / (arr.size() - (flag ? 1 : 0));
  }

  public static double stdev(List<?> arr, boolean flag) {
    return Math.sqrt(variance(arr, flag));
  }

  public static double geomean(List<?> arr) {
    List<Object> logs = new ArrayList<>();
    for (Object v : arr) {
      logs.add(StrictMath.log(Js.toNumber(v)));
    }
    return JsMath.exp(mean(logs));
  }

  /** jStat.median: the middle element of a sorted copy, or the mean of the middle two (by {@code +}). */
  public static Object median(List<Object> arr) {
    int arrlen = arr.size();
    List<Object> sorted = Js.sort(new ArrayList<>(arr), (a, b) -> Js.toNumber(a) - Js.toNumber(b));
    if ((arrlen & 1) == 0) {
      return Js.toNumber(Js.add(Js.get(sorted, arrlen / 2.0 - 1), Js.get(sorted, arrlen / 2.0))) / 2;
    }
    return sorted.get(arrlen / 2);
  }

  public static double covariance(List<?> arr1, List<?> arr2) {
    double u = mean(arr1);
    double v = mean(arr2);
    List<Object> sqDev = new ArrayList<>();
    for (int i = 0; i < arr1.size(); i++) {
      sqDev.add((Js.toNumber(arr1.get(i)) - u) * (Js.toNumber(Js.get(arr2, (double) i)) - v));
    }
    return Js.toNumber(sum(sqDev)) / (arr1.size() - 1);
  }

  public static double corrcoeff(List<?> arr1, List<?> arr2) {
    return covariance(arr1, arr2) / stdev(arr1, true) / stdev(arr2, true);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Special functions

  private static final double[] GAMMALN_COF = {
    76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2,
    -0.5395239384953e-5
  };
  private static final double[] GAMMA_P = {
    -1.716185138865495, 24.76565080557592, -379.80425647094563, 629.3311553128184, 866.9662027904133,
    -31451.272968848367, -36144.413418691176, 66456.14382024054
  };
  private static final double[] GAMMA_Q = {
    -30.8402300119739, 315.35062697960416, -1015.1563674902192, -3107.771671572311, 22538.118420980151,
    4755.8462775278811, -134659.9598649693, -115132.2596755535
  };
  private static final double[] ERF_COF = {
    -1.3026537197817094, 6.4196979235649026e-1, 1.9476473204185836e-2, -9.561514786808631e-3,
    -9.46595344482036e-4, 3.66839497852761e-4, 4.2523324806907e-5, -2.0278578112534e-5, -1.624290004647e-6,
    1.303655835580e-6, 1.5626441722e-8, -8.5238095915e-8, 6.529054439e-9, 5.059343495e-9, -9.91364156e-10,
    -2.27365122e-10, 9.6467911e-11, 2.394038e-12, -6.886027e-12, 8.94487e-13, 3.13092e-13, -1.12708e-13, 3.81e-16,
    7.106e-15, -1.523e-15, -9.4e-17, 1.21e-16, -2.8e-17
  };

  public static double gammaln(double x) {
    double ser = 1.000000000190015;
    double xx = x;
    double y = x;
    double tmp = x + 5.5;
    tmp -= (xx + 0.5) * StrictMath.log(tmp);
    for (int j = 0; j < 6; j++) {
      ser += GAMMALN_COF[j] / ++y;
    }
    return StrictMath.log(2.5066282746310005 * ser / xx) - tmp;
  }

  public static double gammafn(double x) {
    double fact = 0;
    double n = 0;
    double xden = 0;
    double xnum = 0;
    double y = x;
    double z;
    if (x > 171.6243769536076) {
      return Double.POSITIVE_INFINITY;
    }
    if (y <= 0) {
      double res = y % 1 + 3.6e-16;
      if (res == 0 || Double.isNaN(res)) {
        return Double.POSITIVE_INFINITY;
      }
      fact = ((Js.toInt32(y) & 1) == 0 ? 1 : -1) * Math.PI / StrictMath.sin(Math.PI * res);
      y = 1 - y;
    }
    double yi = y;
    if (y < 1) {
      z = y++;
    } else {
      n = (double) Js.toInt32(y) - 1;
      y -= n;
      z = y - 1;
    }
    for (int i = 0; i < 8; ++i) {
      xnum = (xnum + GAMMA_P[i]) * z;
      xden = xden * z + GAMMA_Q[i];
    }
    double res = xnum / xden + 1;
    if (yi < y) {
      res /= yi;
    } else if (yi > y) {
      for (double i = 0; i < n; ++i) {
        res *= y;
        y++;
      }
    }
    return fact != 0 && !Double.isNaN(fact) ? fact / res : res;
  }

  /** The lower regularized incomplete gamma function P(a, x). */
  public static double lowRegGamma(double a, double x) {
    double aln = gammaln(a);
    double ap = a;
    double sum = 1 / a;
    double del = sum;
    double b = x + 1 - a;
    double c = 1 / 1.0e-30;
    double d = 1 / b;
    double h = d;
    double itmax = (double) Js.toInt32(StrictMath.log(a >= 1 ? a : 1 / a) * 8.5 + a * 0.4 + 17) + 1;
    if (x < 0 || a <= 0) {
      return Double.NaN;
    } else if (x < a + 1) {
      for (double i = 1; i <= itmax; i++) {
        del *= x / ++ap;
        sum += del;
      }
      return sum * JsMath.exp(-x + a * StrictMath.log(x) - aln);
    }
    for (double i = 1; i <= itmax; i++) {
      double an = -i * (i - a);
      b += 2;
      d = an * d + b;
      c = b + an / c;
      d = 1 / d;
      h *= d * c;
    }
    return 1 - h * JsMath.exp(-x + a * StrictMath.log(x) - aln);
  }

  private static double factorialln(double n) {
    return n < 0 ? Double.NaN : gammaln(n + 1);
  }

  public static double factorial(double n) {
    return n < 0 ? Double.NaN : gammafn(n + 1);
  }

  public static double combination(double n, double m) {
    return n > 170 || m > 170
        ? JsMath.exp(factorialln(n) - factorialln(m) - factorialln(n - m))
        : (factorial(n) / factorial(m)) / factorial(n - m);
  }

  /** jStat.betafn; its undefined for non-positive arguments only ever reaches a division, so it is NaN here. */
  private static double betafn(double x, double y) {
    if (x <= 0 || y <= 0) {
      return Double.NaN;
    }
    return x + y > 170 ? JsMath.exp(betaln(x, y)) : gammafn(x) * gammafn(y) / gammafn(x + y);
  }

  private static double betaln(double x, double y) {
    return gammaln(x) + gammaln(y) - gammaln(x + y);
  }

  /** jStat.betacf: the continued fraction of the incomplete beta function (modified Lentz). */
  private static double betacf(double x, double a, double b) {
    double fpmin = 1e-30;
    double qab = a + b;
    double qap = a + 1;
    double qam = a - 1;
    double c = 1;
    double d = 1 - qab * x / qap;
    d = 1 / (Math.abs(d) < fpmin ? fpmin : d);
    double h = d;
    for (double m = 1; m <= 100; m++) {
      double m2 = 2 * m;
      double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
      d = 1 + aa * d;
      d = Math.abs(d) < fpmin ? fpmin : d;
      c = 1 + aa / c;
      c = Math.abs(c) < fpmin ? fpmin : c;
      d = 1 / d;
      h *= d * c;
      aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
      d = 1 + aa * d;
      d = Math.abs(d) < fpmin ? fpmin : d;
      c = 1 + aa / c;
      c = Math.abs(c) < fpmin ? fpmin : c;
      d = 1 / d;
      double del = d * c;
      h *= del;
      if (Math.abs(del - 1.0) < 3e-7) {
        break;
      }
    }
    return h;
  }

  /** The inverse of the lower regularized incomplete gamma function. */
  private static double gammapinv(double p, double a) {
    double a1 = a - 1;
    double gln = gammaln(a);
    double x;
    double t;
    double lna1 = 0;
    double afac = 0;
    if (p >= 1) {
      return Math.max(100, a + 100 * Math.sqrt(a));
    }
    if (p <= 0) {
      return 0;
    }
    if (a > 1) {
      lna1 = StrictMath.log(a1);
      afac = JsMath.exp(a1 * (lna1 - 1) - gln);
      t = Math.sqrt(-2 * StrictMath.log(p < 0.5 ? p : 1 - p));
      x = (2.30753 + t * 0.27061) / (1 + t * (0.99229 + t * 0.04481)) - t;
      x = Math.max(1e-3, a * Js.pow(1 - 1 / (9 * a) - (p < 0.5 ? -x : x) / (3 * Math.sqrt(a)), 3));
    } else {
      t = 1 - a * (0.253 + a * 0.12);
      x = p < t ? Js.pow(p / t, 1 / a) : 1 - StrictMath.log(1 - (p - t) / (1 - t));
    }
    for (int j = 0; j < 12; j++) {
      if (x <= 0) {
        return 0;
      }
      double err = lowRegGamma(a, x) - p;
      t = a > 1
          ? afac * JsMath.exp(-(x - a1) + a1 * (StrictMath.log(x) - lna1))
          : JsMath.exp(-x + a1 * StrictMath.log(x) - gln);
      double u = err / t;
      t = u / (1 - 0.5 * Math.min(1, u * ((a - 1) / x - 1)));
      x -= t;
      if (x <= 0) {
        x = 0.5 * (x + t);
      }
      if (Math.abs(t) < 1e-8 * x) {
        break;
      }
    }
    return x;
  }

  /** jStat.erf, called with whatever value the caller passes ({@code 2 + x} concatenates a string). */
  public static double erf(Object x) {
    boolean isneg = false;
    if (Js.lt(x, 0.0)) {
      x = -Js.toNumber(x);
      isneg = true;
    }
    double t = 2 / Js.toNumber(Js.add(2.0, x));
    double ty = 4 * t - 2;
    double d = 0;
    double dd = 0;
    for (int j = ERF_COF.length - 1; j > 0; j--) {
      double tmp = d;
      d = ty * d - dd + ERF_COF[j];
      dd = tmp;
    }
    double xn = Js.toNumber(x);
    double res = t * JsMath.exp(-xn * xn + 0.5 * (ERF_COF[0] + ty * d) - dd);
    return isneg ? res - 1 : 1 - res;
  }

  /** jStat.erfc: {@code 1 - jStat.erf(x)}. */
  public static double erfc(Object x) {
    return 1 - erf(x);
  }

  private static double erfcinv(double p) {
    if (p >= 2) {
      return -100;
    }
    if (p <= 0) {
      return 100;
    }
    double pp = p < 1 ? p : 2 - p;
    double t = Math.sqrt(-2 * StrictMath.log(pp / 2));
    double x = -0.70711 * ((2.30753 + t * 0.27061) / (1 + t * (0.99229 + t * 0.04481)) - t);
    for (int j = 0; j < 2; j++) {
      double err = erfc(x) - pp;
      x += err / (1.12837916709551257 * JsMath.exp(-x * x) - x * err);
    }
    return p < 1 ? x : -x;
  }

  /** The inverse of the regularized incomplete beta function. */
  private static double ibetainv(double p, double a, double b) {
    double a1 = a - 1;
    double b1 = b - 1;
    double t;
    double u;
    double x;
    if (p <= 0) {
      return 0;
    }
    if (p >= 1) {
      return 1;
    }
    if (a >= 1 && b >= 1) {
      t = Math.sqrt(-2 * StrictMath.log(p < 0.5 ? p : 1 - p));
      x = (2.30753 + t * 0.27061) / (1 + t * (0.99229 + t * 0.04481)) - t;
      if (p < 0.5) {
        x = -x;
      }
      double al = (x * x - 3) / 6;
      double h = 2 / (1 / (2 * a - 1) + 1 / (2 * b - 1));
      double w = (x * Math.sqrt(al + h) / h) - (1 / (2 * b - 1) - 1 / (2 * a - 1)) * (al + 5.0 / 6 - 2 / (3 * h));
      x = a / (a + b * JsMath.exp(2 * w));
    } else {
      double lna = StrictMath.log(a / (a + b));
      double lnb = StrictMath.log(b / (a + b));
      t = JsMath.exp(a * lna) / a;
      u = JsMath.exp(b * lnb) / b;
      double w = t + u;
      x = p < t / w ? Js.pow(a * w * p, 1 / a) : 1 - Js.pow(b * w * (1 - p), 1 / b);
    }
    double afac = -gammaln(a) - gammaln(b) + gammaln(a + b);
    for (int j = 0; j < 10; j++) {
      if (x == 0 || x == 1) {
        return x;
      }
      double err = Js.toNumber(ibeta(x, a, b)) - p;
      t = JsMath.exp(a1 * StrictMath.log(x) + b1 * StrictMath.log(1 - x) + afac);
      u = err / t;
      t = u / (1 - 0.5 * Math.min(1, u * (a1 / x - b1 / (1 - x))));
      x -= t;
      if (x <= 0) {
        x = 0.5 * (x + t);
      }
      if (x >= 1) {
        x = 0.5 * (x + t + 1);
      }
      if (Math.abs(t) < 1e-8 * x && j > 0) {
        break;
      }
    }
    return x;
  }

  /** The regularized incomplete beta function I_x(a, b); {@code false} when x is outside [0, 1]. */
  private static Object ibeta(double x, double a, double b) {
    double bt = x == 0 || x == 1
        ? 0
        : JsMath.exp(gammaln(a + b) - gammaln(a) - gammaln(b) + a * StrictMath.log(x)
            + b * StrictMath.log(1 - x));
    if (x < 0 || x > 1) {
      return Boolean.FALSE;
    }
    if (x < (a + 1) / (a + b + 2)) {
      return bt * betacf(x, a, b) / a;
    }
    return 1 - bt * betacf(1 - x, b, a) / b;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Distributions

  public static double betaPdf(double x, double alpha, double beta) {
    if (x > 1 || x < 0) {
      return 0;
    }
    if (alpha == 1 && beta == 1) {
      return 1;
    }
    if (alpha < 512 && beta < 512) {
      return (Js.pow(x, alpha - 1) * Js.pow(1 - x, beta - 1)) / betafn(alpha, beta);
    }
    return JsMath.exp((alpha - 1) * StrictMath.log(x) + (beta - 1) * StrictMath.log(1 - x)
        - betaln(alpha, beta));
  }

  public static Object betaCdf(double x, double alpha, double beta) {
    return x > 1 || x < 0 ? (Object) (x > 1 ? 1.0 : 0.0) : ibeta(x, alpha, beta);
  }

  public static double betaInv(double x, double alpha, double beta) {
    return ibetainv(x, alpha, beta);
  }

  public static double centralFPdf(double x, double df1, double df2) {
    if (x < 0) {
      return 0;
    }
    if (df1 <= 2) {
      if (x == 0 && df1 < 2) {
        return Double.POSITIVE_INFINITY;
      }
      if (x == 0 && df1 == 2) {
        return 1;
      }
      return (1 / betafn(df1 / 2, df2 / 2)) * Js.pow(df1 / df2, df1 / 2) * Js.pow(x, (df1 / 2) - 1)
          * Js.pow((1 + (df1 / df2) * x), -(df1 + df2) / 2);
    }
    double p = (df1 * x) / (df2 + x * df1);
    double q = df2 / (df2 + x * df1);
    double f = df1 * q / 2.0;
    return f * binomialPdf((df1 - 2) / 2, (df1 + df2 - 2) / 2, p);
  }

  public static Object centralFCdf(double x, double df1, double df2) {
    return x < 0 ? (Object) 0.0 : ibeta((df1 * x) / (df1 * x + df2), df1 / 2, df2 / 2);
  }

  public static double centralFInv(double x, double df1, double df2) {
    return df2 / (df1 * (1 / ibetainv(x, df1 / 2, df2 / 2) - 1));
  }

  public static double chisquarePdf(double x, double dof) {
    if (x < 0) {
      return 0;
    }
    return x == 0 && dof == 2
        ? 0.5
        : JsMath.exp((dof / 2 - 1) * StrictMath.log(x) - x / 2 - (dof / 2) * StrictMath.log(2)
            - gammaln(dof / 2));
  }

  public static double chisquareCdf(double x, double dof) {
    return x < 0 ? 0 : lowRegGamma(dof / 2, x / 2);
  }

  public static double chisquareInv(double p, double dof) {
    return 2 * gammapinv(p, 0.5 * dof);
  }

  public static double exponentialPdf(double x, double rate) {
    return x < 0 ? 0 : rate * JsMath.exp(-rate * x);
  }

  public static double exponentialCdf(double x, double rate) {
    return x < 0 ? 0 : 1 - JsMath.exp(-rate * x);
  }

  public static double gammaPdf(double x, double shape, double scale) {
    if (x < 0) {
      return 0;
    }
    return x == 0 && shape == 1
        ? 1 / scale
        : JsMath.exp((shape - 1) * StrictMath.log(x) - x / scale - gammaln(shape)
            - shape * StrictMath.log(scale));
  }

  public static double gammaCdf(double x, double shape, double scale) {
    return x < 0 ? 0 : lowRegGamma(shape, x / scale);
  }

  public static double gammaInv(double p, double shape, double scale) {
    return gammapinv(p, shape) * scale;
  }

  public static double lognormalPdf(double x, double mu, double sigma) {
    if (x <= 0) {
      return 0;
    }
    return JsMath.exp(-StrictMath.log(x) - 0.5 * StrictMath.log(2 * Math.PI) - StrictMath.log(sigma)
        - Js.pow(StrictMath.log(x) - mu, 2) / (2 * sigma * sigma));
  }

  public static double lognormalCdf(double x, double mu, double sigma) {
    return x < 0 ? 0 : 0.5 + (0.5 * erf((StrictMath.log(x) - mu) / Math.sqrt(2 * sigma * sigma)));
  }

  public static double lognormalInv(double p, double mu, double sigma) {
    return JsMath.exp(-1.41421356237309505 * sigma * erfcinv(2 * p) + mu);
  }

  public static double normalPdf(double x, double mean, double std) {
    return JsMath.exp(-0.5 * StrictMath.log(2 * Math.PI) - StrictMath.log(std)
        - Js.pow(x - mean, 2) / (2 * std * std));
  }

  public static double normalCdf(double x, double mean, double std) {
    return 0.5 * (1 + erf((x - mean) / Math.sqrt(2 * std * std)));
  }

  public static double normalInv(double p, double mean, double std) {
    return -1.41421356237309505 * std * erfcinv(2 * p) + mean;
  }

  public static Object studenttCdf(double x, double dof) {
    double dof2 = dof / 2;
    return ibeta((x + Math.sqrt(x * x + dof)) / (2 * Math.sqrt(x * x + dof)), dof2, dof2);
  }

  public static double studenttInv(double p, double dof) {
    double x = ibetainv(2 * Math.min(p, 1 - p), 0.5 * dof, 0.5);
    x = Math.sqrt(dof * (1 - x) / x);
    return p > 0.5 ? x : -x;
  }

  /** The continued fraction jStat.binomial.cdf uses (from www.math.ucla.edu/~tom/distributions/binomial.html). */
  private static double betinc(double x, double a, double b, double eps) {
    double a0 = 0;
    double b0 = 1;
    double a1 = 1;
    double b1 = 1;
    double m9 = 0;
    double a2 = 0;
    while (Math.abs((a1 - a2) / a1) > eps) {
      a2 = a1;
      double c9 = -(a + m9) * (a + b + m9) * x / (a + 2 * m9) / (a + 2 * m9 + 1);
      a0 = a1 + c9 * a0;
      b0 = b1 + c9 * b0;
      m9 = m9 + 1;
      c9 = m9 * (b - m9) * x / (a + 2 * m9 - 1) / (a + 2 * m9);
      a1 = a0 + c9 * a1;
      b1 = b0 + c9 * b1;
      a0 = a0 / b1;
      b0 = b0 / b1;
      a1 = a1 / b1;
      b1 = 1;
    }
    return a1 / a;
  }

  public static double binomialPdf(double k, double n, double p) {
    return p == 0 || p == 1 ? (n * p == k ? 1 : 0) : combination(n, k) * Js.pow(p, k) * Js.pow(1 - p, n - k);
  }

  public static double binomialCdf(double x, double n, double p) {
    double eps = 1e-10;
    if (x < 0) {
      return 0;
    }
    if (x >= n) {
      return 1;
    }
    if (p < 0 || p > 1 || n <= 0) {
      return Double.NaN;
    }
    x = Math.floor(x);
    double a = x + 1;
    double b = n - x;
    double s = a + b;
    double bt = JsMath.exp(gammaln(s) - gammaln(b) - gammaln(a) + a * StrictMath.log(p)
        + b * StrictMath.log(1 - p));
    double betacdf = p < (a + 1) / (s + 2) ? bt * betinc(p, a, b, eps) : 1 - bt * betinc(1 - p, b, a, eps);
    return Js.round((1 - betacdf) * (1 / eps)) / (1 / eps);
  }

  public static Object negbinPdf(double k, double r, double p) {
    if (k != (double) Js.toUint32(k)) {
      return Boolean.FALSE;
    }
    return k < 0 ? (Object) 0.0 : combination(k + r - 1, r - 1) * Js.pow(1 - p, k) * Js.pow(p, r);
  }

  public static double negbinCdf(double x, double r, double p) {
    double sum = 0;
    for (double k = 0; k <= x; k++) {
      sum += Js.toNumber(negbinPdf(k, r, p));
    }
    return x < 0 ? 0 : sum;
  }

  public static double poissonPdf(double k, double l) {
    return l < 0 || (k % 1) != 0 || k < 0 ? 0 : Js.pow(l, k) * JsMath.exp(-l) / factorial(k);
  }

  public static double poissonCdf(double x, double l) {
    List<Object> sumarr = new ArrayList<>();
    for (double k = 0; k <= x; k++) {
      sumarr.add(poissonPdf(k, l));
    }
    return x < 0 ? 0 : Js.toNumber(sum(sumarr));
  }

  /** jStat.normalci(value, alpha, sd, n): [value - change, value + change]. */
  public static double[] normalci(double value, double alpha, double sd, double n) {
    double change = Math.abs(normalInv(alpha / 2, 0, 1) * sd / Math.sqrt(n));
    return new double[] {value - change, value + change};
  }

  /** jStat.tci(value, alpha, sd, n): [value - change, value + change]. */
  public static double[] tci(double value, double alpha, double sd, double n) {
    double change = Math.abs(studenttInv(alpha / 2, n - 1) * sd / Math.sqrt(n));
    return new double[] {value - change, value + change};
  }
}
