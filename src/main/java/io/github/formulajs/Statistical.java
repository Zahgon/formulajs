/*
 * Copyright (c) 2014 Sutoiku, Inc. (github.com/formulajs/formulajs)
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
package io.github.formulajs;

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import io.github.formulajs.js.JsMath;
import io.github.formulajs.jstat.JStat;
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.CriteriaEval;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Statistical functions. */
public final class Statistical {
  private static final double SQRT2PI = 2.5066282746310002;
  private static final BiFunction<Object, Object, Object> ASC = (a, b) -> Js.toNumber(a) - Js.toNumber(b);
  private static final BiFunction<Object, Object, Object> DESC = (a, b) -> Js.toNumber(b) - Js.toNumber(a);

  private Statistical() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers

  private static double num(Object v) {
    return Js.toNumber(v);
  }

  /** {@code Number(arr[i])}: an element as a number (NaN past the end). */
  private static double at(Object arr, double i) {
    return Js.toNumber(Js.get(arr, i));
  }

  private static List<Object> list(Object v) {
    return Js.asList(v);
  }

  /** {@code utils.flatten(arguments)}. */
  private static List<Object> flat(Object[] args) {
    return Common.flatten(Common.arguments(args));
  }

  /** {@code utils.parseNumberArray(utils.flatten(value))}. */
  private static Object parsed(Object value) {
    return Common.parseNumberArray(Common.flatten(value));
  }

  /** The first n named parameters (undefined when missing). */
  private static Object[] params(Object[] args, int n) {
    Object[] out = new Object[n];
    for (int i = 0; i < n; i++) {
      out[i] = Js.arg(args, i);
    }
    return out;
  }

  /** The body applied to {@code utils.parseNumber} of each value, or #VALUE! when one of them is an error value. */
  private static Object numeric(Object[] values, Function<double[], Object> body) {
    double[] p = new double[values.length];
    for (int i = 0; i < p.length; i++) {
      Object v = Common.parseNumber(values[i]);
      if (v instanceof JsError) {
        return Errors.VALUE;
      }
      p[i] = (Double) v;
    }
    return body.apply(p);
  }

  /** The body applied to {@code parseNumberArray(flatten(arg))} of the first n arguments, or #VALUE! on an error. */
  @SuppressWarnings("unchecked")
  private static Object arrays(Object[] args, int n, Function<List<Object>[], Object> body) {
    List<Object>[] p = new List[n];
    for (int i = 0; i < n; i++) {
      Object v = parsed(Js.arg(args, i));
      if (v instanceof JsError) {
        return Errors.VALUE;
      }
      p[i] = list(v);
    }
    return body.apply(p);
  }

  /** The body applied to {@code parseNumberArray(flatten(arguments))}, or its error value. */
  private static Object values(Object[] args, Function<List<Object>, Object> body) {
    Object range = Common.parseNumberArray(flat(args));
    return range instanceof JsError ? range : body.apply(list(range));
  }

  private static List<Object> defined(List<Object> values) {
    List<Object> out = new ArrayList<>();
    for (Object v : values) {
      if (Common.isDefined(v)) {
        out.add(v);
      }
    }
    return out;
  }

  /** The argument list {@code fn.apply(thisArg, arrayLike)} builds (CreateListFromArrayLike). */
  private static Object[] spread(Object arrayLike) {
    if (arrayLike instanceof List<?> l) {
      return l.toArray();
    }
    if (arrayLike instanceof Double || arrayLike instanceof String || arrayLike instanceof Boolean) {
      throw JsException.typeError("CreateListFromArrayLike called on non-object");
    }
    return new Object[0];
  }

  /** {@code Math.max(...values)} or, with max false, {@code Math.min(...values)}. */
  private static double extreme(Object[] values, boolean max) {
    double m = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    for (Object v : values) {
      m = max ? Math.max(m, num(v)) : Math.min(m, num(v));
    }
    return m;
  }

  /** The sum of {@code (v - mean) ** power} over the values. */
  private static double moment(List<Object> values, double mean, double power) {
    double sum = 0;
    for (Object v : values) {
      sum += Js.pow(num(v) - mean, power);
    }
    return sum;
  }

  /** {@code evalExpression.compute([createToken(value, TOKEN_TYPE_LITERAL)].concat(tokenizedCriteria))}. */
  private static boolean matches(Object value, List<CriteriaEval.Token> tokenizedCriteria) {
    List<CriteriaEval.Token> tokens = new ArrayList<>();
    tokens.add(CriteriaEval.createToken(value, CriteriaEval.TOKEN_TYPE_LITERAL));
    tokens.addAll(tokenizedCriteria);
    return CriteriaEval.compute(tokens);
  }

  /** {@code criteria === void 0 || criteria === '*' ? null : evalExpression.parse(criteria + '')}. */
  private static List<CriteriaEval.Token> tokenize(Object criteria) {
    return criteria == Js.UNDEFINED || "*".equals(criteria) ? null : CriteriaEval.parse(Js.str(Js.add(criteria, "")));
  }

  /** {@code utils.anyError.apply(undefined, values)} of an array. */
  private static Object anyError(List<Object> values) {
    return Common.anyError(values.toArray());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Functions

  /** Returns the beta cumulative distribution function. */
  public static Object BETA_DIST(Object... args) {
    if (args.length < 4) {
      return Errors.VALUE;
    }
    return numeric(new Object[] {args[0], args[1], args[2], Js.arg(args, 4, 0.0), Js.arg(args, 5, 1.0)}, p -> {
      double x = (p[0] - p[3]) / (p[4] - p[3]);
      return Js.truthy(args[3]) ? JStat.betaCdf(x, p[1], p[2]) : (Object) JStat.betaPdf(x, p[1], p[2]);
    });
  }

  /** Returns the inverse of the cumulative distribution function for a specified beta distribution. */
  public static Object BETA_INV(Object... args) {
    Object[] values = {Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3, 0.0), Js.arg(args, 4, 1.0)};
    return numeric(values, p -> JStat.betaInv(p[0], p[1], p[2]) * (p[4] - p[3]) + p[3]);
  }

  /** Returns the individual term binomial distribution probability. */
  public static Object BINOM_DIST(Object... args) {
    return numeric(params(args, 4),
        p -> p[3] != 0 && !Double.isNaN(p[3]) ? JStat.binomialCdf(p[0], p[1], p[2]) : JStat.binomialPdf(p[0], p[1], p[2]));
  }

  /** Returns the probability of a trial result using a binomial distribution. */
  public static Object BINOM_DIST_RANGE(Object... args) {
    Object[] values = {Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3, Js.arg(args, 2))};
    return numeric(values, p -> {
      double result = 0;
      for (double i = p[2]; i <= p[3]; i++) {
        result += num(MathTrig.COMBIN(p[0], i)) * Js.pow(p[1], i) * Js.pow(1 - p[1], p[0] - i);
      }
      return result;
    });
  }

  /** Returns the chi-squared distribution. */
  public static Object CHISQ_DIST(Object... args) {
    return numeric(params(args, 2),
        p -> Js.truthy(Js.arg(args, 2)) ? JStat.chisquareCdf(p[0], p[1]) : JStat.chisquarePdf(p[0], p[1]));
  }

  /** Returns the one-tailed probability of the chi-squared distribution. */
  public static Object CHISQ_DIST_RT(Object... args) {
    Object x = Js.arg(args, 0);
    Object degFreedom = Js.arg(args, 1);
    if (!Js.truthy(x) | !Js.truthy(degFreedom)) {
      return Errors.NA;
    }
    if (Js.lt(x, 1.0) || Js.gt(degFreedom, 1e10)) {
      return Errors.NUM;
    }
    if (!(x instanceof Double) || !(degFreedom instanceof Double)) {
      return Errors.VALUE;
    }
    return 1 - JStat.chisquareCdf(num(x), num(degFreedom));
  }

  /** Returns the inverse of the left-tailed probability of the chi-squared distribution. */
  public static Object CHISQ_INV(Object... args) {
    return numeric(params(args, 2), p -> JStat.chisquareInv(p[0], p[1]));
  }

  /** Returns the inverse of the one-tailed probability of the chi-squared distribution. */
  public static Object CHISQ_INV_RT(Object... args) {
    Object probability = Js.arg(args, 0);
    Object degFreedom = Js.arg(args, 1);
    if (!Js.truthy(probability) | !Js.truthy(degFreedom)) {
      return Errors.NA;
    }
    if (Js.lt(probability, 0.0) || Js.gt(probability, 1.0) || Js.lt(degFreedom, 1.0) || Js.gt(degFreedom, 1e10)) {
      return Errors.NUM;
    }
    if (!(probability instanceof Double) || !(degFreedom instanceof Double)) {
      return Errors.VALUE;
    }
    return JStat.chisquareInv(1.0 - num(probability), num(degFreedom));
  }

  /** Returns the test for independence. */
  public static Object CHISQ_TEST(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    if (!(args[0] instanceof List) || !(args[1] instanceof List)) {
      return Errors.VALUE;
    }
    List<Object> actual = list(args[0]);
    List<Object> expected = list(args[1]);
    if (actual.size() != expected.size()) {
      return Errors.VALUE;
    }
    Object actual0 = Js.get(actual, 0.0);
    Object expected0 = Js.get(expected, 0.0);
    if (Js.truthy(actual0) && Js.truthy(expected0) && !Js.strictEquals(Js.length(actual0), Js.length(expected0))) {
      return Errors.VALUE;
    }
    int row = actual.size();
    for (int i = 0; i < row; i++) {
      if (!(actual.get(i) instanceof List)) {
        actual.set(i, Js.list(actual.get(i)));
      }
      if (!(expected.get(i) instanceof List)) {
        expected.set(i, Js.list(expected.get(i)));
      }
    }
    double col = num(Js.length(Js.get(actual, 0.0)));
    double dof = col == 1 ? row - 1 : (row - 1) * (col - 1);
    double xsqr = 0;
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < col; j++) {
        double e = at(expected.get(i), j);
        xsqr += Js.pow(at(actual.get(i), j) - e, 2) / e;
      }
    }
    double p = JsMath.exp(-0.5 * xsqr);
    if (dof % 2 == 1) {
      p = p * Math.sqrt((2 * xsqr) / Math.PI);
    }
    for (double k = dof; k >= 2; k = k - 2) {
      p = (p * xsqr) / k;
    }
    double t = p;
    for (double a = dof; t > 0.0000000001 * p; p = p + t) {
      a = a + 2;
      t = (t * xsqr) / a;
    }
    return Js.round((1 - p) * 1000000) / 1000000;
  }

  /** Returns covariance, the average of the products of paired deviations. */
  public static Object COVARIANCE_P(Object... args) {
    return arrays(args, 2, a -> {
      double mean1 = JStat.mean(a[0]);
      double mean2 = JStat.mean(a[1]);
      double result = 0;
      for (int i = 0; i < a[0].size(); i++) {
        result += (num(a[0].get(i)) - mean1) * (at(a[1], i) - mean2);
      }
      return result / a[0].size();
    });
  }

  /** Returns the sample covariance, the average of the products deviations for each data point pair. */
  public static Object COVARIANCE_S(Object... args) {
    return arrays(args, 2, a -> JStat.covariance(a[0], a[1]));
  }

  /** Returns the smallest value for which the cumulative binomial distribution is at least alpha. */
  public static Object BINOM_INV(Object... args) {
    return numeric(params(args, 3), p -> {
      for (double x = 0; x <= p[0]; x++) {
        if (JStat.binomialCdf(x, p[0], p[1]) >= p[2]) {
          return x;
        }
      }
      return Js.UNDEFINED;
    });
  }

  /** Returns the exponential distribution. */
  public static Object EXPON_DIST(Object... args) {
    return numeric(params(args, 2),
        p -> Js.truthy(Js.arg(args, 2)) ? JStat.exponentialCdf(p[0], p[1]) : JStat.exponentialPdf(p[0], p[1]));
  }

  /** Returns the F probability distribution. */
  public static Object F_DIST(Object... args) {
    return numeric(params(args, 3), p -> Js.truthy(Js.arg(args, 3))
        ? JStat.centralFCdf(p[0], p[1], p[2])
        : (Object) JStat.centralFPdf(p[0], p[1], p[2]));
  }

  /** Returns the right-tailed F probability distribution. */
  public static Object F_DIST_RT(Object... args) {
    if (args.length != 3) {
      return Errors.NA;
    }
    if (Js.lt(args[0], 0.0) || Js.lt(args[1], 1.0) || Js.lt(args[2], 1.0)) {
      return Errors.NUM;
    }
    if (!(args[0] instanceof Double) || !(args[1] instanceof Double) || !(args[2] instanceof Double)) {
      return Errors.VALUE;
    }
    return 1 - num(JStat.centralFCdf(num(args[0]), num(args[1]), num(args[2])));
  }

  /** Returns the inverse of the F probability distribution. */
  public static Object F_INV(Object... args) {
    return numeric(params(args, 3),
        p -> p[0] <= 0.0 || p[0] > 1.0 ? Errors.NUM : (Object) JStat.centralFInv(p[0], p[1], p[2]));
  }

  /** Returns the inverse of the right-tailed F probability distribution. */
  public static Object F_INV_RT(Object... args) {
    if (args.length != 3) {
      return Errors.NA;
    }
    if (Js.lt(args[0], 0.0) || Js.gt(args[0], 1.0) || Js.lt(args[1], 1.0) || Js.gt(args[1], 1e10)
        || Js.lt(args[2], 1.0) || Js.gt(args[2], 1e10)) {
      return Errors.NUM;
    }
    if (!(args[0] instanceof Double) || !(args[1] instanceof Double) || !(args[2] instanceof Double)) {
      return Errors.VALUE;
    }
    return JStat.centralFInv(1.0 - num(args[0]), num(args[1]), num(args[2]));
  }

  /** Returns the result of an F-test. */
  public static Object F_TEST(Object... args) {
    Object array1 = Js.arg(args, 0);
    Object array2 = Js.arg(args, 1);
    if (!Js.truthy(array1) || !Js.truthy(array2) || !(array1 instanceof List<?> a1) || !(array2 instanceof List<?> a2)) {
      return Errors.NA;
    }
    if (a1.size() < 2 || a2.size() < 2) {
      return Errors.DIV0;
    }
    double x1 = num(MathTrig.SUM(array1)) / a1.size();
    double x2 = num(MathTrig.SUM(array2)) / a2.size();
    return moment(list(a1), x1, 2) / (a1.size() - 1) / (moment(list(a2), x2, 2) / (a2.size() - 1));
  }

  /** Returns the gamma distribution. */
  public static Object GAMMA_DIST(Object... args) {
    if (args.length != 4) {
      return Errors.NA;
    }
    if (Js.lt(args[0], 0.0) || Js.le(args[1], 0.0) || Js.le(args[2], 0.0)
        || !(args[0] instanceof Double) || !(args[1] instanceof Double) || !(args[2] instanceof Double)) {
      return Errors.VALUE;
    }
    double x = num(args[0]);
    return Js.truthy(args[3])
        ? JStat.gammaCdf(x, num(args[1]), num(args[2]))
        : JStat.gammaPdf(x, num(args[1]), num(args[2]));
  }

  /** Returns the inverse of the gamma cumulative distribution. */
  public static Object GAMMA_INV(Object... args) {
    if (args.length != 3) {
      return Errors.NA;
    }
    if (Js.lt(args[0], 0.0) || Js.gt(args[0], 1.0) || Js.le(args[1], 0.0) || Js.le(args[2], 0.0)) {
      return Errors.NUM;
    }
    if (!(args[0] instanceof Double) || !(args[1] instanceof Double) || !(args[2] instanceof Double)) {
      return Errors.VALUE;
    }
    return JStat.gammaInv(num(args[0]), num(args[1]), num(args[2]));
  }

  /** Returns the natural logarithm of the gamma function. */
  public static Object GAMMALN_PRECISE(Object... args) {
    if (args.length != 1) {
      return Errors.NA;
    }
    if (Js.le(args[0], 0.0)) {
      return Errors.NUM;
    }
    return args[0] instanceof Double x ? (Object) JStat.gammaln(x) : Errors.VALUE;
  }

  /** Returns the hypergeometric distribution. */
  public static Object HYPGEOM_DIST(Object... args) {
    return numeric(params(args, 4), p -> {
      if (!Js.truthy(Js.arg(args, 4))) {
        return hypgeomPdf(p[0], p[1], p[2], p[3]);
      }
      double result = 0;
      for (double i = 0; i <= p[0]; i++) {
        result += hypgeomPdf(i, p[1], p[2], p[3]);
      }
      return result;
    });
  }

  private static double hypgeomPdf(double x, double n, double m, double bigN) {
    return (num(MathTrig.COMBIN(m, x)) * num(MathTrig.COMBIN(bigN - m, n - x))) / num(MathTrig.COMBIN(bigN, n));
  }

  /** Returns the inverse of the lognormal cumulative distribution. */
  public static Object LOGNORM_INV(Object... args) {
    return numeric(params(args, 3), p -> JStat.lognormalInv(p[0], p[1], p[2]));
  }

  /** Returns the cumulative lognormal distribution. */
  public static Object LOGNORM_DIST(Object... args) {
    return numeric(params(args, 3), p -> Js.truthy(Js.arg(args, 3))
        ? JStat.lognormalCdf(p[0], p[1], p[2])
        : JStat.lognormalPdf(p[0], p[1], p[2]));
  }

  /** Returns a vertical array of the most frequently occurring values in an array or range of data. */
  public static Object MODE_MULT(Object... args) {
    return values(args, range -> {
      Map<String, Double> count = new HashMap<>();
      List<Object> maxItems = new ArrayList<>();
      double max = 0;
      for (Object currentItem : range) {
        double c = count.merge(Js.str(currentItem), 1.0, Double::sum);
        if (c > max) {
          max = c;
          maxItems = new ArrayList<>();
        }
        if (c == max) {
          maxItems.add(currentItem);
        }
      }
      return maxItems;
    });
  }

  /** Returns the most common value in a data set. */
  public static Object MODE_SNGL(Object... args) {
    return values(args, range -> {
      Object modes = MODE_MULT(range);
      if (!(modes instanceof List)) {
        throw JsException.notAFunction("MODE.MULT(...).sort");
      }
      return Js.get(Js.sort(list(modes), ASC), 0.0);
    });
  }

  /** Returns the negative binomial distribution. */
  public static Object NEGBINOM_DIST(Object... args) {
    return numeric(params(args, 3), p -> Js.truthy(Js.arg(args, 3))
        ? (Object) JStat.negbinCdf(p[0], p[1], p[2])
        : JStat.negbinPdf(p[0], p[1], p[2]));
  }

  /** Returns the normal cumulative distribution. */
  public static Object NORM_DIST(Object... args) {
    return numeric(params(args, 3), p -> p[2] <= 0 ? Errors.NUM : Js.truthy(Js.arg(args, 3))
        ? (Object) JStat.normalCdf(p[0], p[1], p[2])
        : JStat.normalPdf(p[0], p[1], p[2]));
  }

  /** Returns the inverse of the normal cumulative distribution. */
  public static Object NORM_INV(Object... args) {
    return numeric(params(args, 3), p -> JStat.normalInv(p[0], p[1], p[2]));
  }

  /** Returns the standard normal cumulative distribution. */
  public static Object NORM_S_DIST(Object... args) {
    return numeric(params(args, 1),
        p -> Js.truthy(Js.arg(args, 1)) ? JStat.normalCdf(p[0], 0, 1) : JStat.normalPdf(p[0], 0, 1));
  }

  /** Returns the inverse of the standard normal cumulative distribution. */
  public static Object NORM_S_INV(Object... args) {
    return numeric(params(args, 1), p -> JStat.normalInv(p[0], 0, 1));
  }

  /** Returns the k-th percentile of values in a range, where k is in the range 0..1, exclusive. */
  public static Object PERCENTILE_EXC(Object... args) {
    return percentile(args, true);
  }

  /** Returns the k-th percentile of values in a range. */
  public static Object PERCENTILE_INC(Object... args) {
    return percentile(args, false);
  }

  private static Object percentile(Object[] args, boolean exclusive) {
    Object array = parsed(Js.arg(args, 0));
    Object k = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(array, k)) {
      return Errors.VALUE;
    }
    List<Object> sorted = Js.sort(list(array), ASC);
    double n = sorted.size();
    double kv = num(k);
    if (exclusive && (kv < 1 / (n + 1) || kv > 1 - 1 / (n + 1))) {
      return Errors.NUM;
    }
    double l = exclusive ? kv * (n + 1) - 1 : kv * (n - 1);
    double fl = Math.floor(l);
    return Common.cleanFloat(
        l == fl ? at(sorted, l) : at(sorted, fl) + (l - fl) * (at(sorted, fl + 1) - at(sorted, fl)));
  }

  /** Returns the rank of a value in a data set as a percentage (0..1, exclusive) of the data set. */
  public static Object PERCENTRANK_EXC(Object... args) {
    return percentRank(args, true);
  }

  /** Returns the percentage rank of a value in a data set. */
  public static Object PERCENTRANK_INC(Object... args) {
    return percentRank(args, false);
  }

  private static Object percentRank(Object[] args, boolean exclusive) {
    Object array = parsed(Js.arg(args, 0));
    Object x = Common.parseNumber(Js.arg(args, 1));
    Object significance = Common.parseNumber(Js.arg(args, 2, 3.0));
    if (Common.anyIsError(array, x, significance)) {
      return Errors.VALUE;
    }
    List<Object> sorted = Js.sort(list(array), ASC);
    Object uniques = LookupReference.UNIQUE(sorted.toArray());
    double n = sorted.size();
    double m = num(Js.length(uniques));
    double power = Js.pow(10, num(significance));
    double result = 0;
    boolean match = false;
    for (int i = 0; !match && i < m; i++) {
      Object unique = Js.get(uniques, i);
      double base = Js.indexOf(sorted, unique) + (exclusive ? 1 : 0);
      if (Js.strictEquals(x, unique)) {
        match = true;
      } else if (Js.ge(x, unique) && (Js.lt(x, Js.get(uniques, i + 1)) || i == m - 1)) {
        base = base + (num(x) - num(unique)) / (at(uniques, i + 1) - num(unique));
        match = true;
      }
      if (match) {
        result = exclusive ? base / (n + 1) : base / (n - 1);
      }
    }
    return Math.floor(result * power) / power;
  }

  /** Returns the Poisson distribution. */
  public static Object POISSON_DIST(Object... args) {
    return numeric(params(args, 2),
        p -> Js.truthy(Js.arg(args, 2)) ? JStat.poissonCdf(p[0], p[1]) : JStat.poissonPdf(p[0], p[1]));
  }

  /** Returns the quartile of the data set, based on percentile values from 0..1, exclusive. */
  public static Object QUARTILE_EXC(Object... args) {
    return quartile(args, true);
  }

  /** Returns the quartile of a data set. */
  public static Object QUARTILE_INC(Object... args) {
    return quartile(args, false);
  }

  private static Object quartile(Object[] args, boolean exclusive) {
    Object range = Common.parseNumberArray(Common.numbers(Common.flatten(Js.arg(args, 0))));
    Object quart = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(range, quart)) {
      return Errors.VALUE;
    }
    for (int q = 1; q <= 3; q++) {
      if (Js.strictEquals(quart, (double) q)) {
        return exclusive ? PERCENTILE_EXC(range, q * 0.25) : PERCENTILE_INC(range, q * 0.25);
      }
    }
    return Errors.NUM;
  }

  /** Returns the rank of a number in a list of numbers (the average rank of ties). */
  public static Object RANK_AVG(Object... args) {
    return rank(args, true);
  }

  /** Returns the rank of a number in a list of numbers. */
  public static Object RANK_EQ(Object... args) {
    return rank(args, false);
  }

  private static Object rank(Object[] args, boolean average) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    Object ref = parsed(Js.arg(args, 1));
    if (Common.anyIsError(number, ref)) {
      return Errors.VALUE;
    }
    List<Object> sorted = Js.sort(average ? Common.flatten(ref) : list(ref), Js.truthy(Js.arg(args, 2)) ? ASC : DESC);
    int index = Js.indexOf(sorted, number);
    int count = 0;
    for (Object v : sorted) {
      count += average && Js.strictEquals(v, number) ? 1 : 0;
    }
    return count > 1 ? (2 * index + count + 1) / 2.0 : (double) (index + 1);
  }

  /** Returns the skewness of a distribution based on a population. */
  public static Object SKEW_P(Object... args) {
    return values(args, range -> {
      double mean = JStat.mean(range);
      double n = range.size();
      double m3 = 0;
      double m2 = 0;
      for (Object v : range) {
        m3 += Js.pow(num(v) - mean, 3);
        m2 += Js.pow(num(v) - mean, 2);
      }
      return m3 / n / Js.pow(m2 / n, 3.0 / 2);
    });
  }

  /** Calculates standard deviation based on the entire population. */
  public static Object STDEV_P(Object... args) {
    double result = Math.sqrt(num(VAR_P(args)));
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  /** Estimates standard deviation based on a sample. */
  public static Object STDEV_S(Object... args) {
    return Math.sqrt(num(VAR_S(args)));
  }

  /** Returns the Percentage Points (probability) for the Student t-distribution. */
  public static Object T_DIST(Object... args) {
    Object cumulative = Js.arg(args, 2);
    if (!Js.strictEquals(cumulative, 1.0) && !Js.strictEquals(cumulative, 2.0)) {
      return Errors.NUM;
    }
    Object x = Js.arg(args, 0);
    Object degFreedom = Js.arg(args, 1);
    return Js.strictEquals(cumulative, 1.0) ? T_DIST_RT(x, degFreedom) : T_DIST_2T(x, degFreedom);
  }

  /** Returns the two-tailed Student's t-distribution. */
  public static Object T_DIST_2T(Object... args) {
    Object check = tDistCheck(args);
    return check != null ? check : (Object) ((1 - num(JStat.studenttCdf(num(args[0]), num(args[1])))) * 2);
  }

  /** Returns the right-tailed Student's t-distribution. */
  public static Object T_DIST_RT(Object... args) {
    Object check = tDistCheck(args);
    return check != null ? check : (Object) (1 - num(JStat.studenttCdf(num(args[0]), num(args[1]))));
  }

  private static Object tDistCheck(Object[] args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    if (Js.lt(args[0], 0.0) || Js.lt(args[1], 1.0)) {
      return Errors.NUM;
    }
    return !(args[0] instanceof Double) || !(args[1] instanceof Double) ? Errors.VALUE : null;
  }

  /** Returns the t-value of the Student's t-distribution as a function of the probability and the degrees of freedom. */
  public static Object T_INV(Object... args) {
    return numeric(params(args, 2), p -> JStat.studenttInv(p[0], p[1]));
  }

  /** Returns the two-tailed inverse of the Student's t-distribution. */
  public static Object T_INV_2T(Object... args) {
    Object probability = Common.parseNumber(Js.arg(args, 0));
    Object degFreedom = Common.parseNumber(Js.arg(args, 1));
    if (Js.le(probability, 0.0) || Js.gt(probability, 1.0) || Js.lt(degFreedom, 1.0)) {
      return Errors.NUM;
    }
    if (Common.anyIsError(probability, degFreedom)) {
      return Errors.VALUE;
    }
    return Math.abs(JStat.studenttInv(num(probability) / 2, num(degFreedom)));
  }

  /** Returns the probability associated with a Student's t-test. */
  public static Object T_TEST(Object... args) {
    return arrays(args, 2, a -> {
      double meanX = JStat.mean(a[0]);
      double meanY = JStat.mean(a[1]);
      double sX = moment(a[0], meanX, 2) / (a[0].size() - 1);
      double sY = moment(a[1], meanY, 2) / (a[1].size() - 1);
      double t = Math.abs(meanX - meanY) / Math.sqrt(sX / a[0].size() + sY / a[1].size());
      return T_DIST_2T(t, (double) (a[0].size() + a[1].size() - 2));
    });
  }

  /** Calculates variance based on the entire population. */
  public static Object VAR_P(Object... args) {
    List<Object> range = Common.numbers(flat(args));
    double result = moment(range, num(AVERAGE(range)), 2) / range.size();
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  /** Estimates variance based on a sample. */
  public static Object VAR_S(Object... args) {
    List<Object> range = Common.numbers(flat(args));
    return moment(range, num(AVERAGE(range)), 2) / (range.size() - 1);
  }

  /** Returns the Weibull distribution. */
  public static Object WEIBULL_DIST(Object... args) {
    return numeric(params(args, 3), p -> Js.truthy(Js.arg(args, 3))
        ? 1 - JsMath.exp(-Js.pow(p[0] / p[2], p[1]))
        : (Js.pow(p[0], p[1] - 1) * JsMath.exp(-Js.pow(p[0] / p[2], p[1])) * p[1]) / Js.pow(p[2], p[1]));
  }

  /** Returns the one-tailed probability-value of a z-test. */
  public static Object Z_TEST(Object... args) {
    Object array = parsed(Js.arg(args, 0));
    Object x = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(array, x)) {
      return Errors.VALUE;
    }
    Object sigma = Js.truthy(Js.arg(args, 2)) ? Js.arg(args, 2) : STDEV_S(array);
    double n = list(array).size();
    return 1 - num(NORM_S_DIST((num(AVERAGE(array)) - num(x)) / (num(sigma) / Math.sqrt(n)), true));
  }

  /** Returns the average of the absolute deviations of data points from their mean. */
  public static Object AVEDEV(Object... args) {
    List<Object> flatArgumentsDefined = defined(flat(args));
    if (flatArgumentsDefined.isEmpty()) {
      return Errors.NUM;
    }
    Object range = Common.parseNumberArray(flatArgumentsDefined);
    if (range instanceof JsError) {
      return range;
    }
    double mean = JStat.mean(list(range));
    List<Object> deviations = new ArrayList<>();
    for (Object v : list(range)) {
      deviations.add(Math.abs(num(v) - mean));
    }
    return num(JStat.sum(deviations)) / list(range).size();
  }

  /** Returns the average of its arguments. */
  public static Object AVERAGE(Object... args) {
    return average(args, false);
  }

  /** Returns the average of its arguments, including numbers, text, and logical values. */
  public static Object AVERAGEA(Object... args) {
    return average(args, true);
  }

  private static Object average(Object[] args, boolean all) {
    List<Object> range = defined(flat(args));
    if (range.isEmpty()) {
      return Errors.DIV0;
    }
    Object someError = anyError(range);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    double sum = 0;
    double count = 0;
    for (Object el : all ? range : Common.numbers(range)) {
      sum += el instanceof Double d ? d : Boolean.TRUE.equals(el) ? 1 : 0;
      count++;
    }
    double result = sum / count;
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  /** Returns the average (arithmetic mean) of all the values in a range that meet a given criteria. */
  public static Object AVERAGEIF(Object... args) {
    if (args.length <= 1) {
      return Errors.NA;
    }
    Object averageRange = Js.truthy(Js.arg(args, 2)) ? args[2] : args[0];
    Object average = Common.parseNumberArray(defined(Common.flatten(averageRange)));
    List<Object> range = Common.flatten(args[0]);
    if (average instanceof JsError) {
      return average;
    }
    double averageCount = 0;
    double result = 0;
    List<CriteriaEval.Token> tokenizedCriteria = tokenize(args[1]);
    for (int i = 0; i < range.size(); i++) {
      if (tokenizedCriteria == null || matches(range.get(i), tokenizedCriteria)) {
        result += at(average, i);
        averageCount++;
      }
    }
    return result / averageCount;
  }

  /** Returns the average (arithmetic mean) of all values that meet multiple criteria. */
  public static Object AVERAGEIFS(Object... args) {
    Object values = Common.applyCriteria(args);
    if (!(values instanceof List<?> l)) {
      throw JsException.notAFunction("values.reduce");
    }
    Object result = 0.0;
    for (Object v : l) {
      result = Js.add(result, v);
    }
    double average = num(result) / l.size();
    return Double.isNaN(average) ? 0.0 : average;
  }

  /** Returns the confidence interval for a population mean. */
  public static Object CONFIDENCE_NORM(Object... args) {
    return numeric(params(args, 3), p -> JStat.normalci(1, p[0], p[1], p[2])[1] - 1);
  }

  /** Returns the confidence interval for a population mean, using a Student's t distribution. */
  public static Object CONFIDENCE_T(Object... args) {
    return numeric(params(args, 3), p -> JStat.tci(1, p[0], p[1], p[2])[1] - 1);
  }

  /** Returns the correlation coefficient between two data sets. */
  public static Object CORREL(Object... args) {
    return arrays(args, 2, a -> JStat.corrcoeff(a[0], a[1]));
  }

  /** Counts how many numbers are in the list of arguments. */
  public static Object COUNT(Object... args) {
    return (double) Common.numbers(flat(args)).size();
  }

  /** Counts how many values are in the list of arguments. */
  public static Object COUNTA(Object... args) {
    List<Object> flatArguments = flat(args);
    return flatArguments.size() - num(COUNTBLANK(flatArguments));
  }

  /** Counts the number of blank values within a range. */
  public static Object COUNTBLANK(Object... args) {
    double blanks = 0;
    for (Object element : flat(args)) {
      blanks += element == Js.UNDEFINED || element == null || "".equals(element) ? 1 : 0;
    }
    return blanks;
  }

  /** Counts the number of values within a range that meet the given criteria. */
  public static Object COUNTIF(Object... args) {
    List<Object> range = Common.flatten(Js.arg(args, 0));
    List<CriteriaEval.Token> tokenizedCriteria = tokenize(Js.arg(args, 1));
    if (tokenizedCriteria == null) {
      return (double) range.size();
    }
    double matches = 0;
    for (Object value : range) {
      matches += matches(value, tokenizedCriteria) ? 1 : 0;
    }
    return matches;
  }

  /** Counts the number of values within a range that meet multiple criteria. */
  public static Object COUNTIFS(Object... args) {
    List<Object> arguments = Common.argsToArray(Common.arguments(args));
    boolean[] results = new boolean[Common.flatten(Js.get(arguments, 0.0)).size()];
    Arrays.fill(results, true);
    for (int i = 0; i < arguments.size(); i += 2) {
      List<Object> range = Common.flatten(arguments.get(i));
      List<CriteriaEval.Token> tokenizedCriteria = tokenize(Js.get(arguments, i + 1));
      for (int j = 0; tokenizedCriteria != null && j < range.size(); j++) {
        if (j < results.length && results[j]) {
          results[j] = matches(range.get(j), tokenizedCriteria);
        }
      }
    }
    double result = 0;
    for (boolean r : results) {
      result += r ? 1 : 0;
    }
    return result;
  }

  /** Returns the sum of squares of deviations. */
  public static Object DEVSQ(Object... args) {
    return values(args, range -> moment(range, JStat.mean(range), 2));
  }

  /** Returns the Fisher transformation. */
  public static Object FISHER(Object... args) {
    Object x = Common.parseNumber(Js.arg(args, 0));
    return x instanceof JsError ? x : (Object) (StrictMath.log((1 + num(x)) / (1 - num(x))) / 2);
  }

  /** Returns the inverse of the Fisher transformation. */
  public static Object FISHERINV(Object... args) {
    Object y = Common.parseNumber(Js.arg(args, 0));
    if (y instanceof JsError) {
      return y;
    }
    double e2y = JsMath.exp(2 * num(y));
    return (e2y - 1) / (e2y + 1);
  }

  /** Returns a value along a linear trend. */
  public static Object FORECAST(Object... args) {
    Object x = Common.parseNumber(Js.arg(args, 0));
    Object knownYs = parsed(Js.arg(args, 1));
    Object knownXs = parsed(Js.arg(args, 2));
    if (Common.anyIsError(x, knownYs, knownXs)) {
      return Errors.VALUE;
    }
    double[] line = regression(list(knownYs), list(knownXs));
    double b = line[0] / line[1];
    return line[3] - b * line[2] + b * num(x);
  }

  /** {num, den, xmean, ymean, lft}: the sums of (x - xmean) * (y - ymean), (x - xmean)^2 and (y - ymean)^2 over x. */
  private static double[] regression(List<Object> ys, List<Object> xs) {
    double xmean = JStat.mean(xs);
    double ymean = JStat.mean(ys);
    double num = 0;
    double den = 0;
    double lft = 0;
    for (int i = 0; i < xs.size(); i++) {
      double x = num(xs.get(i));
      double y = at(ys, i);
      lft += Js.pow(y - ymean, 2);
      num += (x - xmean) * (y - ymean);
      den += Js.pow(x - xmean, 2);
    }
    return new double[] {num, den, xmean, ymean, lft};
  }

  /** Returns a frequency distribution as a vertical array. */
  public static Object FREQUENCY(Object... args) {
    return arrays(args, 2, a -> {
      List<Object> data = a[0];
      List<Object> bins = a[1];
      int b = bins.size();
      List<Object> r = new ArrayList<>();
      for (int i = 0; i <= b; i++) {
        double count = 0;
        for (Object v : data) {
          double d = num(v);
          if (i == 0 ? d <= num(bins.get(0)) : i < b ? d > num(bins.get(i - 1)) && d <= num(bins.get(i))
              : d > num(bins.get(b - 1))) {
            count += 1;
          }
        }
        r.add(count);
      }
      return r;
    });
  }

  /** Returns the Gamma function value. */
  public static Object GAMMA(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    if (number instanceof JsError) {
      return number;
    }
    double n = num(number);
    return n == 0 || (Js.parseInt(number, 10.0) == n && n < 0) ? Errors.NUM : (Object) JStat.gammafn(n);
  }

  /** Returns the natural logarithm of the gamma function. */
  public static Object GAMMALN(Object... args) {
    Object x = Common.parseNumber(Js.arg(args, 0));
    return x instanceof JsError ? x : (Object) JStat.gammaln(num(x));
  }

  /** Returns 0.5 less than the standard normal cumulative distribution. */
  public static Object GAUSS(Object... args) {
    Object z = Common.parseNumber(Js.arg(args, 0));
    return z instanceof JsError ? z : (Object) (JStat.normalCdf(num(z), 0, 1) - 0.5);
  }

  /** Returns the geometric mean. */
  public static Object GEOMEAN(Object... args) {
    return values(args, JStat::geomean);
  }

  /** Returns values along an exponential trend. */
  public static Object GROWTH(Object... args) {
    Object knownY = parsed(Js.arg(args, 0));
    if (knownY instanceof JsError) {
      return knownY;
    }
    List<Object> ys = list(knownY);
    Object knownX = Js.arg(args, 1);
    if (knownX == Js.UNDEFINED) {
      List<Object> sequence = new ArrayList<>();
      for (int i = 1; i <= ys.size(); i++) {
        sequence.add((double) i);
      }
      knownX = sequence;
    }
    Object newX = Js.arg(args, 2, knownX);
    knownX = parsed(knownX);
    newX = parsed(newX);
    if (Common.anyIsError(knownX, newX)) {
      return Errors.VALUE;
    }
    int n = ys.size();
    double avgX = 0;
    double avgY = 0;
    double avgXY = 0;
    double avgXX = 0;
    for (int i = 0; i < n; i++) {
      double x = at(knownX, i);
      double y = StrictMath.log(num(ys.get(i)));
      avgX += x;
      avgY += y;
      avgXY += x * y;
      avgXX += x * x;
    }
    avgX /= n;
    avgY /= n;
    avgXY /= n;
    avgXX /= n;
    boolean useConst = Js.truthy(Js.arg(args, 3, true));
    double beta = useConst ? (avgXY - avgX * avgY) / (avgXX - avgX * avgX) : avgXY / avgXX;
    double alpha = useConst ? avgY - beta * avgX : 0;
    List<Object> newY = new ArrayList<>();
    for (Object x : list(newX)) {
      newY.add(JsMath.exp(alpha + beta * num(x)));
    }
    return newY;
  }

  /** Returns the harmonic mean. */
  public static Object HARMEAN(Object... args) {
    return values(args, range -> {
      double den = 0;
      for (Object v : range) {
        den += 1 / num(v);
      }
      return range.size() / den;
    });
  }

  /** Returns the intercept of the linear regression line. */
  public static Object INTERCEPT(Object... args) {
    Object knownY = Common.parseNumberArray(Js.arg(args, 0));
    Object knownX = Common.parseNumberArray(Js.arg(args, 1));
    if (Common.anyIsError(knownY, knownX)) {
      return Errors.VALUE;
    }
    return Js.strictEquals(Js.length(knownY), Js.length(knownX)) ? FORECAST(0.0, knownY, knownX) : Errors.NA;
  }

  /** Returns the kurtosis of a data set. */
  public static Object KURT(Object... args) {
    return values(args, range -> {
      double n = range.size();
      double sigma = moment(range, JStat.mean(range), 4) / Js.pow(JStat.stdev(range, true), 4);
      return ((n * (n + 1)) / ((n - 1) * (n - 2) * (n - 3))) * sigma - (3 * (n - 1) * (n - 1)) / ((n - 2) * (n - 3));
    });
  }

  /** Returns the k-th largest value in a data set. */
  public static Object LARGE(Object... args) {
    Object array = Js.arg(args, 0);
    Object k = Js.arg(args, 1);
    Object someError = Common.anyError(spread(array));
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    if (k instanceof JsError) {
      return k;
    }
    List<Object> values = Common.numbers(Common.flatten(array));
    k = Common.parseNumber(k);
    if (Js.lt(k, 0.0) || Js.lt((double) values.size(), k)) {
      return Errors.VALUE;
    }
    return Js.get(Js.sort(values, DESC), num(k) - 1);
  }

  /** Returns the parameters of a linear trend. */
  public static Object LINEST(Object... args) {
    return arrays(args, 2, a -> {
      double[] line = regression(a[0], a[1]);
      double m = line[0] / line[1];
      return Js.list(m, line[3] - m * line[2]);
    });
  }

  /** Returns the parameters of an exponential trend. */
  public static Object LOGEST(Object... args) {
    return arrays(args, 2, a -> {
      if (a[0].size() != a[1].size()) {
        return Errors.VALUE;
      }
      a[0].replaceAll(v -> StrictMath.log(num(v)));
      Object result = LINEST(a[0], a[1]);
      if (result instanceof List<?>) {
        list(result).replaceAll(v -> Js.round(JsMath.exp(num(v)) * 1000000) / 1000000);
      }
      return result;
    });
  }

  /** Returns the maximum value in a list of arguments. */
  public static Object MAX(Object... args) {
    return extremeOf(args, true, false);
  }

  /** Returns the maximum value in a list of arguments, including numbers, text, and logical values. */
  public static Object MAXA(Object... args) {
    return extremeOf(args, true, true);
  }

  /** MAX, MIN (numbers only) and MAXA, MINA (values converted, null and undefined as 0). */
  private static Object extremeOf(Object[] args, boolean max, boolean all) {
    List<Object> flatArguments = flat(args);
    Object someError = anyError(flatArguments);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    List<Object> range = all ? Common.arrayValuesToNumbers(flatArguments) : Common.numbers(flatArguments);
    return range.isEmpty() ? 0.0 : extreme(range.stream().map(v -> Js.isNullish(v) ? 0.0 : v).toArray(), max);
  }

  /** Returns the maximum of all values in a range that meet multiple criteria. */
  public static Object MAXIFS(Object... args) {
    Object values = Common.applyCriteria(args);
    return Js.strictEquals(Js.length(values), 0.0) ? 0.0 : extreme(spread(values), true);
  }

  /** Returns the median of the given numbers. */
  public static Object MEDIAN(Object... args) {
    List<Object> flatArguments = flat(args);
    Object someError = anyError(flatArguments);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    Object result = JStat.median(Common.arrayValuesToNumbers(flatArguments));
    return Js.isNaN(result) ? Errors.NUM : result;
  }

  /** Returns the minimum value in a list of arguments. */
  public static Object MIN(Object... args) {
    return extremeOf(args, false, false);
  }

  /** Returns the smallest value in a list of arguments, including numbers, text, and logical values. */
  public static Object MINA(Object... args) {
    return extremeOf(args, false, true);
  }

  /** Returns the minimum of all values in a range that meet multiple criteria. */
  public static Object MINIFS(Object... args) {
    Object values = Common.applyCriteria(args);
    return Js.strictEquals(Js.length(values), 0.0) ? 0.0 : extreme(spread(values), false);
  }

  /** Returns the Pearson product moment correlation coefficient. */
  public static Object PEARSON(Object... args) {
    return arrays(args, 2, a -> {
      double xmean = JStat.mean(a[0]);
      double ymean = JStat.mean(a[1]);
      double num = 0;
      double den1 = 0;
      double den2 = 0;
      for (int i = 0; i < a[0].size(); i++) {
        double x = num(a[0].get(i));
        double y = at(a[1], i);
        num += (x - xmean) * (y - ymean);
        den1 += Js.pow(x - xmean, 2);
        den2 += Js.pow(y - ymean, 2);
      }
      return num / Math.sqrt(den1 * den2);
    });
  }

  /** Returns the number of permutations for a given number of objects. */
  public static Object PERMUT(Object... args) {
    return numeric(params(args, 2), p -> num(MathTrig.FACT(p[0])) / num(MathTrig.FACT(p[0] - p[1])));
  }

  /** Returns the number of permutations (with repetitions) that can be selected from the total objects. */
  public static Object PERMUTATIONA(Object... args) {
    return numeric(params(args, 2), p -> Js.pow(p[0], p[1]));
  }

  /** Returns the value of the density function for a standard normal distribution. */
  public static Object PHI(Object... args) {
    return numeric(params(args, 1), p -> JsMath.exp(-0.5 * p[0] * p[0]) / SQRT2PI);
  }

  /** Returns the probability that values in a range are between two limits. */
  public static Object PROB(Object... args) {
    Object lowerLimit = Js.arg(args, 2);
    if (lowerLimit == Js.UNDEFINED) {
      return 0.0;
    }
    Object upperLimit = Common.parseNumber(Js.arg(args, 3, lowerLimit));
    Object xRange = parsed(Js.arg(args, 0));
    Object probRange = parsed(Js.arg(args, 1));
    lowerLimit = Common.parseNumber(lowerLimit);
    if (Common.anyIsError(xRange, probRange, lowerLimit, upperLimit)) {
      return Errors.VALUE;
    }
    List<Object> xs = list(xRange);
    if (Js.strictEquals(lowerLimit, upperLimit)) {
      return Js.indexOf(xs, lowerLimit) >= 0 ? Js.get(probRange, Js.indexOf(xs, lowerLimit)) : (Object) 0.0;
    }
    double result = 0;
    for (Object v : Js.sort(xs, ASC)) {
      if (num(v) >= num(lowerLimit) && num(v) <= num(upperLimit)) {
        result += at(probRange, Js.indexOf(xs, v));
      }
    }
    return result;
  }

  /** Returns the row number of a reference. */
  public static Object ROW(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    if (Js.lt(args[1], 0.0)) {
      return Errors.NUM;
    }
    if (!(args[0] instanceof List<?> rows) || !(args[1] instanceof Double)) {
      return Errors.VALUE;
    }
    return rows.isEmpty() ? Js.UNDEFINED : JStat.row(args[0], args[1]);
  }

  /** Returns the square of the Pearson product moment correlation coefficient. */
  public static Object RSQ(Object... args) {
    return arrays(args, 2, a -> Js.pow(num(PEARSON(a[0], a[1])), 2));
  }

  /** Returns the skewness of a distribution. */
  public static Object SKEW(Object... args) {
    return values(args, range -> {
      double n = range.size();
      return (n * moment(range, JStat.mean(range), 3)) / ((n - 1) * (n - 2) * Js.pow(JStat.stdev(range, true), 3));
    });
  }

  /** Returns the slope of the linear regression line. */
  public static Object SLOPE(Object... args) {
    return arrays(args, 2, a -> {
      double[] line = regression(a[0], a[1]);
      return line[0] / line[1];
    });
  }

  /** Returns the k-th smallest value in a data set. */
  public static Object SMALL(Object... args) {
    Object array = parsed(Js.arg(args, 0));
    Object k = Common.parseNumber(Js.arg(args, 1));
    return Common.anyIsError(array, k) ? array : Js.get(Js.sort(list(array), ASC), num(k) - 1);
  }

  /** Returns a normalized value. */
  public static Object STANDARDIZE(Object... args) {
    return numeric(params(args, 3), p -> (p[0] - p[1]) / p[2]);
  }

  /** Estimates standard deviation based on a sample, including numbers, text, and logical values. */
  public static Object STDEVA(Object... args) {
    return Math.sqrt(num(VARA(args)));
  }

  /** Calculates standard deviation based on the entire population, including numbers, text, and logical values. */
  public static Object STDEVPA(Object... args) {
    double result = Math.sqrt(num(VARPA(args)));
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  /** Returns the standard error of the predicted y-value for each x in the regression. */
  public static Object STEYX(Object... args) {
    return arrays(args, 2, a -> {
      double[] line = regression(a[0], a[1]);
      return Math.sqrt((line[4] - (line[0] * line[0]) / line[1]) / (a[1].size() - 2));
    });
  }

  /** Returns values along a linear trend. */
  public static Object TREND(Object... args) {
    return arrays(args, 3, a -> {
      Object linest = LINEST(a[0], a[1]);
      List<Object> result = new ArrayList<>();
      for (Object x : a[2]) {
        result.add(at(linest, 0) * num(x) + at(linest, 1));
      }
      return result;
    });
  }

  /** Returns the mean of the interior of a data set. */
  public static Object TRIMMEAN(Object... args) {
    Object range = parsed(Js.arg(args, 0));
    Object percent = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(range, percent)) {
      return Errors.VALUE;
    }
    Object trim = num(MathTrig.FLOOR(list(range).size() * num(percent), 2.0)) / 2;
    return JStat.mean(list(Common.initial(Common.rest(Js.sort(list(range), ASC), trim), trim)));
  }

  /** Estimates variance based on a sample, including numbers, text, and logical values. */
  public static Object VARA(Object... args) {
    double[] sums = varianceA(args);
    return sums[0] / (sums[1] - 1);
  }

  /** Calculates variance based on the entire population, including numbers, text, and logical values. */
  public static Object VARPA(Object... args) {
    double[] sums = varianceA(args);
    double result = sums[0] / sums[1];
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  /** VARA's and VARPA's {sigma, count}: text and false count as 0, true as 1, null is not counted. */
  private static double[] varianceA(Object[] args) {
    List<Object> range = flat(args);
    double mean = num(AVERAGEA(range));
    double sigma = 0;
    double count = 0;
    for (Object el : range) {
      sigma += Js.pow((el instanceof Double d ? d : Boolean.TRUE.equals(el) ? 1 : 0) - mean, 2);
      count += el != null ? 1 : 0;
    }
    return new double[] {sigma, count};
  }

  static void register(Map<String, Fn> ops) {
    ops.put("AVEDEV", Statistical::AVEDEV);
    ops.put("AVERAGE", Statistical::AVERAGE);
    ops.put("AVERAGEA", Statistical::AVERAGEA);
    ops.put("AVERAGEIF", Statistical::AVERAGEIF);
    ops.put("AVERAGEIFS", Statistical::AVERAGEIFS);
    ops.put("BETA.DIST", Statistical::BETA_DIST);
    ops.put("BETA.INV", Statistical::BETA_INV);
    ops.put("BINOM.DIST", Statistical::BINOM_DIST);
    ops.put("BINOM.DIST.RANGE", Statistical::BINOM_DIST_RANGE);
    ops.put("BINOM.INV", Statistical::BINOM_INV);
    ops.put("CHISQ.DIST", Statistical::CHISQ_DIST);
    ops.put("CHISQ.DIST.RT", Statistical::CHISQ_DIST_RT);
    ops.put("CHISQ.INV", Statistical::CHISQ_INV);
    ops.put("CHISQ.INV.RT", Statistical::CHISQ_INV_RT);
    ops.put("CHISQ.TEST", Statistical::CHISQ_TEST);
    ops.put("CONFIDENCE.NORM", Statistical::CONFIDENCE_NORM);
    ops.put("CONFIDENCE.T", Statistical::CONFIDENCE_T);
    ops.put("CORREL", Statistical::CORREL);
    ops.put("COUNT", Statistical::COUNT);
    ops.put("COUNTA", Statistical::COUNTA);
    ops.put("COUNTBLANK", Statistical::COUNTBLANK);
    ops.put("COUNTIF", Statistical::COUNTIF);
    ops.put("COUNTIFS", Statistical::COUNTIFS);
    ops.put("COVARIANCE.P", Statistical::COVARIANCE_P);
    ops.put("COVARIANCE.S", Statistical::COVARIANCE_S);
    ops.put("DEVSQ", Statistical::DEVSQ);
    ops.put("EXPON.DIST", Statistical::EXPON_DIST);
    ops.put("F.DIST", Statistical::F_DIST);
    ops.put("F.DIST.RT", Statistical::F_DIST_RT);
    ops.put("F.INV", Statistical::F_INV);
    ops.put("F.INV.RT", Statistical::F_INV_RT);
    ops.put("F.TEST", Statistical::F_TEST);
    ops.put("FISHER", Statistical::FISHER);
    ops.put("FISHERINV", Statistical::FISHERINV);
    ops.put("FORECAST", Statistical::FORECAST);
    ops.put("FREQUENCY", Statistical::FREQUENCY);
    ops.put("GAMMA", Statistical::GAMMA);
    ops.put("GAMMA.DIST", Statistical::GAMMA_DIST);
    ops.put("GAMMA.INV", Statistical::GAMMA_INV);
    ops.put("GAMMALN", Statistical::GAMMALN);
    ops.put("GAMMALN.PRECISE", Statistical::GAMMALN_PRECISE);
    ops.put("GAUSS", Statistical::GAUSS);
    ops.put("GEOMEAN", Statistical::GEOMEAN);
    ops.put("GROWTH", Statistical::GROWTH);
    ops.put("HARMEAN", Statistical::HARMEAN);
    ops.put("HYPGEOM.DIST", Statistical::HYPGEOM_DIST);
    ops.put("INTERCEPT", Statistical::INTERCEPT);
    ops.put("KURT", Statistical::KURT);
    ops.put("LARGE", Statistical::LARGE);
    ops.put("LINEST", Statistical::LINEST);
    ops.put("LOGEST", Statistical::LOGEST);
    ops.put("LOGNORM.DIST", Statistical::LOGNORM_DIST);
    ops.put("LOGNORM.INV", Statistical::LOGNORM_INV);
    ops.put("MAX", Statistical::MAX);
    ops.put("MAXA", Statistical::MAXA);
    ops.put("MAXIFS", Statistical::MAXIFS);
    ops.put("MEDIAN", Statistical::MEDIAN);
    ops.put("MIN", Statistical::MIN);
    ops.put("MINA", Statistical::MINA);
    ops.put("MINIFS", Statistical::MINIFS);
    ops.put("MODE.MULT", Statistical::MODE_MULT);
    ops.put("MODE.SNGL", Statistical::MODE_SNGL);
    ops.put("NEGBINOM.DIST", Statistical::NEGBINOM_DIST);
    ops.put("NORM.DIST", Statistical::NORM_DIST);
    ops.put("NORM.INV", Statistical::NORM_INV);
    ops.put("NORM.S.DIST", Statistical::NORM_S_DIST);
    ops.put("NORM.S.INV", Statistical::NORM_S_INV);
    ops.put("PEARSON", Statistical::PEARSON);
    ops.put("PERCENTILE.EXC", Statistical::PERCENTILE_EXC);
    ops.put("PERCENTILE.INC", Statistical::PERCENTILE_INC);
    ops.put("PERCENTRANK.EXC", Statistical::PERCENTRANK_EXC);
    ops.put("PERCENTRANK.INC", Statistical::PERCENTRANK_INC);
    ops.put("PERMUT", Statistical::PERMUT);
    ops.put("PERMUTATIONA", Statistical::PERMUTATIONA);
    ops.put("PHI", Statistical::PHI);
    ops.put("POISSON.DIST", Statistical::POISSON_DIST);
    ops.put("PROB", Statistical::PROB);
    ops.put("QUARTILE.EXC", Statistical::QUARTILE_EXC);
    ops.put("QUARTILE.INC", Statistical::QUARTILE_INC);
    ops.put("RANK.AVG", Statistical::RANK_AVG);
    ops.put("RANK.EQ", Statistical::RANK_EQ);
    ops.put("ROW", Statistical::ROW);
    ops.put("RSQ", Statistical::RSQ);
    ops.put("SKEW", Statistical::SKEW);
    ops.put("SKEW.P", Statistical::SKEW_P);
    ops.put("SLOPE", Statistical::SLOPE);
    ops.put("SMALL", Statistical::SMALL);
    ops.put("STANDARDIZE", Statistical::STANDARDIZE);
    ops.put("STDEV.P", Statistical::STDEV_P);
    ops.put("STDEV.S", Statistical::STDEV_S);
    ops.put("STDEVA", Statistical::STDEVA);
    ops.put("STDEVPA", Statistical::STDEVPA);
    ops.put("STEYX", Statistical::STEYX);
    ops.put("T.DIST", Statistical::T_DIST);
    ops.put("T.DIST.2T", Statistical::T_DIST_2T);
    ops.put("T.DIST.RT", Statistical::T_DIST_RT);
    ops.put("T.INV", Statistical::T_INV);
    ops.put("T.INV.2T", Statistical::T_INV_2T);
    ops.put("T.TEST", Statistical::T_TEST);
    ops.put("TREND", Statistical::TREND);
    ops.put("TRIMMEAN", Statistical::TRIMMEAN);
    ops.put("VAR.P", Statistical::VAR_P);
    ops.put("VAR.S", Statistical::VAR_S);
    ops.put("VARA", Statistical::VARA);
    ops.put("VARPA", Statistical::VARPA);
    ops.put("WEIBULL.DIST", Statistical::WEIBULL_DIST);
    ops.put("Z.TEST", Statistical::Z_TEST);
  }
}
