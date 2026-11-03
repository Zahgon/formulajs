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
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Database functions. */
public final class Database {
  private Database() {}

  /** The indexes of the records that meet the criteria, and the values of the field (rest of its column). */
  private record Selection(List<Object> indexes, Object fields) {
    Object at(Object index) {
      return Js.get(fields, index);
    }

    List<Object> values() {
      List<Object> values = new ArrayList<>();
      for (Object index : indexes) {
        values.add(at(index));
      }
      return values;
    }
  }

  /** The common start of the D functions: null when the field is neither a number nor a string. */
  private static Selection select(Object[] args) {
    Object database = Js.arg(args, 0);
    Object field = Js.arg(args, 1);
    if (Js.isNaN(field) && !(field instanceof String)) {
      return null;
    }
    List<Object> indexes = findResultIndex(database, Js.arg(args, 2));
    Object key = field instanceof String ? Common.findField(database, field) : field;
    return new Selection(indexes, Common.rest(Js.get(database, key), Js.UNDEFINED));
  }

  private static double len(Object value) {
    return Js.toNumber(Js.length(value));
  }

  private static List<Object> findResultIndex(Object database, Object criterias) {
    Map<Integer, Boolean> matches = new HashMap<>();
    for (int i = 1; i < len(Js.get(database, 0.0)); ++i) {
      matches.put(i, true);
    }
    Object maxCriteriaLength = Js.length(Js.get(criterias, 0.0));
    for (int i = 1; i < len(criterias); ++i) {
      Object length = Js.length(Js.get(criterias, (double) i));
      if (Js.gt(length, maxCriteriaLength)) {
        maxCriteriaLength = length;
      }
    }
    for (int k = 1; k < len(database); ++k) {
      Object column = Js.get(database, (double) k);
      for (int l = 1; l < len(column); ++l) {
        boolean currentCriteriaResult = false;
        boolean hasMatchingCriteria = false;
        for (int j = 0; j < len(criterias); ++j) {
          Object criteria = Js.get(criterias, (double) j);
          if (Js.lt(Js.length(criteria), maxCriteriaLength)) {
            continue;
          }
          Object criteriaField = Js.get(criteria, 0.0);
          if (!Js.strictEquals(Js.get(column, 0.0), criteriaField)) {
            continue;
          }
          hasMatchingCriteria = true;
          for (int p = 1; p < len(criteria); ++p) {
            if (!currentCriteriaResult) {
              Object c = Js.get(criteria, (double) p);
              currentCriteriaResult = c == Js.UNDEFINED || "*".equals(c)
                  || MathTrig.criteriaMet(Js.get(column, (double) l), c);
            }
          }
        }
        if (hasMatchingCriteria && Boolean.TRUE.equals(matches.get(l))) {
          matches.put(l, currentCriteriaResult);
        }
      }
    }
    List<Object> result = new ArrayList<>();
    for (int n = 0; n < len(Js.get(database, 0.0)); ++n) {
      if (Boolean.TRUE.equals(matches.get(n))) {
        result.add((double) (n - 1));
      }
    }
    return result;
  }

  private static List<Object> compact(List<Object> array) {
    List<Object> result = new ArrayList<>();
    for (Object value : array) {
      if (Js.truthy(value)) {
        result.add(value);
      }
    }
    return result;
  }

  /** Returns the average of selected database entries. */
  public static Object DAVERAGE(Object... args) {
    Selection s = select(args);
    if (s == null) {
      return Errors.VALUE;
    }
    Object sum = 0.0;
    for (Object index : s.indexes()) {
      sum = Js.add(sum, s.at(index));
    }
    return s.indexes().isEmpty() ? Errors.DIV0 : (Object) (Js.toNumber(sum) / s.indexes().size());
  }

  /** Counts the values that contain numbers in a database. */
  public static Object DCOUNT(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.COUNT(s.values());
  }

  /** Counts nonblank values in a database. */
  public static Object DCOUNTA(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.COUNTA(s.values());
  }

  /** Extracts from a database a single record that matches the specified criteria. */
  public static Object DGET(Object... args) {
    Selection s = select(args);
    if (s == null || s.indexes().isEmpty()) {
      return Errors.VALUE;
    }
    if (s.indexes().size() > 1) {
      return Errors.NUM;
    }
    return s.at(s.indexes().get(0));
  }

  /** Returns the maximum value from selected database entries. */
  public static Object DMAX(Object... args) {
    return extreme(args, true);
  }

  /** Returns the minimum value from selected database entries. */
  public static Object DMIN(Object... args) {
    return extreme(args, false);
  }

  private static Object extreme(Object[] args, boolean max) {
    Selection s = select(args);
    if (s == null) {
      return Errors.VALUE;
    }
    Object best = s.at(s.indexes().isEmpty() ? Js.UNDEFINED : s.indexes().get(0));
    for (Object index : s.indexes()) {
      if (max ? Js.lt(best, s.at(index)) : Js.gt(best, s.at(index))) {
        best = s.at(index);
      }
    }
    return best;
  }

  /** Multiplies the values in a particular field of records that match the criteria in a database. */
  public static Object DPRODUCT(Object... args) {
    Selection s = select(args);
    if (s == null) {
      return Errors.VALUE;
    }
    double result = 1;
    for (Object value : compact(s.values())) {
      result *= Js.toNumber(value);
    }
    return result;
  }

  /** Estimates the standard deviation based on a sample of selected database entries. */
  public static Object DSTDEV(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.STDEV_S(compact(s.values()));
  }

  /** Calculates the standard deviation based on the entire population of selected database entries. */
  public static Object DSTDEVP(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.STDEV_P(compact(s.values()));
  }

  /** Adds the numbers in the field column of records in the database that match the criteria. */
  public static Object DSUM(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : MathTrig.SUM(s.values());
  }

  /** Estimates variance based on a sample from selected database entries. */
  public static Object DVAR(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.VAR_S(s.values());
  }

  /** Calculates variance based on the entire population of selected database entries. */
  public static Object DVARP(Object... args) {
    Selection s = select(args);
    return s == null ? Errors.VALUE : Statistical.VAR_P(s.values());
  }

  static void register(Map<String, Fn> ops) {
    ops.put("DAVERAGE", Database::DAVERAGE);
    ops.put("DCOUNT", Database::DCOUNT);
    ops.put("DCOUNTA", Database::DCOUNTA);
    ops.put("DGET", Database::DGET);
    ops.put("DMAX", Database::DMAX);
    ops.put("DMIN", Database::DMIN);
    ops.put("DPRODUCT", Database::DPRODUCT);
    ops.put("DSTDEV", Database::DSTDEV);
    ops.put("DSTDEVP", Database::DSTDEVP);
    ops.put("DSUM", Database::DSUM);
    ops.put("DVAR", Database::DVAR);
    ops.put("DVARP", Database::DVARP);
  }
}
