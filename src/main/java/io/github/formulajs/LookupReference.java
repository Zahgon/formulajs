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
import io.github.formulajs.js.JsException;
import io.github.formulajs.js.JsRegExp;
import io.github.formulajs.jstat.JStat;
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Lookup and reference functions. */
public final class LookupReference {
  private LookupReference() {}

  /** Chooses a value from a list of values. */
  public static Object CHOOSE(Object... args) {
    if (args.length < 2) {
      return Errors.NA;
    }
    Object index = args[0];
    if (Js.lt(index, 1.0) || Js.gt(index, 254.0)) {
      return Errors.VALUE;
    }
    if (Js.lt((double) args.length, Js.add(index, 1.0))) {
      return Errors.VALUE;
    }
    return Js.get(Arrays.asList(args), index);
  }

  /** The numbers following the array, flattened: {@code utils.flatten([first, ...rest])}. */
  private static List<Object> indexArgs(Object[] args) {
    List<Object> list = new ArrayList<>();
    list.add(Js.arg(args, 1));
    list.addAll(Arrays.asList(args).subList(Math.min(2, args.length), args.length));
    return Common.flatten(list);
  }

  /** Whether some index is missing or its absolute value is not within 1..size. */
  private static boolean notInRange(List<Object> indexes, double size) {
    for (Object index : indexes) {
      if (!Common.isDefined(index)) {
        return true;
      }
      double abs = Math.abs(Js.toNumber(index));
      if (!(0 < abs && abs <= size)) {
        return true;
      }
    }
    return false;
  }

  /** Negative indexes counted from the end: {@code n < 0 ? n + size + 1 : n}. */
  private static Object fromEnd(Object n, double size) {
    return Js.lt(n, 0.0) ? Js.add(Js.add(n, size), 1.0) : n;
  }

  /** Returns the specified columns from an array. */
  public static Object CHOOSECOLS(Object... args) {
    Object array = Js.arg(args, 0);
    if (!(array instanceof List<?> rows)) {
      return Errors.VALUE;
    }
    List<Object> colArgs = indexArgs(args);
    double[] size = Common.getMatrixSize(array);
    if (notInRange(colArgs, size[1])) {
      return Errors.VALUE;
    }
    List<Object> targets = new ArrayList<>();
    for (Object colNum : colArgs) {
      targets.add(fromEnd(colNum, size[1]));
    }
    List<Object> result = new ArrayList<>();
    for (int iRow = 0; iRow < size[0]; iRow++) {
      List<Object> row = new ArrayList<>();
      for (Object target : targets) {
        Object value = Js.get(rows.get(iRow), Js.toNumber(target) - 1);
        row.add(Js.isNullish(value) ? 0.0 : value);
      }
      result.add(row);
    }
    return result;
  }

  /** Returns the specified rows from an array. */
  public static Object CHOOSEROWS(Object... args) {
    Object array = Js.arg(args, 0);
    if (!(array instanceof List<?> rows)) {
      return Errors.VALUE;
    }
    List<Object> rowArgs = indexArgs(args);
    if (notInRange(rowArgs, rows.size())) {
      return Errors.VALUE;
    }
    List<Object> selected = new ArrayList<>();
    for (Object rowNum : rowArgs) {
      Object row = Js.get(array, Js.toNumber(fromEnd(rowNum, rows.size())) - 1);
      List<Object> cloned = new ArrayList<>();
      if (row instanceof List<?> list) {
        cloned.addAll(list);
      } else if (row instanceof String s) {
        s.codePoints().forEach(cp -> cloned.add(new String(Character.toChars(cp))));
      } else {
        throw JsException.notIterable("array[(--row_num_selection)]");
      }
      for (int i = 0; i < cloned.size(); i++) {
        if (!Common.isDefined(cloned.get(i))) {
          cloned.set(i, 0.0);
        }
      }
      selected.add(cloned);
    }
    return selected;
  }

  /** Returns the column number of a reference. */
  public static Object COLUMN(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object reference = args[0];
    Object index = args[1];
    if (Js.lt(index, 0.0)) {
      return Errors.NUM;
    }
    if (!(reference instanceof List<?> list) || !(index instanceof Double)) {
      return Errors.VALUE;
    }
    if (list.isEmpty()) {
      return Js.UNDEFINED;
    }
    return JStat.col(reference, index);
  }

  /** Returns the number of columns in a reference. */
  public static Object COLUMNS(Object... args) {
    if (args.length != 1) {
      return Errors.NA;
    }
    if (!(args[0] instanceof List<?> list)) {
      return Errors.VALUE;
    }
    if (list.isEmpty()) {
      return 0.0;
    }
    return JStat.cols(list);
  }

  /** Start and end indexes kept by DROP for one dimension, or null for #CALC!. */
  private static double[] dropBounds(Object count, double size) {
    double start = 0;
    double end = size;
    if (count instanceof Double n) {
      if (Math.abs(n) > size) {
        return null;
      }
      if (n > 0) {
        start = n;
      } else if (n < 0) {
        end += n;
      }
    }
    return new double[] {start, end};
  }

  /** Excludes a specified number of rows or columns from the start or end of an array. */
  public static Object DROP(Object... args) {
    Object array = Js.arg(args, 0);
    Object rows = Js.arg(args, 1);
    Object columns = Js.arg(args, 2);
    if (Common.anyIsError(array, rows, columns)) {
      return Common.anyError(array, rows, columns);
    }
    if (!(array instanceof List)) {
      return Errors.VALUE;
    }
    double[] size = Common.getMatrixSize(array);
    double[] rowBounds = dropBounds(rows, size[0]);
    if (rowBounds == null) {
      return Errors.CALC;
    }
    double[] colBounds = dropBounds(columns, size[1]);
    if (colBounds == null) {
      return Errors.CALC;
    }
    List<Object> result = new ArrayList<>();
    for (double iRow = rowBounds[0]; iRow < rowBounds[1]; iRow++) {
      Object row = Js.get(array, iRow);
      if (Js.isNullish(row)) {
        throw JsException.cannotRead(row, "slice");
      }
      if (row instanceof String) {
        throw JsException.typeError("array[iRow].slice.map is not a function or its return value is not iterable");
      }
      if (!(row instanceof List<?> list)) {
        throw JsException.notAFunction("array[iRow].slice");
      }
      int from = Js.relativeIndex(colBounds[0], list.size());
      int to = Js.relativeIndex(colBounds[1], list.size());
      List<Object> cloned = new ArrayList<>();
      for (int i = from; i < to; i++) {
        Object value = list.get(i);
        cloned.add(Js.isNullish(value) ? 0.0 : value);
      }
      result.add(cloned);
    }
    return result;
  }

  /** {@code array[iRow]?.[iCol]}. */
  private static Object optionalCell(Object array, double iRow, double iCol) {
    Object row = Js.get(array, iRow);
    return Js.isNullish(row) ? Js.UNDEFINED : Js.get(row, iCol);
  }

  /** {@code matrix[iRow][iCol] = value} on a matrix of arrays. */
  private static void setCell(Object matrix, double iRow, double iCol, Object value) {
    Object row = Js.get(matrix, iRow);
    if (!(row instanceof List)) {
      throw JsException.cannotSet(row, iCol);
    }
    Js.set(Js.asList(row), (int) iCol, value);
  }

  /** Expands or pads an array to specified row and column dimensions. */
  public static Object EXPAND(Object... args) {
    Object array = Js.arg(args, 0);
    Object rows = Js.arg(args, 1);
    Object columns = Js.arg(args, 2);
    Object padWith = Js.arg(args, 3);
    double[] size = Common.getMatrixSize(array);
    if (!Common.isDefined(rows)) {
      rows = size[0];
    }
    if (!Common.isDefined(columns)) {
      columns = size[1];
    }
    if (Js.lt(rows, size[0]) || Js.lt(columns, size[1])) {
      return Errors.VALUE;
    }
    Object result = Common.createMatrix(rows, columns);
    for (double iRow = 0; Js.lt(iRow, rows); iRow++) {
      for (double iCol = 0; Js.lt(iCol, columns); iCol++) {
        Object value = optionalCell(array, iRow, iCol);
        if (!Common.isDefined(value)) {
          value = iRow < size[0] && iCol < size[1] ? 0.0 : Js.isNullish(padWith) ? Errors.NA : padWith;
        }
        setCell(result, iRow, iCol, value);
      }
    }
    return result;
  }

  /** Looks in the top row of an array and returns the value of the indicated value. */
  public static Object HLOOKUP(Object... args) {
    return VLOOKUP(Js.arg(args, 0), Common.transpose(Js.arg(args, 1)), Js.arg(args, 2), Js.arg(args, 3));
  }

  /** Uses an index to choose a value from a reference or array. */
  public static Object INDEX(Object... args) {
    Object array = Js.arg(args, 0);
    Object rowNum = Js.arg(args, 1);
    Object columnNum = Js.arg(args, 2);
    Object someError = Common.anyError(array, rowNum, columnNum);
    if (someError != Js.UNDEFINED) {
      return someError;
    }
    if (!(array instanceof List<?> list)) {
      return Errors.VALUE;
    }
    boolean oneDimension = !list.isEmpty() && !(list.get(0) instanceof List);
    if (oneDimension && !Js.truthy(columnNum)) {
      columnNum = rowNum;
      rowNum = 1.0;
    } else {
      columnNum = Js.truthy(columnNum) ? columnNum : 1.0;
      rowNum = Js.truthy(rowNum) ? rowNum : 1.0;
    }
    if (Js.lt(columnNum, 0.0) || Js.lt(rowNum, 0.0)) {
      return Errors.VALUE;
    }
    if (oneDimension && Js.strictEquals(rowNum, 1.0) && Js.le(columnNum, (double) list.size())) {
      return Js.get(array, Js.toNumber(columnNum) - 1);
    } else if (Js.le(rowNum, (double) list.size())
        && Js.le(columnNum, Js.length(Js.get(array, Js.toNumber(rowNum) - 1)))) {
      return Js.get(Js.get(array, Js.toNumber(rowNum) - 1), Js.toNumber(columnNum) - 1);
    }
    return Errors.REF;
  }

  /** Looks up values in a vector or array. */
  public static Object LOOKUP(Object... args) {
    Object lookupValue = Js.arg(args, 0);
    List<Object> array = Common.flatten(Js.arg(args, 1));
    Object resultArg = Js.arg(args, 2);
    List<Object> resultArray = Js.truthy(resultArg) ? Common.flatten(resultArg) : array;
    boolean isNumberLookup = lookupValue instanceof Double;
    Object result = Errors.NA;
    for (int i = 0; i < array.size(); i++) {
      Object item = array.get(i);
      if (Js.strictEquals(item, lookupValue)) {
        return Js.get(resultArray, (double) i);
      } else if ((isNumberLookup && Js.le(item, lookupValue))
          || (item instanceof String s && Js.localeCompare(s, Js.str(lookupValue)) < 0)) {
        result = Js.get(resultArray, (double) i);
      } else if (isNumberLookup && Js.gt(item, lookupValue)) {
        return result;
      }
    }
    return result;
  }

  /** Looks up values in a reference or array. */
  public static Object MATCH(Object... args) {
    Object lookupValue = Js.arg(args, 0);
    Object lookupArray = Js.arg(args, 1);
    Object matchType = Js.arg(args, 2);
    if ((!Js.truthy(lookupValue) && !Js.strictEquals(lookupValue, 0.0)) || !Js.truthy(lookupArray)) {
      return Errors.NA;
    }
    if (args.length == 2) {
      matchType = 1.0;
    }
    List<Object> array = Common.flatten(lookupArray);
    if (!Js.strictEquals(matchType, -1.0) && !Js.strictEquals(matchType, 0.0)
        && !Js.strictEquals(matchType, 1.0)) {
      return Errors.NA;
    }
    double type = (Double) matchType;
    Object index = Js.UNDEFINED;
    Object indexValue = Js.UNDEFINED;
    for (int idx = 0; idx < array.size(); idx++) {
      Object item = array.get(idx);
      if (type == 1) {
        if (Js.strictEquals(item, lookupValue)) {
          return idx + 1.0;
        } else if (Js.lt(item, lookupValue)) {
          if (!Js.truthy(indexValue) || Js.gt(item, indexValue)) {
            index = idx + 1.0;
            indexValue = item;
          }
        }
      } else if (type == 0) {
        if (lookupValue instanceof String s && item instanceof String text) {
          String pattern = Js.toLowerCase(s).replace("?", ".").replace("*", ".*").replace("~", "\\")
              .replace("+", "\\+").replace("(", "\\(").replace(")", "\\)").replace("[", "\\[").replace("]", "\\]");
          if (JsRegExp.test("^" + pattern + "$", Js.toLowerCase(text))) {
            return idx + 1.0;
          }
        } else if (Js.strictEquals(item, lookupValue)) {
          return idx + 1.0;
        }
      } else {
        if (Js.strictEquals(item, lookupValue)) {
          return idx + 1.0;
        } else if (Js.gt(item, lookupValue)) {
          if (!Js.truthy(indexValue) || Js.lt(item, indexValue)) {
            index = idx + 1.0;
            indexValue = item;
          }
        }
      }
    }
    return Js.truthy(index) ? index : Errors.NA;
  }

  /** Returns the number of rows in a reference. */
  public static Object ROWS(Object... args) {
    if (args.length != 1) {
      return Errors.NA;
    }
    if (!(args[0] instanceof List<?> list)) {
      return Errors.VALUE;
    }
    if (list.isEmpty()) {
      return 0.0;
    }
    return JStat.rows(list);
  }

  /** Returns a sorted array of the elements in an array, the same shape as the provided array. */
  public static Object SORT(Object... args) {
    Object array = Js.arg(args, 0);
    if (!Js.truthy(array) || !(array instanceof List<?> list)) {
      return Errors.NA;
    }
    if (list.isEmpty()) {
      return 0.0;
    }
    Object sortIndex = Common.parseNumber(Js.arg(args, 1, 1.0));
    if (!Js.truthy(sortIndex) || Js.lt(sortIndex, 1.0)) {
      return Errors.VALUE;
    }
    Object sortOrder = Common.parseNumber(Js.arg(args, 2, 1.0));
    if (!Js.strictEquals(sortOrder, 1.0) && !Js.strictEquals(sortOrder, -1.0)) {
      return Errors.VALUE;
    }
    Object byCol = Common.parseBool(Js.arg(args, 3, false));
    if (!(byCol instanceof Boolean)) {
      return Errors.NAME;
    }
    double order = (Double) sortOrder;
    Object matrix = Common.fillMatrix(array, Js.UNDEFINED);
    Object result = (Boolean) byCol ? Common.transpose(matrix) : matrix;
    if (!Js.ge(sortIndex, 1.0) || !Js.le(sortIndex, Js.length(Js.get(result, 0.0)))) {
      return Errors.VALUE;
    }
    double column = Js.toNumber(sortIndex) - 1;
    List<Object> sorted = Js.sort(Js.asList(result), (a, b) -> {
      Object x = Common.parseString(Js.get(a, column));
      Object y = Common.parseString(Js.get(b, column));
      return order == 1 ? (Js.lt(x, y) ? order * -1 : order) : Js.gt(x, y) ? order : order * -1;
    });
    return (Boolean) byCol ? Common.transpose(sorted) : sorted;
  }

  /** Start and end (inclusive) indexes kept by TAKE for one dimension, or null for #CALC!. */
  private static double[] takeBounds(Object count, double size) {
    double start = 0;
    double end = size - 1;
    if (count instanceof Double n) {
      if (n == 0) {
        return null;
      }
      if (Math.abs(n) <= size) {
        if (n < 0) {
          start = size + n;
        } else {
          end = n - 1;
        }
      }
    }
    return new double[] {start, end};
  }

  /** Returns a specified number of contiguous rows or columns from the start or end of an array. */
  public static Object TAKE(Object... args) {
    Object array = Js.arg(args, 0);
    Object rows = Js.arg(args, 1);
    Object columns = Js.arg(args, 2);
    if (Common.anyIsError(array, rows, columns)) {
      return Common.anyError(array, rows, columns);
    }
    if (!(array instanceof List)) {
      return Errors.VALUE;
    }
    double[] size = Common.getMatrixSize(array);
    double[] r = takeBounds(rows, size[0]);
    if (r == null) {
      return Errors.CALC;
    }
    double[] c = takeBounds(columns, size[1]);
    if (c == null) {
      return Errors.CALC;
    }
    Object result = Common.createMatrix(r[1] - r[0] + 1, c[1] - c[0] + 1);
    double iRowResult = -1;
    for (double iRow = r[0]; iRow <= r[1]; iRow++) {
      iRowResult++;
      double iColResult = -1;
      for (double iCol = c[0]; iCol <= c[1]; iCol++) {
        Object value = optionalCell(array, iRow, iCol);
        setCell(result, iRowResult, ++iColResult, Js.isNullish(value) ? 0.0 : value);
      }
    }
    return result;
  }

  /** Returns the transpose of an array. */
  public static Object TRANSPOSE(Object... args) {
    Object array = Js.arg(args, 0);
    if (!Js.truthy(array)) {
      return Errors.NA;
    }
    return Common.transpose(Common.fillMatrix(array, Js.UNDEFINED));
  }

  /** Returns a list of unique values in a list or range. */
  public static Object UNIQUE(Object... args) {
    List<Object> result = new ArrayList<>();
    for (Object element : args) {
      if (Js.indexOf(result, element) < 0) {
        result.add(element);
      }
    }
    return result;
  }

  /** Looks in the first column of an array and moves across the row to return the value of a value. */
  public static Object VLOOKUP(Object... args) {
    Object lookupValue = Js.arg(args, 0);
    Object tableArray = Js.arg(args, 1);
    Object colIndexNum = Js.arg(args, 2);
    Object rangeLookupArg = Js.arg(args, 3);
    if (!Js.truthy(tableArray) || !Js.truthy(colIndexNum)) {
      return Errors.NA;
    }
    boolean rangeLookup = !(Js.strictEquals(rangeLookupArg, 0.0) || Boolean.FALSE.equals(rangeLookupArg));
    Object result = Errors.NA;
    boolean exactMatchOnly = false;
    boolean isNumberLookup = lookupValue instanceof Double;
    Object lowered = lookupValue instanceof String s ? Js.toLowerCase(s) : lookupValue;
    double length = Js.toNumber(Js.length(tableArray));
    for (int i = 0; i < length; i++) {
      Object row = Js.get(tableArray, (double) i);
      Object first = Js.get(row, 0.0);
      Object rowValue = first instanceof String s ? Js.toLowerCase(s) : first;
      if (Js.strictEquals(rowValue, lowered)) {
        result = cell(row, colIndexNum);
        break;
      } else if (!exactMatchOnly && ((isNumberLookup && rangeLookup && Js.le(rowValue, lookupValue))
          || (rangeLookup && rowValue instanceof String s && Js.localeCompare(s, Js.str(lookupValue)) < 0))) {
        result = cell(row, colIndexNum);
      }
      if (isNumberLookup && Js.gt(rowValue, lookupValue)) {
        exactMatchOnly = true;
      }
    }
    return result;
  }

  /** {@code col_index_num < row.length + 1 ? row[col_index_num - 1] : error.ref}. */
  private static Object cell(Object row, Object colIndexNum) {
    if (Js.lt(colIndexNum, Js.add(Js.length(row), 1.0))) {
      return Js.get(row, Js.toNumber(colIndexNum) - 1);
    }
    return Errors.REF;
  }

  /** Appends arrays horizontally and in sequence to return a larger array. */
  public static Object HSTACK(Object... args) {
    List<Object> arrays = stackArgs(args);
    double maxRowCount = 0;
    double totalColCount = 0;
    double[] colsPerArray = new double[arrays.size()];
    for (int i = 0; i < arrays.size(); i++) {
      double[] size = Common.getMatrixSize(arrays.get(i));
      maxRowCount = Math.max(maxRowCount, size[0]);
      totalColCount += size[1];
      colsPerArray[i] = size[1];
    }
    List<Object> result = new ArrayList<>();
    for (int i = 0; i < maxRowCount; i++) {
      result.add(Js.newArray(totalColCount));
    }
    int iColResult = 0;
    for (int iArr = 0; iArr < arrays.size(); iArr++) {
      Object current = arrays.get(iArr);
      for (int iCol = 0; iCol < colsPerArray[iArr]; iCol++) {
        for (int iRow = 0; iRow < maxRowCount; iRow++) {
          Object value = optionalCell(current, iRow, iCol);
          if (Js.isNullish(value)) {
            value = Js.lt((double) iRow, Js.length(current)) ? 0.0 : Errors.NA;
          }
          Js.set(Js.asList(result.get(iRow)), iColResult, value);
        }
        iColResult++;
      }
    }
    return result;
  }

  private static List<Object> stackArgs(Object[] args) {
    List<Object> arrays = new ArrayList<>();
    arrays.add(Js.arg(args, 0));
    arrays.addAll(Arrays.asList(args).subList(Math.min(1, args.length), args.length));
    return arrays;
  }

  /** Appends arrays vertically and in sequence to return a larger array. */
  public static Object VSTACK(Object... args) {
    List<Object> arrays = stackArgs(args);
    double maxColCount = 0;
    double totalRowCount = 0;
    for (Object current : arrays) {
      double[] size = Common.getMatrixSize(current);
      maxColCount = Math.max(maxColCount, size[1]);
      totalRowCount += size[0];
    }
    List<Object> result = Js.newArray(totalRowCount);
    int iRowResult = -1;
    for (Object current : arrays) {
      if (current == Js.UNDEFINED) {
        break;
      }
      double rowCount = Js.toNumber(Js.length(current));
      for (int iRow = 0; iRow < rowCount; iRow++) {
        List<Object> row = Js.newArray(maxColCount);
        Js.set(result, ++iRowResult, row);
        Object source = Js.get(current, (double) iRow);
        for (int iCol = 0; iCol < maxColCount; iCol++) {
          Object value = Js.get(source, (double) iCol);
          if (Js.isNullish(value)) {
            value = Js.lt((double) iCol, Js.length(source)) ? 0.0 : Errors.NA;
          }
          row.set(iCol, value);
        }
      }
    }
    return result;
  }

  static void register(Map<String, Fn> ops) {
    ops.put("CHOOSE", LookupReference::CHOOSE);
    ops.put("CHOOSECOLS", LookupReference::CHOOSECOLS);
    ops.put("CHOOSEROWS", LookupReference::CHOOSEROWS);
    ops.put("COLUMN", LookupReference::COLUMN);
    ops.put("COLUMNS", LookupReference::COLUMNS);
    ops.put("DROP", LookupReference::DROP);
    ops.put("EXPAND", LookupReference::EXPAND);
    ops.put("HLOOKUP", LookupReference::HLOOKUP);
    ops.put("HSTACK", LookupReference::HSTACK);
    ops.put("INDEX", LookupReference::INDEX);
    ops.put("LOOKUP", LookupReference::LOOKUP);
    ops.put("MATCH", LookupReference::MATCH);
    ops.put("ROWS", LookupReference::ROWS);
    ops.put("SORT", LookupReference::SORT);
    ops.put("TAKE", LookupReference::TAKE);
    ops.put("TRANSPOSE", LookupReference::TRANSPOSE);
    ops.put("UNIQUE", LookupReference::UNIQUE);
    ops.put("VLOOKUP", LookupReference::VLOOKUP);
    ops.put("VSTACK", LookupReference::VSTACK);
  }
}
