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
import io.github.formulajs.js.JsDate;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Text functions. */
public final class Text {
  private Text() {}

  /** Returns the character specified by the code number. */
  public static Object CHAR(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    if (Js.strictEquals(number, 0.0)) {
      return Errors.VALUE;
    }
    if (number instanceof JsError) {
      return number;
    }
    return Js.fromCharCode(number);
  }

  /** Removes all nonprintable characters from text. */
  public static Object CLEAN(Object... args) {
    Object text = Js.arg(args, 0);
    if (Common.anyIsError(text)) {
      return text;
    }
    if (!Js.truthy(text)) {
      text = "";
    }
    if (!(text instanceof String s)) {
      throw JsException.notAFunction("text.replace");
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 0x1F) {
        out.append(s.charAt(i));
      }
    }
    return out.toString();
  }

  /** Returns a numeric code for the first character in a text string. */
  public static Object CODE(Object... args) {
    Object text = Js.arg(args, 0);
    if (Common.anyIsError(text)) {
      return text;
    }
    if (!Js.truthy(text)) {
      text = "";
    }
    if (!(text instanceof String s)) {
      throw JsException.notAFunction("text.charCodeAt");
    }
    return s.isEmpty() ? Errors.VALUE : (Object) (double) s.charAt(0);
  }

  /** Joins several text items into one text item. */
  public static Object CONCATENATE(Object... arguments) {
    List<Object> args = Common.flatten(Common.arguments(arguments));
    Object someError = Common.anyError(args.toArray());
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    int found;
    while ((found = Js.indexOf(args, true)) > -1) {
      args.set(found, "TRUE");
    }
    while ((found = Js.indexOf(args, false)) > -1) {
      args.set(found, "FALSE");
    }
    return Js.join(args, "");
  }

  /** Converts a number to text, using the $ (dollar) currency format. */
  public static Object DOLLAR(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    Object decimals = Js.arg(args, 1, 2.0);
    if (Js.isNaN(number)) {
      return Errors.VALUE;
    }
    number = MathTrig.ROUND(number, decimals);
    Object fractionDigits = Js.ge(decimals, 0.0) ? decimals : 0.0;
    String formatted;
    if (number instanceof Double d) {
      formatted = formatCurrency(d, fractionDigits);
    } else {
      formatted = Js.str(number);
    }
    if (Js.lt(number, 0.0)) {
      return "$(" + Js.slice(formatted, 2.0, Js.UNDEFINED) + ")";
    }
    return formatted;
  }

  /** Number.prototype.toLocaleString('en-US', {style: 'currency', currency: 'USD', ...FractionDigits}). */
  private static String formatCurrency(double x, Object fractionDigitsOption) {
    double option = Js.toNumber(fractionDigitsOption);
    if (Double.isNaN(option) || option < 0 || option > 100) {
      throw JsException.rangeError("minimumFractionDigits value is out of range.");
    }
    int fractionDigits = (int) Math.floor(option);
    boolean negative = x < 0 || (x == 0 && 1 / x < 0);
    String sign = negative ? "-$" : "$";
    if (Double.isNaN(x)) {
      return "$NaN";
    }
    if (Double.isInfinite(x)) {
      return sign + "∞";
    }
    BigDecimal value = BigDecimal.ZERO;
    if (x != 0) {
      Js.Decimal dec = Js.shortest(Math.abs(x));
      value = new BigDecimal(new BigInteger(dec.digits()), dec.digits().length() - dec.point());
    }
    String plain = value.setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString();
    int dot = plain.indexOf('.');
    String integer = dot < 0 ? plain : plain.substring(0, dot);
    String fraction = dot < 0 ? "" : plain.substring(dot);
    StringBuilder grouped = new StringBuilder();
    for (int i = 0; i < integer.length(); i++) {
      if (i > 0 && (integer.length() - i) % 3 == 0) {
        grouped.append(',');
      }
      grouped.append(integer.charAt(i));
    }
    return sign + grouped + fraction;
  }

  /** Checks to see if two text values are identical. */
  public static Object EXACT(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object someError = Common.anyError(args[0], args[1]);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    return Js.strictEquals(Common.parseString(args[0]), Common.parseString(args[1]));
  }

  /** Returns the position of one text string within another (case-sensitive). */
  public static Object FIND(Object... args) {
    if (args.length < 2) {
      return Errors.NA;
    }
    Object findText = Common.parseString(args[0]);
    Object withinText = Common.parseString(args[1]);
    Object startNum = Js.arg(args, 2);
    startNum = startNum == Js.UNDEFINED ? 0.0 : startNum;
    if (!(withinText instanceof String within)) {
      throw JsException.notAFunction("within_text.indexOf");
    }
    int foundIndex = Js.indexOf(within, findText, Js.toNumber(startNum) - 1);
    if (foundIndex == -1) {
      return Errors.VALUE;
    }
    return foundIndex + 1.0;
  }

  /** Formats a number as text with a fixed number of decimals. */
  public static Object FIXED(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    if (Js.isNaN(number)) {
      return Errors.VALUE;
    }
    Object decimals = Common.parseNumber(Js.arg(args, 1, 2.0));
    if (Js.isNaN(decimals)) {
      return Errors.VALUE;
    }
    Object noCommas = Js.arg(args, 2, false);
    double n = (Double) number;
    double d = (Double) decimals;
    String text;
    if (d < 0) {
      double factor = Js.pow(10, -d);
      text = Js.numberToString(Js.round(n / factor) * factor);
    } else {
      text = Js.toFixed(n, d);
    }
    if (Js.truthy(noCommas)) {
      return text.replace(",", "");
    }
    int dot = text.indexOf('.');
    String integer = dot < 0 ? text : text.substring(0, dot);
    return groupThousands(integer) + (dot < 0 ? "" : text.substring(dot));
  }

  /** {@code s.replace(/\B(?=(\d{3})+$)/g, ',')}. */
  private static String groupThousands(String s) {
    StringBuilder out = new StringBuilder();
    int digitsFrom = s.length();
    while (digitsFrom > 0 && s.charAt(digitsFrom - 1) >= '0' && s.charAt(digitsFrom - 1) <= '9') {
      digitsFrom--;
    }
    for (int p = 0; p < s.length(); p++) {
      int rest = s.length() - p;
      if (p > 0 && p >= digitsFrom && rest % 3 == 0 && isWordChar(s.charAt(p - 1))) {
        out.append(',');
      }
      out.append(s.charAt(p));
    }
    return out.toString();
  }

  private static boolean isWordChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
  }

  /** Returns the leftmost characters from a text value. */
  public static Object LEFT(Object... args) {
    Object text = Js.arg(args, 0);
    Object numChars = Js.arg(args, 1);
    Object someError = Common.anyError(text, numChars);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    text = Common.parseString(text);
    numChars = Common.parseNumber(numChars == Js.UNDEFINED ? 1.0 : numChars);
    if (numChars instanceof JsError || !(text instanceof String)) {
      return Errors.VALUE;
    }
    return Js.substring((String) text, 0.0, numChars);
  }

  /** Returns the number of characters in a text string. */
  public static Object LEN(Object... args) {
    if (args.length == 0) {
      return Errors.ERROR;
    }
    Object text = args[0];
    if (text instanceof JsError) {
      return text;
    }
    if (text instanceof List) {
      return Errors.VALUE;
    }
    return (double) ((String) Common.parseString(text)).length();
  }

  /** Converts text to lowercase. */
  public static Object LOWER(Object... args) {
    if (args.length != 1) {
      return Errors.VALUE;
    }
    Object text = Common.parseString(args[0]);
    if (text instanceof JsError) {
      return text;
    }
    return Js.toLowerCase((String) text);
  }

  /** Returns a specific number of characters from a text string starting at the position you specify. */
  public static Object MID(Object... args) {
    Object text = Js.arg(args, 0);
    Object startNum = Js.arg(args, 1);
    Object numChars = Js.arg(args, 2);
    if (Common.anyIsError(text, startNum, numChars)) {
      return Common.anyError(text, startNum, numChars);
    }
    Object start = Common.parseNumber(startNum);
    Object count = Common.parseNumber(numChars);
    for (Object numArg : new Object[] {start, count}) {
      if (numArg instanceof JsError || !Js.gt(numArg, 0.0)) {
        return Errors.VALUE;
      }
    }
    String s = Common.isDefined(text) ? Js.str(text) : "";
    double begin = (Double) start - 1;
    double end = begin + (Double) count;
    return Js.substring(s, begin, end);
  }

  /** Converts text to number in a locale-independent manner. */
  public static Object NUMBERVALUE(Object... args) {
    Object text = Js.arg(args, 0);
    text = Common.isDefined(text) ? text : "";
    if (text instanceof Double) {
      return text;
    }
    if (!(text instanceof String s)) {
      return Errors.NA;
    }
    Object decimalSeparator = Js.arg(args, 1);
    Object groupSeparator = Js.arg(args, 2);
    decimalSeparator = decimalSeparator == Js.UNDEFINED ? "." : decimalSeparator;
    groupSeparator = groupSeparator == Js.UNDEFINED ? "," : groupSeparator;
    String replaced = Js.replaceFirst(s, Js.str(decimalSeparator), ".");
    return Js.stringToNumber(Js.replaceFirst(replaced, Js.str(groupSeparator), ""));
  }

  /** Capitalizes the first letter in each word of a text value. */
  public static Object PROPER(Object... args) {
    Object text = Js.arg(args, 0);
    if (Common.anyIsError(text)) {
      return text;
    }
    if (text instanceof Double d && d.isNaN()) {
      return Errors.VALUE;
    }
    String s = (String) Common.parseString(text);
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < s.length()) {
      if (!isWordChar(s.charAt(i))) {
        out.append(s.charAt(i++));
        continue;
      }
      int j = i + 1;
      while (j < s.length() && !Js.isSpace(s.charAt(j))) {
        j++;
      }
      out.append(Js.toUpperCase(s.substring(i, i + 1))).append(Js.toLowerCase(s.substring(i + 1, j)));
      i = j;
    }
    return out.toString();
  }

  /** Replaces characters within text. */
  public static Object REPLACE(Object... args) {
    Object oldText = Js.arg(args, 0);
    Object numChars = Common.parseNumber(Js.arg(args, 1));
    Object length = Common.parseNumber(Js.arg(args, 2));
    Object newText = Js.arg(args, 3);
    if (Common.anyIsError(numChars, length) || !(oldText instanceof String old) || !(newText instanceof String)) {
      return Errors.VALUE;
    }
    double start = (Double) numChars - 1;
    return Js.substr(old, 0.0, start) + newText + Js.substr(old, start + (Double) length, Js.UNDEFINED);
  }

  /** Repeats text a given number of times. */
  public static Object REPT(Object... args) {
    Object text = Js.arg(args, 0);
    Object numberTimes = Js.arg(args, 1);
    Object someError = Common.anyError(text, numberTimes);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    String s = (String) Common.parseString(text);
    numberTimes = Common.parseNumber(numberTimes);
    if (numberTimes instanceof JsError) {
      return numberTimes;
    }
    long count = Js.checkArrayLength((Double) numberTimes + 1) - 1;
    if (count <= 0) {
      return "";
    }
    Js.checkStringLength(count * s.length());
    return s.repeat((int) count);
  }

  /** Returns the rightmost characters from a text value. */
  public static Object RIGHT(Object... args) {
    Object text = Js.arg(args, 0);
    Object numChars = Js.arg(args, 1);
    Object someError = Common.anyError(text, numChars);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    String s = (String) Common.parseString(text);
    numChars = Common.parseNumber(numChars == Js.UNDEFINED ? 1.0 : numChars);
    if (numChars instanceof JsError) {
      return numChars;
    }
    return Js.substring(s, s.length() - (Double) numChars, Js.UNDEFINED);
  }

  /** Finds one text value within another (not case-sensitive). */
  public static Object SEARCH(Object... args) {
    Object findText = Js.arg(args, 0);
    Object withinText = Js.arg(args, 1);
    if (!(findText instanceof String find) || !(withinText instanceof String within)) {
      return Errors.VALUE;
    }
    Object startNum = Js.arg(args, 2);
    startNum = startNum == Js.UNDEFINED ? 0.0 : startNum;
    int foundAt = Js.indexOf(Js.toLowerCase(within), Js.toLowerCase(find), Js.toNumber(startNum) - 1) + 1;
    return foundAt == 0 ? Errors.VALUE : (Object) (double) foundAt;
  }

  /** Substitutes new text for old text in a text string. */
  public static Object SUBSTITUTE(Object... args) {
    if (args.length < 3) {
      return Errors.NA;
    }
    Object text = args[0];
    Object oldText = args[1];
    Object newText = args[2];
    Object instanceNum = Js.arg(args, 3);
    if (!Js.truthy(text) || !Js.truthy(oldText)) {
      return text;
    }
    if (instanceNum == Js.UNDEFINED) {
      if (!(text instanceof String s)) {
        throw JsException.notAFunction("text.split");
      }
      return Js.join(Js.split(s, Js.str(oldText)), newText == Js.UNDEFINED ? "," : Js.str(newText));
    }
    double instance = Math.floor(Js.toNumber(instanceNum));
    if (Double.isNaN(instance) || instance <= 0) {
      return Errors.VALUE;
    }
    if (!(text instanceof String s)) {
      throw JsException.notAFunction("text.indexOf");
    }
    double index = 0;
    int i = 0;
    while (index > -1 && Js.indexOf(s, oldText, index) > -1) {
      index = Js.indexOf(s, oldText, index + 1);
      i++;
      if (index > -1 && i == instance) {
        Object end = Js.add(index, Js.length(oldText));
        return Js.add(Js.add(Js.substring(s, 0.0, index), newText), Js.substring(s, end, Js.UNDEFINED));
      }
    }
    return text;
  }

  /** Converts its arguments to text. */
  public static Object T(Object... args) {
    Object value = Js.arg(args, 0);
    if (value instanceof JsError) {
      return value;
    }
    return value instanceof String ? value : "";
  }

  /** Formats a number and converts it to text. */
  public static Object TEXT(Object... args) {
    Object value = Js.arg(args, 0);
    Object formatText = Js.arg(args, 1);
    if (value == Js.UNDEFINED || value instanceof JsError || formatText instanceof JsError) {
      return Errors.NA;
    }
    if (value instanceof JsDate d) {
      return d.toISOString().substring(0, 10);
    }
    if (Js.isNullish(formatText)) {
      return "";
    }
    if (formatText instanceof Double) {
      return Js.str(formatText);
    }
    if (!(formatText instanceof String format)) {
      return Errors.VALUE;
    }
    String currencySymbol = format.startsWith("$") ? "$" : "";
    boolean isPercent = format.endsWith("%");
    format = format.replace("%", "").replace("$", "");
    double decimalPlaces = 0;
    if (format.contains(".")) {
      String decimals = (String) Js.split(format, ".").get(1);
      int zeros = 0;
      for (int i = 0; i < decimals.length(); i++) {
        if (decimals.charAt(i) == '0') {
          zeros++;
        }
      }
      if (zeros == 0) {
        throw JsException.cannotRead(null, "length");
      }
      decimalPlaces = zeros;
    }
    boolean noCommas = !format.contains(",");
    if (isPercent) {
      value = Js.toNumber(value) * 100;
    }
    Object fixed = FIXED(value, decimalPlaces, noCommas);
    if (!(fixed instanceof String text)) {
      throw JsException.notAFunction("value.startsWith");
    }
    if (text.startsWith("-")) {
      text = "-" + currencySymbol + text.substring(1);
    } else {
      text = currencySymbol + text;
    }
    if (isPercent) {
      text = text + "%";
    }
    return text;
  }

  /** Combines the text from multiple ranges and/or strings. */
  public static Object TEXTJOIN(Object... args) {
    Object delimiter = Js.arg(args, 0);
    Object ignoreEmpty = Js.arg(args, 1);
    if (!(ignoreEmpty instanceof Boolean)) {
      ignoreEmpty = Common.parseBool(ignoreEmpty);
    }
    if (args.length < 3) {
      return Errors.NA;
    }
    delimiter = Js.isNullish(delimiter) ? "" : delimiter;
    List<Object> rest = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
    List<Object> flatArgs = Common.flatten(rest);
    List<Object> textToJoin = flatArgs;
    if (Js.truthy(ignoreEmpty)) {
      textToJoin = new ArrayList<>();
      for (Object text : flatArgs) {
        if (Js.truthy(text)) {
          textToJoin.add(text);
        }
      }
    }
    if (delimiter instanceof List) {
      List<Object> delimiters = Common.flatten(delimiter);
      List<Object> chunks = new ArrayList<>();
      for (Object item : textToJoin) {
        chunks.add(Js.list(item));
      }
      int index = 0;
      for (int i = 0; i < chunks.size() - 1; i++) {
        Js.asList(chunks.get(i)).add(Js.get(delimiters, (double) index));
        index++;
        if (index == delimiters.size()) {
          index = 0;
        }
      }
      return Js.join(Common.flatten(chunks), "");
    }
    return Js.join(textToJoin, Js.str(delimiter));
  }

  /** Removes spaces from text. */
  public static Object TRIM(Object... args) {
    Object text = Common.parseString(Js.arg(args, 0));
    if (text instanceof JsError) {
      return text;
    }
    String s = (String) text;
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < s.length()) {
      if (Js.isSpace(s.charAt(i))) {
        while (i < s.length() && Js.isSpace(s.charAt(i))) {
          i++;
        }
        out.append(' ');
      } else {
        out.append(s.charAt(i++));
      }
    }
    return Js.trim(out.toString());
  }

  /** Converts text to uppercase. */
  public static Object UPPER(Object... args) {
    Object text = Common.parseString(Js.arg(args, 0));
    if (text instanceof JsError) {
      return text;
    }
    return Js.toUpperCase((String) text);
  }

  /** Converts a text argument to a number. */
  public static Object VALUE(Object... args) {
    Object text = Js.arg(args, 0);
    Object anyError = Common.anyError(text);
    if (anyError != Js.UNDEFINED) {
      return anyError;
    }
    if (text instanceof Double) {
      return text;
    }
    if (!Common.isDefined(text)) {
      text = "";
    }
    if (!(text instanceof String s)) {
      return Errors.VALUE;
    }
    boolean isPercent = s.endsWith("%") || s.startsWith("%");
    int lead = 0;
    while (lead < 3 && lead < s.length() && !isDigit(s.charAt(lead)) && s.charAt(lead) != '-') {
      lead++;
    }
    s = s.substring(lead);
    int trail = 0;
    while (trail < 3 && trail < s.length() && !isDigit(s.charAt(s.length() - 1 - trail))) {
      trail++;
    }
    s = s.substring(0, s.length() - trail).replace(" ", "").replace(",", "");
    if (s.isEmpty()) {
      return 0.0;
    }
    double output = Js.stringToNumber(s);
    if (Double.isNaN(output)) {
      return Errors.VALUE;
    }
    if (output == 0) {
      output = 0;
    }
    if (isPercent) {
      output = output * 0.01;
    }
    return output;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  static void register(Map<String, Fn> ops) {
    ops.put("CHAR", Text::CHAR);
    ops.put("CLEAN", Text::CLEAN);
    ops.put("CODE", Text::CODE);
    ops.put("CONCAT", Text::CONCATENATE);
    ops.put("CONCATENATE", Text::CONCATENATE);
    ops.put("DOLLAR", Text::DOLLAR);
    ops.put("EXACT", Text::EXACT);
    ops.put("FIND", Text::FIND);
    ops.put("FIXED", Text::FIXED);
    ops.put("LEFT", Text::LEFT);
    ops.put("LEN", Text::LEN);
    ops.put("LOWER", Text::LOWER);
    ops.put("MID", Text::MID);
    ops.put("NUMBERVALUE", Text::NUMBERVALUE);
    ops.put("PROPER", Text::PROPER);
    ops.put("REPLACE", Text::REPLACE);
    ops.put("REPT", Text::REPT);
    ops.put("RIGHT", Text::RIGHT);
    ops.put("SEARCH", Text::SEARCH);
    ops.put("SUBSTITUTE", Text::SUBSTITUTE);
    ops.put("T", Text::T);
    ops.put("TEXT", Text::TEXT);
    ops.put("TEXTJOIN", Text::TEXTJOIN);
    ops.put("TRIM", Text::TRIM);
    ops.put("UNICHAR", Text::CHAR);
    ops.put("UNICODE", Text::CODE);
    ops.put("UPPER", Text::UPPER);
    ops.put("VALUE", Text::VALUE);
  }
}
