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
import io.github.formulajs.utils.Errors;
import java.util.List;
import java.util.Map;

/** Information functions. */
public final class Information {
  private Information() {}

  /** Returns a number corresponding to an error type. */
  public static Object ERROR_TYPE(Object... args) {
    Object errorVal = Js.arg(args, 0);
    Object[] codes = {Errors.NIL, Errors.DIV0, Errors.VALUE, Errors.REF, Errors.NAME, Errors.NUM, Errors.NA,
      Errors.DATA};
    for (int i = 0; i < codes.length; i++) {
      if (codes[i] == errorVal) {
        return (double) (i + 1);
      }
    }
    return Errors.NA;
  }

  /** Returns TRUE if the value is blank. */
  public static Object ISBLANK(Object... args) {
    return Js.arg(args, 0) == null;
  }

  /** Returns TRUE if the value is any error value except #N/A. */
  public static Object ISERR(Object... args) {
    Object value = Js.arg(args, 0);
    return value == Errors.VALUE || value == Errors.REF || value == Errors.DIV0 || value == Errors.NUM
        || value == Errors.NAME || value == Errors.NIL
        || (value instanceof Double d && (d.isNaN() || d.isInfinite()));
  }

  /** Returns TRUE if the value is any error value. */
  public static Object ISERROR(Object... args) {
    Object value = Js.arg(args, 0);
    return (Boolean) ISERR(value) || value == Errors.NA;
  }

  /** Returns TRUE if the number is even. */
  public static Object ISEVEN(Object... args) {
    return (Js.toInt32(Math.floor(Math.abs(Js.toNumber(Js.arg(args, 0))))) & 1) == 0;
  }

  /** Returns TRUE if the value is a logical value. */
  public static Object ISLOGICAL(Object... args) {
    return Js.arg(args, 0) instanceof Boolean;
  }

  /** Returns TRUE if the value is the #N/A error value. */
  public static Object ISNA(Object... args) {
    return Js.arg(args, 0) == Errors.NA;
  }

  /** Returns TRUE if the value is not text. */
  public static Object ISNONTEXT(Object... args) {
    return !(Js.arg(args, 0) instanceof String);
  }

  /** Returns TRUE if the value is a number. */
  public static Object ISNUMBER(Object... args) {
    return Js.arg(args, 0) instanceof Double d && !d.isNaN() && !d.isInfinite();
  }

  /** Returns TRUE if the number is odd. */
  public static Object ISODD(Object... args) {
    return (Js.toInt32(Math.floor(Math.abs(Js.toNumber(Js.arg(args, 0))))) & 1) != 0;
  }

  /** Returns TRUE if the value is text. */
  public static Object ISTEXT(Object... args) {
    return Js.arg(args, 0) instanceof String;
  }

  /** Returns a value converted to a number. */
  public static Object N(Object... args) {
    Object value = Js.arg(args, 0);
    if ((Boolean) ISNUMBER(value)) {
      return value;
    }
    if (value instanceof JsDate d) {
      return d.getTime();
    }
    if (Boolean.TRUE.equals(value)) {
      return 1.0;
    }
    if (Boolean.FALSE.equals(value)) {
      return 0.0;
    }
    if ((Boolean) ISERROR(value)) {
      return value;
    }
    return 0.0;
  }

  /** Returns the error value #N/A. */
  public static Object NA(Object... args) {
    return Errors.NA;
  }

  /** Returns a number indicating the data type of a value. */
  public static Object TYPE(Object... args) {
    Object value = Js.arg(args, 0);
    if ((Boolean) ISNUMBER(value)) {
      return 1.0;
    }
    if ((Boolean) ISTEXT(value)) {
      return 2.0;
    }
    if ((Boolean) ISLOGICAL(value)) {
      return 4.0;
    }
    if ((Boolean) ISERROR(value)) {
      return 16.0;
    }
    if (value instanceof List) {
      return 64.0;
    }
    return Js.UNDEFINED;
  }

  static void register(Map<String, Fn> ops) {
    ops.put("ERROR.TYPE", Information::ERROR_TYPE);
    ops.put("ISBLANK", Information::ISBLANK);
    ops.put("ISERR", Information::ISERR);
    ops.put("ISERROR", Information::ISERROR);
    ops.put("ISEVEN", Information::ISEVEN);
    ops.put("ISLOGICAL", Information::ISLOGICAL);
    ops.put("ISNA", Information::ISNA);
    ops.put("ISNONTEXT", Information::ISNONTEXT);
    ops.put("ISNUMBER", Information::ISNUMBER);
    ops.put("ISODD", Information::ISODD);
    ops.put("ISTEXT", Information::ISTEXT);
    ops.put("N", Information::N);
    ops.put("NA", Information::NA);
    ops.put("TYPE", Information::TYPE);
  }
}
