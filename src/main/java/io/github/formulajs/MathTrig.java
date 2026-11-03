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
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.CriteriaEval;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/** MathTrig functions. */
public final class MathTrig {
  /** V8's stack depth, in FACT frames, for a call from the command (measured on the reference). */
  private static final int FACT_FRAMES = 6865;

  /** V8's stack depth, in FACTDOUBLE frames, for a call from the command (measured on the reference). */
  private static final int FACTDOUBLE_FRAMES = 7846;

  private static final List<Double> MEMOIZED_FACT = new ArrayList<>(List.of(1.0, 1.0));

  private static final String[] ROMAN_KEY = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM", "", "X",
    "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC", "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

  private MathTrig() {}

  private interface Unary {
    Object apply(double x);
  }

  private interface Binary {
    Object apply(double a, double b);
  }

  private static Object unary(Object[] args, Unary f) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    return number instanceof JsError ? number : f.apply((Double) number);
  }

  private static Object binary(Object[] args, Binary f) {
    Object a = Common.parseNumber(Js.arg(args, 0));
    Object b = Common.parseNumber(Js.arg(args, 1));
    Object error = Common.anyError(a, b);
    return error != Js.UNDEFINED ? error : f.apply((Double) a, (Double) b);
  }

  private static Object orNum(double result) {
    return Double.isNaN(result) ? Errors.NUM : (Object) result;
  }

  private static boolean truthy(double x) {
    return x != 0 && !Double.isNaN(x);
  }

  private static JsException stackOverflow() {
    return JsException.rangeError("Maximum call stack size exceeded");
  }

  /** {@code new Array(count).join(s) + suffix}. */
  private static String joinRepeat(double count, String s, String suffix) {
    Js.checkArrayLength(count);
    long n = count > 0 ? (long) count - 1 : 0;
    Js.checkStringLength(n);
    Js.checkStringLength(n + suffix.length());
    return s.repeat((int) n) + suffix;
  }

  /** Whether {@code value} meets {@code criteria} as criteria-eval sees it. */
  static boolean criteriaMet(Object value, Object criteria) {
    List<CriteriaEval.Token> tokens = new ArrayList<>();
    tokens.add(CriteriaEval.createToken(value, CriteriaEval.TOKEN_TYPE_LITERAL));
    tokens.addAll(CriteriaEval.parse(Js.str(Js.add(criteria, ""))));
    return CriteriaEval.compute(tokens);
  }

  /** Rounds a number up, to the nearest integer or to the nearest multiple of significance. */
  public static Object CEILING_MATH(Object... args) {
    Object number = Js.arg(args, 0);
    Object significance = Js.arg(args, 1);
    if (significance == Js.UNDEFINED) {
      significance = Js.gt(number, 0.0) ? 1.0 : -1.0;
    }
    return roundMath(number, significance, Js.arg(args, 2, 0.0), true);
  }

  /** Rounds a number up, to the nearest integer or to the nearest multiple of significance. */
  public static Object CEILING_PRECISE(Object... args) {
    return CEILING_MATH(Js.arg(args, 0), Js.arg(args, 1));
  }

  /** Rounds a number down, to the nearest integer or to the nearest multiple of significance. */
  public static Object FLOOR_MATH(Object... args) {
    return roundMath(Js.arg(args, 0), Js.arg(args, 1, 1.0), Js.arg(args, 2, 0.0), false);
  }

  /** Rounds a number the nearest integer or to the nearest multiple of significance. */
  public static Object FLOOR_PRECISE(Object... args) {
    return FLOOR_MATH(Js.arg(args, 0), Js.arg(args, 1));
  }

  private static Object roundMath(Object number, Object significance, Object mode, boolean up) {
    Object n = Common.parseNumber(number);
    Object s = Common.parseNumber(significance);
    Object m = Common.parseNumber(mode);
    Object error = Common.anyError(n, s, m);
    if (error != Js.UNDEFINED) {
      return error;
    }
    double x = (Double) n;
    double sig = (Double) s;
    if (sig == 0) {
      return 0.0;
    }
    sig = Math.abs(sig);
    boolean ceil = (Double) m == 0 || x > 0 ? up : !up;
    return (ceil ? Math.ceil(x / sig) : Math.floor(x / sig)) * sig;
  }

  /** Returns the absolute value of a number. */
  public static Object ABS(Object... args) {
    return unary(args, x -> Math.abs(x));
  }

  /** Returns the arccosine of a number. */
  public static Object ACOS(Object... args) {
    return unary(args, x -> orNum(StrictMath.acos(x)));
  }

  /** Returns the inverse hyperbolic cosine of a number. */
  public static Object ACOSH(Object... args) {
    return unary(args, x -> orNum(StrictMath.log(x + Math.sqrt(x * x - 1))));
  }

  /** Returns the arccotangent of a number. */
  public static Object ACOT(Object... args) {
    return unary(args, x -> JsMath.atan(1 / x));
  }

  /** Returns the hyperbolic arccotangent of a number. */
  public static Object ACOTH(Object... args) {
    return unary(args, x -> orNum(0.5 * StrictMath.log((x + 1) / (x - 1))));
  }

  /** Returns an aggregate in a list or database. */
  public static Object AGGREGATE(Object... args) {
    Object functionNum = Common.parseNumber(Js.arg(args, 0));
    Object options = Common.parseNumber(functionNum);
    if (Common.anyIsError(functionNum, options)) {
      return Errors.VALUE;
    }
    return aggregate((Double) functionNum, Js.arg(args, 2), Js.arg(args, 3));
  }

  private static Object aggregate(double fn, Object ref1, Object ref2) {
    if (fn != Math.floor(fn) || fn < 1 || fn > 19) {
      return Js.UNDEFINED;
    }
    return switch ((int) fn) {
      case 1 -> Statistical.AVERAGE(ref1);
      case 2 -> Statistical.COUNT(ref1);
      case 3 -> Statistical.COUNTA(ref1);
      case 4 -> Statistical.MAX(ref1);
      case 5 -> Statistical.MIN(ref1);
      case 6 -> PRODUCT(ref1);
      case 7 -> Statistical.STDEV_S(ref1);
      case 8 -> Statistical.STDEV_P(ref1);
      case 9 -> SUM(ref1);
      case 10 -> Statistical.VAR_S(ref1);
      case 11 -> Statistical.VAR_P(ref1);
      case 12 -> Statistical.MEDIAN(ref1);
      case 13 -> Statistical.MODE_SNGL(ref1);
      case 14 -> Statistical.LARGE(ref1, ref2);
      case 15 -> Statistical.SMALL(ref1, ref2);
      case 16 -> Statistical.PERCENTILE_INC(ref1, ref2);
      case 17 -> Statistical.QUARTILE_INC(ref1, ref2);
      case 18 -> Statistical.PERCENTILE_EXC(ref1, ref2);
      default -> Statistical.QUARTILE_EXC(ref1, ref2);
    };
  }

  /** Converts a Roman number to Arabic, as a number. */
  public static Object ARABIC(Object... args) {
    Object text = Js.arg(args, 0);
    if (Js.isNullish(text)) {
      return 0.0;
    }
    if (text instanceof JsError) {
      return text;
    }
    String s = Js.str(text);
    if (!isRoman(s)) {
      return Errors.VALUE;
    }
    if (!(text instanceof String)) {
      throw JsException.notAFunction("text.replace");
    }
    double r = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
      int pair = "CMCDXCXLIXIV".indexOf("" + c + next);
      if (pair >= 0 && pair % 2 == 0) {
        r += new int[] {900, 0, 400, 0, 90, 0, 40, 0, 9, 0, 4}[pair];
        i++;
      } else {
        r += new int[] {1000, 500, 100, 50, 10, 5, 1}["MDCLXVI".indexOf(c)];
      }
    }
    return r;
  }

  /** {@code /^M*(?:D?C{0,3}|C[MD])(?:L?X{0,3}|X[CL])(?:V?I{0,3}|I[XV])$/.test(s)}. */
  private static boolean isRoman(String s) {
    int start = 0;
    while (start < s.length() && s.charAt(start) == 'M') {
      start++;
    }
    String t = s.substring(start);
    if (t.length() > 12) {
      return false;
    }
    int ends = romanGroup(t, 1, 'C', 'D', 'M');
    ends = romanGroup(t, ends, 'X', 'L', 'C');
    ends = romanGroup(t, ends, 'I', 'V', 'X');
    return (ends >> t.length() & 1) != 0;
  }

  /** The end positions of {@code (?:F?O{0,3}|O[TF])} from each start position (bit sets). */
  private static int romanGroup(String t, int starts, char one, char five, char ten) {
    int ends = 0;
    int len = t.length();
    for (int p = 0; p <= len; p++) {
      if ((starts >> p & 1) == 0) {
        continue;
      }
      ends |= 1 << p;
      for (int q = p; q - p < 3 && q < len && t.charAt(q) == one; q++) {
        ends |= 1 << (q + 1);
      }
      if (p < len && t.charAt(p) == five) {
        ends |= 1 << (p + 1);
        for (int q = p + 1; q - p - 1 < 3 && q < len && t.charAt(q) == one; q++) {
          ends |= 1 << (q + 1);
        }
      }
      if (p + 1 < len && t.charAt(p) == one && (t.charAt(p + 1) == ten || t.charAt(p + 1) == five)) {
        ends |= 1 << (p + 2);
      }
    }
    return ends;
  }

  /** Returns the arcsine of a number. */
  public static Object ASIN(Object... args) {
    return unary(args, x -> orNum(StrictMath.asin(x)));
  }

  /** Returns the inverse hyperbolic sine of a number. */
  public static Object ASINH(Object... args) {
    return unary(args, x -> StrictMath.log(x + Math.sqrt(x * x + 1)));
  }

  /** Returns the arctangent of a number. */
  public static Object ATAN(Object... args) {
    return unary(args, x -> JsMath.atan(x));
  }

  /** Returns the arctangent from x- and y-coordinates. */
  public static Object ATAN2(Object... args) {
    return binary(args, (x, y) -> JsMath.atan2(x, y));
  }

  /** Returns the inverse hyperbolic tangent of a number. */
  public static Object ATANH(Object... args) {
    return unary(args, x -> orNum(StrictMath.log((1 + x) / (1 - x)) / 2));
  }

  /** Converts a number into a text representation with the given radix (base). */
  public static Object BASE(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    Object radix = Common.parseNumber(Js.arg(args, 1));
    Object minLength = Common.parseNumber(Js.arg(args, 2));
    Object error = Common.anyError(number, radix, minLength);
    if (error != Js.UNDEFINED) {
      return error;
    }
    if ((Double) radix == 0) {
      return Errors.NUM;
    }
    double r = Js.toIntegerOrInfinity((Double) radix);
    if (r < 2 || r > 36) {
      throw JsException.rangeError("toString() radix argument must be between 2 and 36");
    }
    String result = Js.toRadixString((Double) number, (int) r);
    return joinRepeat(Math.max((Double) minLength + 1 - result.length(), 0), "0", result);
  }

  /** Rounds a number to the nearest integer or to the nearest multiple of significance. */
  public static Object CEILING(Object... args) {
    return binary(args, (number, significance) -> {
      if (significance == 0) {
        return 0.0;
      }
      if (number > 0 && significance < 0) {
        return Errors.NUM;
      }
      return Math.ceil(number / significance) * significance;
    });
  }

  /** Returns the number of combinations for a given number of objects. */
  public static Object COMBIN(Object... args) {
    return combin(args, 1);
  }

  private static Object combin(Object[] args, int frames) {
    return binary(args, (number, chosen) -> {
      if (number < chosen) {
        return Errors.NUM;
      }
      return Js.toNumber(fact(number, frames))
          / (Js.toNumber(fact(chosen, frames)) * Js.toNumber(fact(number - chosen, frames)));
    });
  }

  /** Returns the number of combinations with repetitions for a given number of items. */
  public static Object COMBINA(Object... args) {
    return binary(args, (number, chosen) -> {
      if (number < chosen) {
        return Errors.NUM;
      }
      return number == 0 && chosen == 0 ? 1.0 : combin(new Object[] {number + chosen - 1, number - 1}, 2);
    });
  }

  /** Returns the cosine of a number. */
  public static Object COS(Object... args) {
    return unary(args, x -> StrictMath.cos(x));
  }

  /** Returns the hyperbolic cosine of a number. */
  public static Object COSH(Object... args) {
    return unary(args, x -> (JsMath.exp(x) + JsMath.exp(-x)) / 2);
  }

  /** Returns the hyperbolic cosine of a number. */
  public static Object COT(Object... args) {
    return unary(args, x -> x == 0 ? Errors.DIV0 : (Object) (1 / StrictMath.tan(x)));
  }

  /** Returns the cotangent of an angle. */
  public static Object COTH(Object... args) {
    return unary(args, x -> {
      if (x == 0) {
        return Errors.DIV0;
      }
      double e2 = JsMath.exp(2 * x);
      return (e2 + 1) / (e2 - 1);
    });
  }

  /** Returns the cosecant of an angle. */
  public static Object CSC(Object... args) {
    return unary(args, x -> x == 0 ? Errors.DIV0 : (Object) (1 / StrictMath.sin(x)));
  }

  /** Returns the hyperbolic cosecant of an angle. */
  public static Object CSCH(Object... args) {
    return unary(args, x -> x == 0 ? Errors.DIV0 : (Object) (2 / (JsMath.exp(x) - JsMath.exp(-x))));
  }

  /** Converts a text representation of a number in a given base into a decimal number. */
  public static Object DECIMAL(Object... args) {
    if (args.length < 2) {
      return Errors.NA;
    }
    Object text = Js.truthy(args[0]) ? args[0] : "0";
    Object radix = Common.parseNumber(args[1]);
    Object error = Common.anyError(text, radix);
    if (error != Js.UNDEFINED) {
      return error;
    }
    if ((Double) radix == 0) {
      return Errors.NUM;
    }
    return orNum(Js.parseInt(text, radix));
  }

  /** Converts radians to degrees. */
  public static Object DEGREES(Object... args) {
    return unary(args, x -> (x * 180) / Math.PI);
  }

  /** Rounds a number up to the nearest even integer. */
  public static Object EVEN(Object... args) {
    return unary(args, x -> CEILING_MATH(x, -2.0, -1.0));
  }

  /** Returns e raised to the power of a given number. */
  public static Object EXP(Object... args) {
    if (args.length < 1) {
      return Errors.NA;
    }
    if (args.length > 1) {
      return Errors.ERROR;
    }
    return unary(args, x -> JsMath.exp(x));
  }

  /** Returns the factorial of a number. */
  public static Object FACT(Object... args) {
    return fact(Js.arg(args, 0), 0);
  }

  /** FACT with its memo; {@code frames} is the stack its callers already use. */
  private static Object fact(Object number, int frames) {
    Object parsed = Common.parseNumber(number);
    if (parsed instanceof JsError) {
      return parsed;
    }
    double n = Math.floor((Double) parsed);
    if (n == 0 || n == 1) {
      return 1.0;
    }
    if (Double.isNaN(n)) {
      return Double.NaN;
    }
    int top = MEMOIZED_FACT.size() - 1;
    if (n >= 2 && n <= top) {
      return MEMOIZED_FACT.get((int) n);
    }
    if (!(n > top) || n - top + 1 + frames > FACT_FRAMES) {
      throw stackOverflow();
    }
    for (int k = top + 1; k <= n; k++) {
      MEMOIZED_FACT.add(MEMOIZED_FACT.get(k - 1) * k);
    }
    return MEMOIZED_FACT.get((int) n);
  }

  /** Returns the double factorial of a number. */
  public static Object FACTDOUBLE(Object... args) {
    return unary(args, number -> {
      double n = Math.floor(number);
      if (Double.isNaN(n) || n <= 0) {
        return Double.isNaN(n) ? n : 1.0;
      }
      if (Math.floor((n + 1) / 2) + 1 > FACTDOUBLE_FRAMES) {
        throw stackOverflow();
      }
      double result = 1;
      for (double m = n % 2 == 0 ? 2 : 1; m <= n; m += 2) {
        result = m * result;
      }
      return result;
    });
  }

  /** Rounds a number down, toward zero. */
  public static Object FLOOR(Object... args) {
    return binary(args, (number, significance) -> {
      if (!truthy(significance)) {
        return Errors.DIV0;
      }
      if (number > 0 && significance < 0) {
        return Errors.NUM;
      }
      return Math.floor(number / significance) * significance;
    });
  }

  /** Returns the greatest common divisor. */
  public static Object GCD(Object... args) {
    Object range = Common.parseNumberArray(Common.flatten(Common.arguments(args)));
    if (range instanceof JsError) {
      return range;
    }
    List<Object> list = Js.asList(range);
    double r0 = (Double) list.get(0);
    double x = r0 < 0 ? -r0 : r0;
    for (int i = 1; i < list.size(); i++) {
      double ri = (Double) list.get(i);
      double y = ri < 0 ? -ri : ri;
      while (truthy(x) && truthy(y)) {
        if (x > y) {
          x %= y;
        } else {
          y %= x;
        }
      }
      x += y;
    }
    return x;
  }

  /** Rounds a number down to the nearest integer. */
  public static Object INT(Object... args) {
    return unary(args, x -> Math.floor(x));
  }

  /** Returns the least common multiple. */
  public static Object LCM(Object... args) {
    Object parsed = Common.parseNumberArray(Common.flatten(Common.arguments(args)));
    if (parsed instanceof JsError) {
      return parsed;
    }
    List<Object> o = Js.asList(parsed);
    double r = 1;
    while (!o.isEmpty()) {
      double n = (Double) o.remove(o.size() - 1);
      if (n == 0) {
        return 0.0;
      }
      while (n > 1) {
        double d;
        if (truthy(n % 2)) {
          double i = 3;
          double j = Math.floor(Math.sqrt(n));
          while (i <= j && truthy(n % i)) {
            i += 2;
          }
          d = i <= j ? i : n;
        } else {
          d = 2;
        }
        n /= d;
        r *= d;
        for (int i = o.size(); i != 0; ) {
          i--;
          double v = (Double) o.get(i);
          if (v % d == 0) {
            o.set(i, v / d);
            if (v / d == 1) {
              o.remove(i);
            }
          }
        }
      }
    }
    return r;
  }

  /** Returns the natural logarithm of a number. */
  public static Object LN(Object... args) {
    return unary(args, x -> x == 0 ? Errors.NUM : (Object) StrictMath.log(x));
  }

  /** Returns the logarithm of a number to a specified base. */
  public static Object LOG(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    Object base = Js.truthy(Js.arg(args, 1)) ? Common.parseNumber(Js.arg(args, 1)) : 10.0;
    Object error = Common.anyError(number, base);
    if (error != Js.UNDEFINED) {
      return error;
    }
    if ((Double) number == 0 || (Double) base == 0) {
      return Errors.NUM;
    }
    return StrictMath.log((Double) number) / StrictMath.log((Double) base);
  }

  /** Returns the base-10 logarithm of a number. */
  public static Object LOG10(Object... args) {
    return unary(args, x -> x == 0 ? Errors.NUM : (Object) (StrictMath.log(x) / StrictMath.log(10)));
  }

  /** Returns the matrix product of two arrays. */
  public static Object MMULT(Object... args) {
    Object array1 = Js.arg(args, 0);
    Object array2 = Js.arg(args, 1);
    if (!(array1 instanceof List<?> l1) || !(array2 instanceof List<?> l2) || anyEmpty(l1) || anyEmpty(l2)
        || anyNonNumber(Js.asList(l1)) || anyNonNumber(Js.asList(l2))
        || !Js.strictEquals(Js.length(l1.get(0)), (double) l2.size())) {
      return Errors.VALUE;
    }
    double columns = Js.toNumber(Js.length(l2.get(0)));
    List<Object> matrix = new ArrayList<>();
    for (Object rowObject : l1) {
      List<Object> row = Js.asList(rowObject);
      List<Object> out = new ArrayList<>();
      for (int j = 0; j < columns; j++) {
        double sum = 0;
        for (int k = 0; k < row.size(); k++) {
          sum = sum + (Double) row.get(k) * Js.toNumber(Js.get(Js.get(l2, (double) k), (double) j));
        }
        out.add(sum);
      }
      matrix.add(out);
    }
    return matrix;
  }

  private static boolean anyEmpty(List<?> list) {
    for (Object el : list) {
      if (!Js.truthy(Js.length(el))) {
        return true;
      }
    }
    return false;
  }

  private static boolean anyNonNumber(List<Object> list) {
    if (list.size() == 1 && !(list.get(0) instanceof List)) {
      throw JsException.notAFunction("utils.flattenShallow(...).some");
    }
    for (Object el : Common.flattenShallow(list)) {
      if (!(el instanceof Double)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the remainder from division. */
  public static Object MOD(Object... args) {
    return binary(args, (number, divisor) -> {
      if (divisor == 0) {
        return Errors.DIV0;
      }
      double modulus = Math.abs(number % divisor);
      modulus = number < 0 ? divisor - modulus : modulus;
      return divisor > 0 ? modulus : -modulus;
    });
  }

  /** Returns a number rounded to the desired multiple. */
  public static Object MROUND(Object... args) {
    return binary(args, (number, multiple) -> {
      if (number * multiple == 0) {
        return 0.0;
      }
      if (number * multiple < 0) {
        return Errors.NUM;
      }
      return Js.round(number / multiple) * multiple;
    });
  }

  /** Returns the multinomial of a set of numbers. */
  public static Object MULTINOMIAL(Object... args) {
    Object parsed = Common.parseNumberArray(Common.flatten(Common.arguments(args)));
    if (parsed instanceof JsError) {
      return parsed;
    }
    double sum = 0;
    double divisor = 1;
    for (Object v : Js.asList(parsed)) {
      sum += (Double) v;
      divisor *= Js.toNumber(fact(v, 1));
    }
    return Js.toNumber(fact(sum, 1)) / divisor;
  }

  /** Returns the unit matrix for the specified dimension. */
  public static Object MUNIT(Object... args) {
    if (args.length > 1) {
      return Errors.NA;
    }
    double dimension = Js.parseInt(Js.arg(args, 0), Js.UNDEFINED);
    if (!truthy(dimension) || dimension <= 0) {
      return Errors.VALUE;
    }
    Js.checkArrayLength(dimension);
    List<Object> matrix = new ArrayList<>();
    for (int i = 0; i < dimension; i++) {
      List<Object> row = new ArrayList<>(Collections.nCopies((int) dimension, (Object) 0.0));
      row.set(i, 1.0);
      matrix.add(row);
    }
    return matrix;
  }

  /** Rounds a number up to the nearest odd integer. */
  public static Object ODD(Object... args) {
    return unary(args, number -> {
      double temp = Math.ceil(Math.abs(number));
      temp = (Js.toInt32(temp) & 1) != 0 ? temp : temp + 1;
      return number >= 0 ? temp : -temp;
    });
  }

  /** Returns the value of pi. */
  public static Object PI(Object... args) {
    return Math.PI;
  }

  /** Returns the result of a number raised to a power. */
  public static Object POWER(Object... args) {
    return binary(args, (number, power) -> number == 0 && power == 0 ? Errors.NUM : orNum(Js.pow(number, power)));
  }

  /** Multiplies its arguments. */
  public static Object PRODUCT(Object... args) {
    List<Object> defined = new ArrayList<>();
    for (Object arg : Common.flatten(Common.arguments(args))) {
      if (!Js.isNullish(arg)) {
        defined.add(arg);
      }
    }
    if (defined.isEmpty()) {
      return 0.0;
    }
    Object parsed = Common.parseNumberArray(defined);
    if (parsed instanceof JsError) {
      return parsed;
    }
    double result = 1;
    for (Object v : defined) {
      result *= (Double) v;
    }
    return result;
  }

  /** Returns the integer portion of a division. */
  public static Object QUOTIENT(Object... args) {
    return binary(args, (numerator, denominator) -> Js.parseInt(numerator / denominator, 10.0));
  }

  /** Converts degrees to radians. */
  public static Object RADIANS(Object... args) {
    return unary(args, x -> (x * Math.PI) / 180);
  }

  /** Converts an arabic numeral to roman, as text. */
  public static Object ROMAN(Object... args) {
    return unary(args, number -> {
      List<Object> digits = Js.split(Js.numberToString(number), "");
      String roman = "";
      for (int i = 2; i >= 0; i--) {
        Object digit = digits.isEmpty() ? Js.UNDEFINED : digits.remove(digits.size() - 1);
        double index = Js.toNumber(digit) + i * 10;
        roman = (index >= 0 && index < 30 && index == Math.floor(index) ? ROMAN_KEY[(int) index] : "") + roman;
      }
      return joinRepeat(Js.toNumber(Js.join(digits, "")) + 1, "M", roman);
    });
  }

  private static Object roundBase(Object[] args, DoubleUnaryOperator roundFn) {
    return binary(args, (number, digits) -> {
      double sign = number >= 0 ? 1 : -1;
      double val = Math.abs(number);
      List<Object> pair = Js.split(Js.numberToString(val) + "e" + Js.numberToString(digits), "e");
      double shifted = roundFn.applyAsDouble(Js.toNumber(pair.get(0) + "e" + pair.get(1)));
      pair = Js.split(Js.numberToString(shifted) + "e" + Js.numberToString(-digits), "e");
      return Js.toNumber(pair.get(0) + "e" + pair.get(1)) * sign;
    });
  }

  /** Rounds a number to a specified number of digits. */
  public static Object ROUND(Object... args) {
    return roundBase(args, Js::round);
  }

  /** Rounds a number down, toward zero. */
  public static Object ROUNDDOWN(Object... args) {
    return roundBase(args, Math::floor);
  }

  /** Rounds a number up, away from zero. */
  public static Object ROUNDUP(Object... args) {
    return roundBase(args, Math::ceil);
  }

  /** Returns the secant of an angle. */
  public static Object SEC(Object... args) {
    return unary(args, x -> 1 / StrictMath.cos(x));
  }

  /** Returns the hyperbolic secant of an angle. */
  public static Object SECH(Object... args) {
    return unary(args, x -> 2 / (JsMath.exp(x) + JsMath.exp(-x)));
  }

  /** Returns the sum of a power series based on the formula. */
  public static Object SERIESSUM(Object... args) {
    Object x = Common.parseNumber(Js.arg(args, 0));
    Object n = Common.parseNumber(Js.arg(args, 1));
    Object m = Common.parseNumber(Js.arg(args, 2));
    Object coefficients = Common.parseNumberArray(Js.arg(args, 3));
    if (Common.anyIsError(x, n, m, coefficients)) {
      return Errors.VALUE;
    }
    double result = Js.toNumber(Js.get(coefficients, 0.0)) * Js.pow((Double) x, (Double) n);
    for (int i = 1; i < Js.toNumber(Js.length(coefficients)); i++) {
      result += Js.toNumber(Js.get(coefficients, (double) i)) * Js.pow((Double) x, (Double) n + i * (Double) m);
    }
    return result;
  }

  /** Returns the sign of a number. */
  public static Object SIGN(Object... args) {
    return unary(args, x -> x < 0 ? -1.0 : x == 0 ? 0.0 : 1.0);
  }

  /** Returns the sine of the given angle. */
  public static Object SIN(Object... args) {
    return unary(args, x -> StrictMath.sin(x));
  }

  /** Returns the hyperbolic sine of a number. */
  public static Object SINH(Object... args) {
    return unary(args, x -> (JsMath.exp(x) - JsMath.exp(-x)) / 2);
  }

  /** Returns a positive square root. */
  public static Object SQRT(Object... args) {
    return unary(args, x -> x < 0 ? Errors.NUM : (Object) Math.sqrt(x));
  }

  /** Returns the square root of (number * pi). */
  public static Object SQRTPI(Object... args) {
    return unary(args, x -> Math.sqrt(x * Math.PI));
  }

  /** Returns a subtotal in a list or database. */
  public static Object SUBTOTAL(Object... args) {
    Object functionNum = Common.parseNumber(Js.arg(args, 0));
    if (functionNum instanceof JsError) {
      return functionNum;
    }
    double f = (Double) functionNum;
    if (f >= 101 && f <= 111) {
      f -= 100;
    }
    return f > 11 ? Js.UNDEFINED : aggregate(f, Js.arg(args, 1), Js.UNDEFINED);
  }

  /** Adds its arguments. */
  public static Object SUM(Object... args) {
    Object result = 0.0;
    for (Object value : args) {
      if (result instanceof JsError) {
        break;
      } else if (value instanceof JsError) {
        result = value;
      } else if (value instanceof Double d) {
        result = (Double) result + d;
      } else if (value instanceof String s) {
        double parsed = Js.parseFloat(s);
        if (!Double.isNaN(parsed)) {
          result = (Double) result + parsed;
        }
      } else if (value instanceof List<?> list) {
        Object inner = SUM(list.toArray());
        result = inner instanceof JsError ? inner : (Object) ((Double) result + (Double) inner);
      }
    }
    return result;
  }

  /** Adds the values specified by a given criteria. */
  public static Object SUMIF(Object... args) {
    Object criteria = Js.arg(args, 1);
    List<Object> range = Common.flatten(Js.arg(args, 0));
    List<Object> sumRange = Js.truthy(Js.arg(args, 2)) ? Common.flatten(Js.arg(args, 2)) : range;
    if (Js.isNullish(criteria) || criteria instanceof JsError) {
      return 0.0;
    }
    Object result = 0.0;
    boolean isWildcard = "*".equals(criteria);
    for (int i = 0; i < range.size(); i++) {
      Object value = range.get(i);
      if (isWildcard) {
        result = Js.add(result, value);
      } else {
        result = Js.add(result, criteriaMet(value, criteria) ? Js.get(sumRange, (double) i) : 0.0);
      }
    }
    return result;
  }

  /** Adds the values in a range that meet multiple criteria. */
  public static Object SUMIFS(Object... args) {
    List<Object> list = new ArrayList<>(Arrays.asList(args));
    Object first = Common.arrayValuesToNumbers(Common.flatten(Js.arg(args, 0)));
    if (list.isEmpty()) {
      list.add(first);
    } else {
      list.set(0, first);
    }
    return SUM(Common.applyCriteria(list.toArray()));
  }

  /** Returns the sum of the products of corresponding array components. */
  public static Object SUMPRODUCT(Object... args) {
    if (args.length == 0) {
      return Errors.VALUE;
    }
    double result = 0;
    for (int i = 0; i < Js.toNumber(Js.length(args[0])); i++) {
      Object item = Js.get(args[0], (double) i);
      int count = item instanceof List<?> inner ? inner.size() : 1;
      for (int j = 0; j < count; j++) {
        double product = 1;
        for (Object array : args) {
          Object value = Js.get(array, (double) i);
          Object parsed = Common.parseNumber(item instanceof List ? Js.get(value, (double) j) : value);
          if (parsed instanceof JsError) {
            return parsed;
          }
          product *= (Double) parsed;
        }
        result += product;
      }
    }
    return result;
  }

  /** Returns the sum of the squares of the arguments. */
  public static Object SUMSQ(Object... args) {
    Object numbers = Common.parseNumberArray(Common.flatten(Common.arguments(args)));
    if (numbers instanceof JsError) {
      return numbers;
    }
    double result = 0;
    for (Object v : Js.asList(numbers)) {
      result += (Boolean) Information.ISNUMBER(v) ? (Double) v * (Double) v : 0;
    }
    return result;
  }

  /** Returns the sum of the difference of squares of corresponding values in two arrays. */
  public static Object SUMX2MY2(Object... args) {
    return sumX(args, 0);
  }

  /** Returns the sum of the sum of squares of corresponding values in two arrays. */
  public static Object SUMX2PY2(Object... args) {
    return sumX(args, 1);
  }

  /** Returns the sum of squares of differences of corresponding values in two arrays. */
  public static Object SUMXMY2(Object... args) {
    return sumX(args, 2);
  }

  private static Object sumX(Object[] args, int kind) {
    Object x = Common.parseNumberArray(Common.flatten(Js.arg(args, 0)));
    Object y = Common.parseNumberArray(Common.flatten(Js.arg(args, 1)));
    if (Common.anyIsError(x, y)) {
      return Errors.VALUE;
    }
    if (kind == 1) {
      x = Common.parseNumberArray(Common.flatten(x));
      y = Common.parseNumberArray(Common.flatten(y));
    } else if (kind == 2) {
      x = Common.flatten(x);
      y = Common.flatten(y);
    }
    double result = 0;
    for (int i = 0; i < Js.toNumber(Js.length(x)); i++) {
      double xi = Js.toNumber(Js.get(x, (double) i));
      double yi = Js.toNumber(Js.get(y, (double) i));
      result += kind == 0 ? xi * xi - yi * yi : kind == 1 ? xi * xi + yi * yi : Js.pow(xi - yi, 2);
    }
    return result;
  }

  /** Returns the tangent of a number. */
  public static Object TAN(Object... args) {
    return unary(args, x -> StrictMath.tan(x));
  }

  /** Returns the hyperbolic tangent of a number. */
  public static Object TANH(Object... args) {
    return unary(args, x -> {
      double e2 = JsMath.exp(2 * x);
      return (e2 - 1) / (e2 + 1);
    });
  }

  /** Truncates a number to an integer. */
  public static Object TRUNC(Object... args) {
    return binary(args, (number, digits) -> {
      double sign = number > 0 ? 1 : -1;
      return (sign * Math.floor(Math.abs(number) * Js.pow(10, digits))) / Js.pow(10, digits);
    });
  }

  static void register(Map<String, Fn> ops) {
    ops.put("ABS", MathTrig::ABS);
    ops.put("ACOS", MathTrig::ACOS);
    ops.put("ACOSH", MathTrig::ACOSH);
    ops.put("ACOT", MathTrig::ACOT);
    ops.put("ACOTH", MathTrig::ACOTH);
    ops.put("AGGREGATE", MathTrig::AGGREGATE);
    ops.put("ARABIC", MathTrig::ARABIC);
    ops.put("ASIN", MathTrig::ASIN);
    ops.put("ASINH", MathTrig::ASINH);
    ops.put("ATAN", MathTrig::ATAN);
    ops.put("ATAN2", MathTrig::ATAN2);
    ops.put("ATANH", MathTrig::ATANH);
    ops.put("BASE", MathTrig::BASE);
    ops.put("CEILING", MathTrig::CEILING);
    ops.put("CEILING.MATH", MathTrig::CEILING_MATH);
    ops.put("CEILING.PRECISE", MathTrig::CEILING_PRECISE);
    ops.put("COMBIN", MathTrig::COMBIN);
    ops.put("COMBINA", MathTrig::COMBINA);
    ops.put("COS", MathTrig::COS);
    ops.put("COSH", MathTrig::COSH);
    ops.put("COT", MathTrig::COT);
    ops.put("COTH", MathTrig::COTH);
    ops.put("CSC", MathTrig::CSC);
    ops.put("CSCH", MathTrig::CSCH);
    ops.put("DECIMAL", MathTrig::DECIMAL);
    ops.put("DEGREES", MathTrig::DEGREES);
    ops.put("EVEN", MathTrig::EVEN);
    ops.put("EXP", MathTrig::EXP);
    ops.put("FACT", MathTrig::FACT);
    ops.put("FACTDOUBLE", MathTrig::FACTDOUBLE);
    ops.put("FLOOR", MathTrig::FLOOR);
    ops.put("FLOOR.MATH", MathTrig::FLOOR_MATH);
    ops.put("FLOOR.PRECISE", MathTrig::FLOOR_PRECISE);
    ops.put("GCD", MathTrig::GCD);
    ops.put("INT", MathTrig::INT);
    ops.put("ISO.CEILING", MathTrig::CEILING);
    ops.put("ISO.CEILING.MATH", MathTrig::CEILING_MATH);
    ops.put("ISO.CEILING.PRECISE", MathTrig::CEILING_PRECISE);
    ops.put("LCM", MathTrig::LCM);
    ops.put("LN", MathTrig::LN);
    ops.put("LOG", MathTrig::LOG);
    ops.put("LOG10", MathTrig::LOG10);
    ops.put("MMULT", MathTrig::MMULT);
    ops.put("MOD", MathTrig::MOD);
    ops.put("MROUND", MathTrig::MROUND);
    ops.put("MULTINOMIAL", MathTrig::MULTINOMIAL);
    ops.put("MUNIT", MathTrig::MUNIT);
    ops.put("ODD", MathTrig::ODD);
    ops.put("PI", MathTrig::PI);
    ops.put("POWER", MathTrig::POWER);
    ops.put("PRODUCT", MathTrig::PRODUCT);
    ops.put("QUOTIENT", MathTrig::QUOTIENT);
    ops.put("RADIANS", MathTrig::RADIANS);
    ops.put("ROMAN", MathTrig::ROMAN);
    ops.put("ROUND", MathTrig::ROUND);
    ops.put("ROUNDDOWN", MathTrig::ROUNDDOWN);
    ops.put("ROUNDUP", MathTrig::ROUNDUP);
    ops.put("SEC", MathTrig::SEC);
    ops.put("SECH", MathTrig::SECH);
    ops.put("SERIESSUM", MathTrig::SERIESSUM);
    ops.put("SIGN", MathTrig::SIGN);
    ops.put("SIN", MathTrig::SIN);
    ops.put("SINH", MathTrig::SINH);
    ops.put("SQRT", MathTrig::SQRT);
    ops.put("SQRTPI", MathTrig::SQRTPI);
    ops.put("SUBTOTAL", MathTrig::SUBTOTAL);
    ops.put("SUM", MathTrig::SUM);
    ops.put("SUMIF", MathTrig::SUMIF);
    ops.put("SUMIFS", MathTrig::SUMIFS);
    ops.put("SUMPRODUCT", MathTrig::SUMPRODUCT);
    ops.put("SUMSQ", MathTrig::SUMSQ);
    ops.put("SUMX2MY2", MathTrig::SUMX2MY2);
    ops.put("SUMX2PY2", MathTrig::SUMX2PY2);
    ops.put("SUMXMY2", MathTrig::SUMXMY2);
    ops.put("TAN", MathTrig::TAN);
    ops.put("TANH", MathTrig::TANH);
    ops.put("TRUNC", MathTrig::TRUNC);
  }
}
