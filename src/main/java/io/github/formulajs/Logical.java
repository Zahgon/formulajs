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
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.util.List;
import java.util.Map;

/** Logical functions. */
public final class Logical {
  private Logical() {}

  /** Returns TRUE if all of its arguments are TRUE. */
  public static Object AND(Object... args) {
    return fold(args, true);
  }

  /** Returns TRUE if any argument is TRUE. */
  public static Object OR(Object... args) {
    return fold(args, false);
  }

  /** AND and OR: errors win, blanks and strings are skipped, #VALUE! when nothing is left. */
  private static Object fold(Object[] arguments, boolean and) {
    List<Object> args = Common.flatten(Common.arguments(arguments));
    Object result = Errors.VALUE;
    for (Object arg : args) {
      if (arg instanceof JsError) {
        return arg;
      }
      if (Js.isNullish(arg) || arg instanceof String) {
        continue;
      }
      if (result == Errors.VALUE) {
        result = and;
      }
      if (Js.truthy(arg) != and) {
        result = !and;
      }
    }
    return result;
  }

  /** Returns the logical value FALSE. */
  public static Object FALSE(Object... args) {
    return false;
  }

  /** Specifies a logical test to perform. */
  public static Object IF(Object... args) {
    Object logicalTest = Js.arg(args, 0);
    if (logicalTest instanceof JsError) {
      return logicalTest;
    }
    Object valueIfTrue = args.length >= 2 ? args[1] : Boolean.TRUE;
    if (Js.isNullish(valueIfTrue)) {
      valueIfTrue = 0.0;
    }
    Object valueIfFalse = args.length == 3 ? args[2] : Boolean.FALSE;
    if (Js.isNullish(valueIfFalse)) {
      valueIfFalse = 0.0;
    }
    return Js.truthy(logicalTest) ? valueIfTrue : valueIfFalse;
  }

  /** Checks whether one or more conditions are met and returns a value that corresponds to the first TRUE one. */
  public static Object IFS(Object... args) {
    for (int i = 0; i < args.length / 2.0; i++) {
      if (Js.truthy(args[i * 2])) {
        return Js.arg(args, i * 2 + 1);
      }
    }
    return Errors.NA;
  }

  /** Returns a value you specify if a formula evaluates to an error; otherwise, returns the result. */
  public static Object IFERROR(Object... args) {
    Object value = Js.arg(args, 0);
    return (Boolean) Information.ISERROR(value) ? Js.arg(args, 1) : value;
  }

  /** Returns the value you specify if the expression resolves to #N/A, otherwise the result of the expression. */
  public static Object IFNA(Object... args) {
    Object value = Js.arg(args, 0);
    return value == Errors.NA ? Js.arg(args, 1) : value;
  }

  /** Reverses the logic of its argument. */
  public static Object NOT(Object... args) {
    Object logical = Js.arg(args, 0);
    if (logical instanceof String) {
      return Errors.VALUE;
    }
    if (logical instanceof JsError) {
      return logical;
    }
    return !Js.truthy(logical);
  }

  /** Returns the logical value TRUE. */
  public static Object TRUE(Object... args) {
    return true;
  }

  /** Returns a logical exclusive OR of all arguments. */
  public static Object XOR(Object... arguments) {
    List<Object> args = Common.flatten(Common.arguments(arguments));
    Object result = Errors.VALUE;
    for (Object arg : args) {
      if (arg instanceof JsError) {
        return arg;
      }
      if (Js.isNullish(arg) || arg instanceof String) {
        continue;
      }
      if (result == Errors.VALUE) {
        result = 0.0;
      }
      if (Js.truthy(arg)) {
        result = (Double) result + 1;
      }
    }
    if (result == Errors.VALUE) {
      return result;
    }
    return (Js.toInt32(Math.floor(Math.abs((Double) result))) & 1) != 0;
  }

  /** Evaluates an expression against a list of values and returns the result of the first match. */
  public static Object SWITCH(Object... args) {
    Object result = Js.UNDEFINED;
    if (args.length > 0) {
      Object targetValue = args[0];
      int argc = args.length - 1;
      int switchCount = argc / 2;
      boolean switchSatisfied = false;
      boolean hasDefaultClause = argc % 2 != 0;
      Object defaultClause = argc % 2 == 0 ? null : args[args.length - 1];
      for (int index = 0; index < switchCount; index++) {
        if (Js.strictEquals(targetValue, args[index * 2 + 1])) {
          result = args[index * 2 + 2];
          switchSatisfied = true;
          break;
        }
      }
      if (!switchSatisfied) {
        result = hasDefaultClause ? defaultClause : Errors.NA;
      }
    } else {
      result = Errors.VALUE;
    }
    return result;
  }

  static void register(Map<String, Fn> ops) {
    ops.put("AND", Logical::AND);
    ops.put("FALSE", Logical::FALSE);
    ops.put("IF", Logical::IF);
    ops.put("IFERROR", Logical::IFERROR);
    ops.put("IFNA", Logical::IFNA);
    ops.put("IFS", Logical::IFS);
    ops.put("NOT", Logical::NOT);
    ops.put("OR", Logical::OR);
    ops.put("SWITCH", Logical::SWITCH);
    ops.put("TRUE", Logical::TRUE);
    ops.put("XOR", Logical::XOR);
  }
}
