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
package io.github.formulajs.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * The JavaScript semantics the library relies on. Values are modelled as: {@link Undefined#INSTANCE} (undefined),
 * {@code null}, {@link Boolean}, {@link Double} (every number), {@link String} (UTF-16, as in JavaScript), {@link List}
 * (arrays, mutable and shared by reference), {@link JsDate} and {@link JsError}.
 */
public final class Js {
  public static final Object UNDEFINED = Undefined.INSTANCE;

  private Js() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Types

  public static boolean isUndefined(Object v) {
    return v == UNDEFINED;
  }

  /** {@code v === undefined || v === null}. */
  public static boolean isNullish(Object v) {
    return v == null || v == UNDEFINED;
  }

  public static boolean isNumber(Object v) {
    return v instanceof Double;
  }

  public static boolean isString(Object v) {
    return v instanceof String;
  }

  public static boolean isBoolean(Object v) {
    return v instanceof Boolean;
  }

  public static boolean isArray(Object v) {
    return v instanceof List;
  }

  public static boolean isError(Object v) {
    return v instanceof JsError;
  }

  public static boolean isDate(Object v) {
    return v instanceof JsDate;
  }

  public static String typeOf(Object v) {
    if (v == UNDEFINED) {
      return "undefined";
    }
    if (v instanceof Double) {
      return "number";
    }
    if (v instanceof String) {
      return "string";
    }
    if (v instanceof Boolean) {
      return "boolean";
    }
    return "object";
  }

  /** A named parameter: the argument at this position, undefined when the call passed fewer. */
  public static Object arg(Object[] args, int index) {
    return index < args.length ? args[index] : UNDEFINED;
  }

  /** A parameter with a default value, which (as in JavaScript) also replaces an explicit undefined. */
  public static Object arg(Object[] args, int index, Object defaultValue) {
    Object v = arg(args, index);
    return v == UNDEFINED ? defaultValue : v;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object v) {
    return (List<Object>) v;
  }

  /** A new mutable array of the given elements. */
  public static List<Object> list(Object... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Conversions

  public static boolean truthy(Object v) {
    if (v == null || v == UNDEFINED) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof Double d) {
      return d != 0 && !Double.isNaN(d);
    }
    if (v instanceof String s) {
      return !s.isEmpty();
    }
    return true;
  }

  /** ToPrimitive: arrays, errors and (with no hint or a string hint) dates become strings, dates become numbers. */
  public static Object toPrimitive(Object v, boolean numberHint) {
    if (v instanceof JsDate d) {
      return numberHint ? (Object) d.getTime() : d.toString();
    }
    if (v instanceof List || v instanceof JsError) {
      return str(v);
    }
    return v;
  }

  public static double toNumber(Object v) {
    if (v instanceof Double d) {
      return d;
    }
    if (v == UNDEFINED) {
      return Double.NaN;
    }
    if (v == null) {
      return 0;
    }
    if (v instanceof Boolean b) {
      return b ? 1 : 0;
    }
    if (v instanceof String s) {
      return stringToNumber(s);
    }
    if (v instanceof JsDate d) {
      return d.getTime();
    }
    return toNumber(toPrimitive(v, true));
  }

  /** StringToNumber: JavaScript white space trimmed, decimal, Infinity and 0x/0o/0b literals. */
  public static double stringToNumber(String s) {
    String t = trim(s);
    if (t.isEmpty()) {
      return 0;
    }
    if (t.length() > 2 && t.charAt(0) == '0') {
      int radix = switch (t.charAt(1)) {
        case 'x', 'X' -> 16;
        case 'o', 'O' -> 8;
        case 'b', 'B' -> 2;
        default -> 0;
      };
      if (radix != 0) {
        String digits = t.substring(2);
        for (int i = 0; i < digits.length(); i++) {
          if (Character.digit(digits.charAt(i), radix) < 0 || digits.charAt(i) > 'z') {
            return Double.NaN;
          }
        }
        return new BigInteger(digits, radix).doubleValue();
      }
    }
    int end = decimalPrefix(t, 0);
    if (end != t.length()) {
      return Double.NaN;
    }
    return parseDecimal(t);
  }

  /** The end of the longest StrDecimalLiteral starting at {@code start}, or {@code start} when there is none. */
  private static int decimalPrefix(String s, int start) {
    int i = start;
    int n = s.length();
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      i++;
    }
    if (s.startsWith("Infinity", i)) {
      return i + 8;
    }
    int digitsStart = i;
    while (i < n && isDigit(s.charAt(i))) {
      i++;
    }
    boolean any = i > digitsStart;
    if (i < n && s.charAt(i) == '.') {
      int fracStart = i + 1;
      int j = fracStart;
      while (j < n && isDigit(s.charAt(j))) {
        j++;
      }
      if (any || j > fracStart) {
        any = true;
        i = j;
      }
    }
    if (!any) {
      return start;
    }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      int j = i + 1;
      if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
        j++;
      }
      int expStart = j;
      while (j < n && isDigit(s.charAt(j))) {
        j++;
      }
      if (j > expStart) {
        i = j;
      }
    }
    return i;
  }

  private static double parseDecimal(String literal) {
    String body = literal;
    boolean negative = false;
    if (body.startsWith("+") || body.startsWith("-")) {
      negative = body.charAt(0) == '-';
      body = body.substring(1);
    }
    double value = body.equals("Infinity") ? Double.POSITIVE_INFINITY : Double.parseDouble(body);
    return negative ? -value : value;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  public static double toIntegerOrInfinity(double d) {
    if (Double.isNaN(d) || d == 0) {
      return 0;
    }
    if (Double.isInfinite(d)) {
      return d;
    }
    return d < 0 ? Math.ceil(d) : Math.floor(d);
  }

  public static int toInt32(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      return 0;
    }
    double t = d < 0 ? Math.ceil(d) : Math.floor(d);
    double m = t % 4294967296.0;
    if (m < 0) {
      m += 4294967296.0;
    }
    return (int) (long) m;
  }

  public static long toUint32(double d) {
    return toInt32(d) & 0xFFFFFFFFL;
  }

  /** ToString. */
  public static String str(Object v) {
    if (v instanceof String s) {
      return s;
    }
    if (v instanceof Double d) {
      return numberToString(d);
    }
    if (v == null) {
      return "null";
    }
    if (v instanceof List<?> list) {
      return join(list, ",");
    }
    return v.toString();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Number printing

  /** Number::toString(10): the shortest digits that round-trip, in JavaScript's notation. */
  public static String numberToString(double v) {
    if (Double.isNaN(v)) {
      return "NaN";
    }
    if (v == 0) {
      return "0";
    }
    if (Double.isInfinite(v)) {
      return v > 0 ? "Infinity" : "-Infinity";
    }
    Decimal dec = shortest(Math.abs(v));
    String digits = dec.digits;
    int k = digits.length();
    int n = dec.point;
    StringBuilder sb = new StringBuilder(v < 0 ? "-" : "");
    if (k <= n && n <= 21) {
      sb.append(digits).append("0".repeat(n - k));
    } else if (0 < n && n <= 21) {
      sb.append(digits, 0, n).append('.').append(digits, n, k);
    } else if (-6 < n && n <= 0) {
      sb.append("0.").append("0".repeat(-n)).append(digits);
    } else {
      int e = n - 1;
      sb.append(digits.charAt(0));
      if (k > 1) {
        sb.append('.').append(digits, 1, k);
      }
      sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
    }
    return sb.toString();
  }

  /** Decimal digits d1..dk (no leading or trailing zeros) and n with value = 0.d1..dk * 10^n. */
  public record Decimal(String digits, int point) {}

  /** The shortest round-trip decimal of a positive finite double, the closest one when several are as short. */
  public static Decimal shortest(double a) {
    String s = Double.toString(a);
    int exp = 0;
    int e = s.indexOf('E');
    if (e >= 0) {
      exp = Integer.parseInt(s.substring(e + 1));
      s = s.substring(0, e);
    }
    int dot = s.indexOf('.');
    String digits = s.substring(0, dot) + s.substring(dot + 1);
    int point = dot + exp;
    int lead = 0;
    while (lead < digits.length() - 1 && digits.charAt(lead) == '0') {
      lead++;
    }
    digits = digits.substring(lead);
    point -= lead;
    int end = digits.length();
    while (end > 1 && digits.charAt(end - 1) == '0') {
      end--;
    }
    digits = digits.substring(0, end);
    if (digits.length() == 2) {
      // Java may print two digits where one already round-trips (subnormals); JavaScript prints the one digit.
      Decimal best = null;
      BigDecimal exact = new BigDecimal(a);
      for (int d = digits.charAt(0) - '0'; d <= digits.charAt(0) - '0' + 1; d++) {
        Decimal cand = d == 10 ? new Decimal("1", point + 1) : new Decimal(String.valueOf(d), point);
        BigDecimal value = new BigDecimal(new BigInteger(cand.digits), -(cand.point - 1));
        if (value.doubleValue() == a
            && (best == null
                || value.subtract(exact).abs().compareTo(
                        new BigDecimal(new BigInteger(best.digits), -(best.point - 1)).subtract(exact).abs())
                    < 0)) {
          best = cand;
        }
      }
      if (best != null) {
        return best;
      }
    }
    return new Decimal(digits, point);
  }

  /** Number.prototype.toFixed. */
  public static String toFixed(double x, Object fractionDigits) {
    double f = toIntegerOrInfinity(toNumber(fractionDigits));
    if (f < 0 || f > 100) {
      throw JsException.rangeError("toFixed() digits argument must be between 0 and 100");
    }
    if (Double.isNaN(x)) {
      return "NaN";
    }
    if (Double.isInfinite(x) || Math.abs(x) >= 1e21) {
      return numberToString(x);
    }
    String body = new BigDecimal(Math.abs(x)).setScale((int) f, RoundingMode.HALF_UP).toPlainString();
    return (x < 0 ? "-" : "") + body;
  }

  /** Number.prototype.toString(radix) for radix 2..36 other than 10 (V8's DoubleToRadixCString). */
  public static String toRadixString(double value, int radix) {
    if (radix == 10) {
      return numberToString(value);
    }
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "Infinity" : "-Infinity";
    }
    final String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
    boolean negative = value < 0;
    if (negative) {
      value = -value;
    }
    double integer = Math.floor(value);
    double fraction = value - integer;
    double delta = 0.5 * (Math.nextUp(value) - value);
    delta = Math.max(Math.nextUp(0.0), delta);
    StringBuilder frac = new StringBuilder();
    if (fraction >= delta) {
      frac.append('.');
      do {
        fraction *= radix;
        delta *= radix;
        int digit = (int) fraction;
        frac.append(chars.charAt(digit));
        fraction -= digit;
        if (fraction > 0.5 || (fraction == 0.5 && (digit & 1) != 0)) {
          if (fraction + delta > 1) {
            while (true) {
              int last = frac.length() - 1;
              if (last == 0) {
                integer += 1;
                break;
              }
              char c = frac.charAt(last);
              frac.setLength(last);
              int d = c > '9' ? c - 'a' + 10 : c - '0';
              if (d + 1 < radix) {
                frac.append(chars.charAt(d + 1));
                break;
              }
            }
            break;
          }
        }
      } while (fraction >= delta);
      if (frac.length() == 1) {
        frac.setLength(0);
      }
    }
    StringBuilder intPart = new StringBuilder();
    while (Math.getExponent(integer / radix) - 52 > 0) {
      integer /= radix;
      intPart.append('0');
    }
    do {
      double remainder = integer % radix;
      intPart.append(chars.charAt((int) remainder));
      integer = (integer - remainder) / radix;
    } while (integer > 0);
    if (negative) {
      intPart.append('-');
    }
    return intPart.reverse().append(frac).toString();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Global functions

  public static boolean isNaN(Object v) {
    return Double.isNaN(toNumber(v));
  }

  public static boolean isFinite(Object v) {
    double d = toNumber(v);
    return !Double.isNaN(d) && !Double.isInfinite(d);
  }

  public static double parseFloat(Object v) {
    String s = trimStart(str(v));
    int end = decimalPrefix(s, 0);
    if (end == 0) {
      return Double.NaN;
    }
    return parseDecimal(s.substring(0, end));
  }

  public static double parseInt(Object v, Object radixArg) {
    String s = trimStart(str(v));
    int sign = 1;
    if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
      sign = s.charAt(0) == '-' ? -1 : 1;
      s = s.substring(1);
    }
    int radix = toInt32(toNumber(radixArg));
    boolean stripPrefix = true;
    if (radix != 0) {
      if (radix < 2 || radix > 36) {
        return Double.NaN;
      }
      if (radix != 16) {
        stripPrefix = false;
      }
    } else {
      radix = 10;
    }
    if (stripPrefix && s.length() >= 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
      s = s.substring(2);
      radix = 16;
    }
    int end = 0;
    while (end < s.length() && s.charAt(end) < 128 && Character.digit(s.charAt(end), radix) >= 0) {
      end++;
    }
    if (end == 0) {
      return Double.NaN;
    }
    String digits = s.substring(0, end);
    double value;
    if (radix == 10 || Integer.bitCount(radix) == 1) {
      value = new BigInteger(digits, radix).doubleValue();
    } else {
      value = parseIntApproximate(digits, radix);
    }
    return sign * value;
  }

  /** V8's accumulation for radixes that are neither 10 nor a power of two. */
  private static double parseIntApproximate(String digits, int radix) {
    double result = 0;
    int i = 0;
    final long maxMultiplier = 0xFFFFFFFFL / 36;
    boolean done = false;
    do {
      long part = 0;
      long multiplier = 1;
      while (true) {
        int d = Character.digit(digits.charAt(i), radix);
        long m = multiplier * radix;
        if (m > maxMultiplier) {
          break;
        }
        part = part * radix + d;
        multiplier = m;
        i++;
        if (i == digits.length()) {
          done = true;
          break;
        }
      }
      result = result * multiplier + part;
    } while (!done);
    return result;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Operators

  /** {@code a === b}. */
  public static boolean strictEquals(Object a, Object b) {
    if (a instanceof Double x && b instanceof Double y) {
      return x.doubleValue() == y.doubleValue();
    }
    if (a instanceof String x && b instanceof String y) {
      return x.equals(y);
    }
    if (a instanceof Boolean x && b instanceof Boolean y) {
      return x.booleanValue() == y.booleanValue();
    }
    return a == b;
  }

  /** SameValueZero, as Array.prototype.includes compares. */
  public static boolean sameValueZero(Object a, Object b) {
    if (a instanceof Double x && b instanceof Double y && x.isNaN() && y.isNaN()) {
      return true;
    }
    return strictEquals(a, b);
  }

  /** {@code a == b}. */
  public static boolean looseEquals(Object a, Object b) {
    if (isNullish(a) || isNullish(b)) {
      return isNullish(a) && isNullish(b);
    }
    if (typeOf(a).equals(typeOf(b))) {
      return strictEquals(a, b);
    }
    if (a instanceof Boolean) {
      return looseEquals(toNumber(a), b);
    }
    if (b instanceof Boolean) {
      return looseEquals(a, toNumber(b));
    }
    boolean aObject = typeOf(a).equals("object");
    boolean bObject = typeOf(b).equals("object");
    if (aObject && !bObject) {
      return looseEquals(toPrimitive(a, false), b);
    }
    if (bObject && !aObject) {
      return looseEquals(a, toPrimitive(b, false));
    }
    return toNumber(a) == toNumber(b);
  }

  /** The abstract relational comparison: -1 for less, 0 for equal, 1 for greater, 2 when undefined (NaN). */
  private static int compare(Object a, Object b) {
    Object pa = toPrimitive(a, true);
    Object pb = toPrimitive(b, true);
    if (pa instanceof String x && pb instanceof String y) {
      return Integer.signum(x.compareTo(y));
    }
    double x = toNumber(pa);
    double y = toNumber(pb);
    if (Double.isNaN(x) || Double.isNaN(y)) {
      return 2;
    }
    return x < y ? -1 : x > y ? 1 : 0;
  }

  public static boolean lt(Object a, Object b) {
    return compare(a, b) == -1;
  }

  public static boolean gt(Object a, Object b) {
    return compare(b, a) == -1;
  }

  public static boolean le(Object a, Object b) {
    int c = compare(b, a);
    return c == 1 || c == 0;
  }

  public static boolean ge(Object a, Object b) {
    int c = compare(a, b);
    return c == 1 || c == 0;
  }

  /** {@code a + b}. */
  public static Object add(Object a, Object b) {
    Object pa = toPrimitive(a, false);
    Object pb = toPrimitive(b, false);
    if (pa instanceof String || pb instanceof String) {
      return str(pa) + str(pb);
    }
    return toNumber(pa) + toNumber(pb);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Math

  /** Math.round. */
  public static double round(double x) {
    if (Double.isNaN(x) || Double.isInfinite(x) || Math.abs(x) >= 4503599627370496.0) {
      return x;
    }
    double r = Math.floor(x);
    if (x - r >= 0.5) {
      r += 1;
    }
    if (r == 0 && (x < 0 || (x == 0 && 1 / x < 0))) {
      return -0.0;
    }
    return r;
  }

  /** Math.trunc. */
  public static double trunc(double x) {
    return x < 0 ? Math.ceil(x) : Math.floor(x);
  }

  /** Math.sign. */
  public static double sign(double x) {
    if (Double.isNaN(x) || x == 0) {
      return x;
    }
    return x > 0 ? 1 : -1;
  }

  /** Math.pow, as V8 computes it. */
  public static double pow(double x, double y) {
    return JsMath.pow(x, y);
  }

  /** Math.max over numbers already converted. */
  public static double max(double... values) {
    double m = Double.NEGATIVE_INFINITY;
    for (double v : values) {
      m = Math.max(m, v);
    }
    return m;
  }

  /** Math.min over numbers already converted. */
  public static double min(double... values) {
    double m = Double.POSITIVE_INFINITY;
    for (double v : values) {
      m = Math.min(m, v);
    }
    return m;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Strings

  /** WhiteSpace or LineTerminator, as String.prototype.trim and the regular expression class \s see them. */
  public static boolean isSpace(int c) {
    return switch (c) {
      case 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true;
      default -> c >= 0x2000 && c <= 0x200A;
    };
  }

  public static String trim(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && isSpace(s.charAt(start))) {
      start++;
    }
    while (end > start && isSpace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(start, end);
  }

  public static String trimStart(String s) {
    int start = 0;
    while (start < s.length() && isSpace(s.charAt(start))) {
      start++;
    }
    return s.substring(start);
  }

  public static String toUpperCase(String s) {
    return s.toUpperCase(Locale.ROOT);
  }

  public static String toLowerCase(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  private static int clampIndex(Object position, int length) {
    double p = toIntegerOrInfinity(toNumber(position));
    return (int) Math.max(0, Math.min(p, length));
  }

  /** String.prototype.substring. */
  public static String substring(String s, Object start, Object end) {
    int a = clampIndex(start, s.length());
    int b = end == UNDEFINED ? s.length() : clampIndex(end, s.length());
    return s.substring(Math.min(a, b), Math.max(a, b));
  }

  /** String.prototype.substr. */
  public static String substr(String s, Object start, Object length) {
    int size = s.length();
    double begin = toIntegerOrInfinity(toNumber(start));
    if (begin == Double.NEGATIVE_INFINITY) {
      begin = 0;
    } else if (begin < 0) {
      begin = Math.max(size + begin, 0);
    } else {
      begin = Math.min(begin, size);
    }
    double count = length == UNDEFINED ? size : toIntegerOrInfinity(toNumber(length));
    double end = Math.min(begin + Math.max(count, 0), size);
    return begin >= end ? "" : s.substring((int) begin, (int) end);
  }

  /** String.prototype.slice and Array.prototype.slice bounds: a relative index clamped to [0, length]. */
  public static int relativeIndex(Object position, int length) {
    double p = toIntegerOrInfinity(toNumber(position));
    if (p < 0) {
      return (int) Math.max(length + p, 0);
    }
    return (int) Math.min(p, length);
  }

  public static String slice(String s, Object start, Object end) {
    int a = relativeIndex(start, s.length());
    int b = end == UNDEFINED ? s.length() : relativeIndex(end, s.length());
    return a < b ? s.substring(a, b) : "";
  }

  /** String.prototype.indexOf. */
  public static int indexOf(String s, Object search, Object position) {
    String needle = str(search);
    int start = clampIndex(position, s.length());
    if (needle.isEmpty()) {
      return start;
    }
    return s.indexOf(needle, start);
  }

  /** String.prototype.split with a string separator and no limit. */
  public static List<Object> split(String s, String separator) {
    List<Object> out = new ArrayList<>();
    if (separator.isEmpty()) {
      for (int i = 0; i < s.length(); i++) {
        out.add(String.valueOf(s.charAt(i)));
      }
      return out;
    }
    int from = 0;
    int at;
    while ((at = s.indexOf(separator, from)) >= 0) {
      out.add(s.substring(from, at));
      from = at + separator.length();
    }
    out.add(s.substring(from));
    return out;
  }

  /** String.prototype.replace with a string pattern and a replacement without $ patterns (first match only). */
  public static String replaceFirst(String s, String pattern, String replacement) {
    int at = s.indexOf(pattern);
    return at < 0 ? s : s.substring(0, at) + replacement + s.substring(at + pattern.length());
  }

  /** ICU root collation order of the ASCII characters that are not ignorable (letters: lower and upper together). */
  private static final String COLLATION_ORDER =
      "\t\n\u000b\f\r _-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$0123456789abcdefghijklmnopqrstuvwxyz";

  /**
   * String.prototype.localeCompare in the en-US locale (ICU's root collation, tertiary strength), exact for ASCII:
   * control characters are ignored, then primary order, then lower case before upper case. Other characters are
   * compared by code unit after the ASCII ones.
   */
  public static int localeCompare(String a, String b) {
    int[] ka = collationKey(a);
    int[] kb = collationKey(b);
    for (int level = 0; level < 2; level++) {
      int na = ka.length / 2;
      int nb = kb.length / 2;
      for (int i = 0; i < Math.min(na, nb); i++) {
        int c = Integer.compare(ka[level * na + i], kb[level * nb + i]);
        if (c != 0) {
          return c;
        }
      }
      if (na != nb) {
        return na < nb ? -1 : 1;
      }
    }
    return 0;
  }

  /** Primary weights of the non-ignorable characters followed by their tertiary (case) weights. */
  private static int[] collationKey(String s) {
    int[] primary = new int[s.length()];
    int[] tertiary = new int[s.length()];
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 128) {
        int p = COLLATION_ORDER.indexOf(Character.toLowerCase(c));
        if (p < 0) {
          continue;
        }
        primary[n] = p;
        tertiary[n++] = c >= 'A' && c <= 'Z' ? 1 : 0;
      } else {
        primary[n] = COLLATION_ORDER.length() + c;
        tertiary[n++] = 0;
      }
    }
    int[] key = new int[2 * n];
    System.arraycopy(primary, 0, key, 0, n);
    System.arraycopy(tertiary, 0, key, n, n);
    return key;
  }

  /** String.fromCharCode for one argument. */
  public static String fromCharCode(Object code) {
    return String.valueOf((char) (toUint32(toNumber(code)) & 0xFFFF));
  }

  /** String.prototype.repeat-like guard: V8's maximum string length. */
  public static final int MAX_STRING_LENGTH = (1 << 29) - 24;

  public static void checkStringLength(long length) {
    if (length > MAX_STRING_LENGTH) {
      throw JsException.rangeError("Invalid string length");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Arrays and members

  /** Array.prototype.join. */
  public static String join(List<?> list, String separator) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(separator);
      }
      Object v = list.get(i);
      if (!isNullish(v)) {
        sb.append(str(v));
      }
      checkStringLength(sb.length());
    }
    return sb.toString();
  }

  /** {@code target[key]} for an integer key: an array element, a character, or undefined. */
  public static Object get(Object target, double key) {
    if (target instanceof List<?> list) {
      if (key >= 0 && key < list.size() && key == Math.floor(key)) {
        return list.get((int) key);
      }
      return UNDEFINED;
    }
    if (target instanceof String s) {
      if (key >= 0 && key < s.length() && key == Math.floor(key)) {
        return String.valueOf(s.charAt((int) key));
      }
      return UNDEFINED;
    }
    if (isNullish(target)) {
      throw JsException.cannotRead(target, key);
    }
    return UNDEFINED;
  }

  /** {@code target[key]} for any key: integer-valued keys index, anything else reads nothing. */
  public static Object get(Object target, Object key) {
    if (key instanceof Double d) {
      return get(target, d.doubleValue());
    }
    String name = str(key);
    double index = stringToNumber(name);
    if (!name.isEmpty() && numberToString(index).equals(name)) {
      return get(target, index);
    }
    if (name.equals("length") && (target instanceof List || target instanceof String)) {
      return length(target);
    }
    if (isNullish(target)) {
      throw JsException.cannotRead(target, name);
    }
    return UNDEFINED;
  }

  /** {@code target.length}: a number for arrays and strings, undefined for other values. */
  public static Object length(Object target) {
    if (target instanceof List<?> list) {
      return (double) list.size();
    }
    if (target instanceof String s) {
      return (double) s.length();
    }
    if (isNullish(target)) {
      throw JsException.cannotRead(target, "length");
    }
    return UNDEFINED;
  }

  /** {@code array[index] = value}, growing the array with undefined as JavaScript does. */
  public static void set(List<Object> list, int index, Object value) {
    while (list.size() <= index) {
      list.add(UNDEFINED);
    }
    list.set(index, value);
  }

  /** The length {@code new Array(length)} accepts, or its RangeError. */
  public static long checkArrayLength(double length) {
    if (!(length >= 0) || length != Math.floor(length) || length > 4294967295.0) {
      throw JsException.rangeError("Invalid array length");
    }
    return (long) length;
  }

  /** {@code new Array(length)} (holes read as undefined). */
  public static List<Object> newArray(double length) {
    checkArrayLength(length);
    List<Object> out = new ArrayList<>();
    for (int i = 0; i < (int) length; i++) {
      out.add(UNDEFINED);
    }
    return out;
  }

  /** Array.prototype.indexOf (strict equality). */
  public static int indexOf(List<?> list, Object value) {
    for (int i = 0; i < list.size(); i++) {
      if (strictEquals(list.get(i), value)) {
        return i;
      }
    }
    return -1;
  }

  /** Array.prototype.includes (SameValueZero). */
  public static boolean includes(List<?> list, Object value) {
    for (Object item : list) {
      if (sameValueZero(item, value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Array.prototype.sort with a comparator, in place, as V8 does it: undefined elements go to the end without being
   * compared, the comparator's answer is converted with ToNumber (NaN counts as 0), and the rest is sorted by V8's
   * TimSort (see {@link ArraySort}), so that inconsistent comparators give V8's order too.
   */
  public static List<Object> sort(List<Object> list, BiFunction<Object, Object, Object> comparator) {
    return ArraySort.sort(list, comparator);
  }
}
