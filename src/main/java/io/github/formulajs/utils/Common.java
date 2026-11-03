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
package io.github.formulajs.utils;

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsDate;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * utils/common.js. The helpers keep the original's effects on their arguments: several of them convert or flatten
 * arrays in place, and {@link #flattenShallow} appends to the first nested array it meets.
 */
public final class Common {
  private Common() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Arrays

  /** A function's {@code arguments} object, for the helpers that take it as one array-like argument. */
  public static List<Object> arguments(Object[] args) {
    return Arrays.asList(args);
  }

  public static List<Object> argsToArray(List<?> args) {
    return new ArrayList<>(args);
  }

  public static List<Object> arrayValuesToNumbers(List<Object> arr) {
    for (int n = arr.size() - 1; n >= 0; n--) {
      Object el = arr.get(n);
      if (el instanceof Double) {
        continue;
      }
      if (Boolean.TRUE.equals(el)) {
        arr.set(n, 1.0);
        continue;
      }
      if (Boolean.FALSE.equals(el)) {
        arr.set(n, 0.0);
        continue;
      }
      if (el instanceof String) {
        Object number = parseNumber(el);
        arr.set(n, number instanceof JsError ? 0.0 : number);
      }
    }
    return arr;
  }

  /** [rowCount, columnCount] of a matrix given as an array of rows. */
  public static double[] getMatrixSize(Object array) {
    double rowSize = Js.toNumber(Js.length(array));
    if (!(array instanceof List<?> rows)) {
      throw JsException.notAFunction("array.reduce");
    }
    double columnSize = 0;
    for (Object row : rows) {
      columnSize = Math.max(columnSize, Js.toNumber(Js.length(row)));
    }
    return new double[] {rowSize, columnSize};
  }

  /** A rowSize x columnSize matrix of undefined, #N/A when a size is missing, #VALUE! when one is not positive. */
  public static Object createMatrix(Object rowSize, Object columnSize) {
    if (!isDefined(rowSize) || !isDefined(columnSize)) {
      return Errors.NA;
    }
    if (Js.le(rowSize, 0.0) || Js.le(columnSize, 0.0)) {
      return Errors.VALUE;
    }
    int rows = toLength(rowSize);
    int columns = toLength(columnSize);
    List<Object> matrix = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      matrix.add(Js.newArray(columns));
    }
    return matrix;
  }

  /** ToLength, as Array.from reads a length. */
  private static int toLength(Object value) {
    double n = Js.toIntegerOrInfinity(Js.toNumber(value));
    if (n <= 0) {
      return 0;
    }
    if (n > 4294967295.0) {
      throw JsException.rangeError("Invalid array length");
    }
    return (int) n;
  }

  public static Object fillMatrix(Object matrixArg, Object fillValue) {
    if (!Js.truthy(matrixArg)) {
      return Errors.VALUE;
    }
    if (!(matrixArg instanceof List)) {
      throw JsException.notAFunction("matrix.every");
    }
    List<Object> matrix = Js.asList(matrixArg);
    boolean allArrays = true;
    for (Object el : matrix) {
      if (!(el instanceof List)) {
        allArrays = false;
        break;
      }
    }
    if (!allArrays || matrix.isEmpty()) {
      matrix = Js.list(new ArrayList<>(matrix));
    }
    for (Object arr : matrix) {
      List<Object> row = Js.asList(arr);
      for (int j = 0; j < row.size(); j++) {
        if (!Js.truthy(row.get(j))) {
          row.set(j, 0.0);
        }
      }
    }
    int longest = 0;
    for (int i = 0; i < matrix.size(); i++) {
      if (Js.asList(matrix.get(i)).size() > Js.asList(matrix.get(longest)).size()) {
        longest = i;
      }
    }
    int longestLength = Js.asList(matrix.get(longest)).size();
    Object fill = Js.truthy(fillValue) ? fillValue : 0.0;
    List<Object> out = new ArrayList<>();
    for (Object el : matrix) {
      List<Object> row = new ArrayList<>(Js.asList(el));
      while (row.size() < longestLength) {
        row.add(fill);
      }
      out.add(row);
    }
    return out;
  }

  /** flatten(...arguments): one array-like argument is copied, several are collected, then nested arrays opened. */
  public static List<Object> flatten(Object... args) {
    List<Object> result;
    if (args.length == 1) {
      Object argument = args[0];
      result = isArrayLike(argument) ? argsToArray(Js.asList(argument)) : Js.list(argument);
    } else {
      result = Js.list(args);
    }
    while (!isFlat(result)) {
      result = flattenShallow(result);
    }
    return result;
  }

  /** One level of flattening by reduce, which appends into the first nested array when it leads. */
  public static List<Object> flattenShallow(Object array) {
    if (!(array instanceof List<?>)) {
      return Js.list(array);
    }
    List<Object> list = Js.asList(array);
    if (list.isEmpty()) {
      throw JsException.typeError("Reduce of empty array with no initial value");
    }
    Object acc = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      Object b = list.get(i);
      boolean aIsArray = acc instanceof List;
      boolean bIsArray = b instanceof List;
      if (aIsArray && bIsArray) {
        List<Object> joined = new ArrayList<>(Js.asList(acc));
        joined.addAll(Js.asList(b));
        acc = joined;
      } else if (aIsArray) {
        Js.asList(acc).add(b);
      } else if (bIsArray) {
        List<Object> joined = Js.list(acc);
        joined.addAll(Js.asList(b));
        acc = joined;
      } else {
        acc = Js.list(acc, b);
      }
    }
    if (acc instanceof List) {
      return Js.asList(acc);
    }
    return Js.list(acc);
  }

  public static Object initial(Object array, Object idx) {
    double n = Js.truthy(idx) ? Js.toNumber(idx) : 1;
    if (array instanceof List<?> list) {
      return new ArrayList<Object>(list.subList(0, Js.relativeIndex(list.size() - n, list.size())));
    }
    if (array instanceof String s) {
      return Js.slice(s, 0.0, s.length() - n);
    }
    return array;
  }

  public static boolean isArrayLike(Object a) {
    return a instanceof List;
  }

  public static boolean isFlat(Object array) {
    if (!Js.truthy(array)) {
      return false;
    }
    if (array instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof List) {
          return false;
        }
      }
    }
    return true;
  }

  public static Object rest(Object array, Object idx) {
    double n = Js.truthy(idx) ? Js.toNumber(idx) : 1;
    if (array instanceof List<?> list) {
      return new ArrayList<Object>(list.subList(Js.relativeIndex(n, list.size()), list.size()));
    }
    if (array instanceof String s) {
      return Js.slice(s, n, Js.UNDEFINED);
    }
    return array;
  }

  /** The transposed matrix (rows of the result are the columns of the first row's length). */
  public static Object transpose(Object matrix) {
    if (!Js.truthy(matrix)) {
      return Errors.VALUE;
    }
    Object first = Js.get(matrix, 0.0);
    if (!(first instanceof List<?> firstRow)) {
      if (Js.isNullish(first)) {
        throw JsException.cannotRead(first, "map");
      }
      throw JsException.notAFunction("matrix[0].map");
    }
    List<Object> rows = Js.asList(matrix);
    List<Object> out = new ArrayList<>();
    for (int i = 0; i < firstRow.size(); i++) {
      List<Object> column = new ArrayList<>();
      for (Object row : rows) {
        column.add(Js.get(row, (double) i));
      }
      out.add(column);
    }
    return out;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Databases

  public static Object findField(Object database, Object title) {
    double length = Js.toNumber(Js.length(database));
    for (int i = 0; i < length; i++) {
      if (Js.strictEquals(Js.get(Js.get(database, (double) i), 0.0), title)) {
        return (double) i;
      }
    }
    return Errors.VALUE;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Errors

  public static Object anyError(Object... args) {
    for (Object arg : args) {
      if (arg instanceof JsError) {
        return arg;
      }
    }
    return Js.UNDEFINED;
  }

  public static boolean anyIsError(Object... args) {
    for (Object arg : args) {
      if (arg instanceof JsError) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Numbers

  public static double cleanFloat(double number) {
    double power = 1e14;
    return Js.round(number * power) / power;
  }

  public static List<Object> numbers(Object... args) {
    List<Object> out = new ArrayList<>();
    for (Object el : flatten(args)) {
      if (el instanceof Double) {
        out.add(el);
      }
    }
    return out;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Parsers

  public static Object parseBool(Object bool) {
    if (bool instanceof Boolean || bool instanceof JsError) {
      return bool;
    }
    if (bool instanceof Double d) {
      return d != 0;
    }
    if (bool instanceof String s) {
      String up = Js.toUpperCase(s);
      if (up.equals("TRUE")) {
        return true;
      }
      if (up.equals("FALSE")) {
        return false;
      }
    }
    if (bool instanceof JsDate d && d.isValid()) {
      return true;
    }
    return Errors.VALUE;
  }

  public static Object parseDate(Object date) {
    if (!Js.isNaN(date)) {
      if (date instanceof JsDate d) {
        return JsDate.of(d);
      }
      double d = Js.parseFloat(date);
      if (d < 0 || d >= 2958466) {
        return Errors.NUM;
      }
      return DateUtils.serialToDate(d);
    }
    if (date instanceof String s) {
      JsDate parsed = endsWithIsoLikeDate(s) ? JsDate.of(s + "T00:00:00.000") : JsDate.of(s);
      if (parsed.isValid()) {
        return parsed;
      }
    }
    return Errors.VALUE;
  }

  /** {@code /(\d{4})-(\d\d?)-(\d\d?)$/.test(s)}. */
  private static boolean endsWithIsoLikeDate(String s) {
    int end = s.length();
    for (int dayDigits = 1; dayDigits <= 2; dayDigits++) {
      for (int monthDigits = 1; monthDigits <= 2; monthDigits++) {
        int start = end - dayDigits - 1 - monthDigits - 1 - 4;
        if (start < 0) {
          continue;
        }
        if (digits(s, start, 4) && s.charAt(start + 4) == '-' && digits(s, start + 5, monthDigits)
            && s.charAt(start + 5 + monthDigits) == '-' && digits(s, end - dayDigits, dayDigits)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean digits(String s, int from, int count) {
    for (int i = from; i < from + count; i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  public static Object parseDateArray(List<Object> arr) {
    for (int len = arr.size() - 1; len >= 0; len--) {
      Object parsed = parseDate(arr.get(len));
      if (parsed == Errors.VALUE) {
        return parsed;
      }
      arr.set(len, parsed);
    }
    return arr;
  }

  public static Object parseMatrix(Object matrix) {
    if (!Js.truthy(matrix)) {
      return Errors.VALUE;
    }
    double length = Js.toNumber(Js.length(matrix));
    for (int i = 0; i < length; i++) {
      Object pnarr = parseNumberArray(Js.get(matrix, (double) i));
      assign(matrix, i, pnarr);
      if (pnarr instanceof JsError) {
        return pnarr;
      }
    }
    return matrix;
  }

  /** {@code target[index] = value} in strict mode: arrays take it, strings refuse it. */
  private static void assign(Object target, int index, Object value) {
    if (target instanceof List) {
      Js.set(Js.asList(target), index, value);
    } else if (target instanceof String s) {
      throw JsException.typeError("Cannot assign to read only property '" + index + "' of string '" + s + "'");
    }
  }

  public static Object parseNumber(Object string) {
    if (string instanceof JsError) {
      return string;
    }
    if (Js.isNullish(string)) {
      return 0.0;
    }
    if (string instanceof Boolean b) {
      string = b ? 1.0 : 0.0;
    }
    if (!Js.isNaN(string) && !"".equals(string)) {
      return Js.parseFloat(string);
    }
    return Errors.VALUE;
  }

  public static Object parseNumberArray(Object arr) {
    if (!Js.truthy(arr)) {
      return Errors.VALUE;
    }
    Object lengthValue = Js.length(arr);
    if (Js.strictEquals(lengthValue, 0.0)) {
      return Errors.VALUE;
    }
    double len = Js.toNumber(lengthValue);
    for (int i = (int) (len > 0 ? len : 0) - 1; i >= 0; i--) {
      Object item = Js.get(arr, (double) i);
      if (item instanceof JsError) {
        return item;
      }
      Object parsed = parseNumber(item);
      if (parsed instanceof JsError) {
        return parsed;
      }
      assign(arr, i, parsed);
    }
    return arr;
  }

  public static Object parseString(Object string) {
    if (string instanceof JsError) {
      return string;
    }
    if (Js.isNullish(string)) {
      return "";
    }
    return Js.str(string);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Strings

  public static boolean anyIsString(Object... args) {
    for (Object arg : args) {
      if (arg instanceof String) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Misc

  /** The values of the first range whose rows meet every (range, criteria) pair that follows. */
  public static Object applyCriteria(Object... arguments) {
    List<Object> args = new ArrayList<>(Arrays.asList(arguments));
    Object first = args.isEmpty() ? Js.UNDEFINED : args.remove(0);
    Object range = parseNumberArray(flatten(first));
    if (range instanceof JsError) {
      return range;
    }
    List<Object> rangeList = Js.asList(range);
    List<Object> criterias = args;
    double criteriaLength = criterias.size() / 2.0;
    for (int i = 0; i < criteriaLength; i++) {
      criterias.set(i * 2, flatten(criterias.get(i * 2)));
    }
    List<Object> values = new ArrayList<>();
    for (int i = 0; i < rangeList.size(); i++) {
      boolean isMetCondition = false;
      for (int j = 0; j < criteriaLength; j++) {
        Object valueToTest = Js.get(criterias.get(j * 2), (double) i);
        Object criteria = Js.get(criterias, (double) (j * 2 + 1));
        boolean isWildcard = criteria == Js.UNDEFINED || "*".equals(criteria);
        boolean computedResult;
        if (isWildcard) {
          computedResult = true;
        } else {
          List<CriteriaEval.Token> tokens = new ArrayList<>();
          tokens.add(CriteriaEval.createToken(valueToTest, CriteriaEval.TOKEN_TYPE_LITERAL));
          tokens.addAll(CriteriaEval.parse(Js.str(Js.add(criteria, ""))));
          computedResult = CriteriaEval.compute(tokens);
        }
        if (!computedResult) {
          isMetCondition = false;
          break;
        }
        isMetCondition = true;
      }
      if (isMetCondition) {
        values.add(rangeList.get(i));
      }
    }
    return values;
  }

  public static boolean isDefined(Object arg) {
    return !Js.isNullish(arg);
  }
}
