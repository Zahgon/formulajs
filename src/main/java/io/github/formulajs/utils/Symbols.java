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

import io.github.formulajs.Fn;
import io.github.formulajs.MathTrig;
import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsError;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * utils/symbol.js: Formula.js-only functions for the operators +, -, /, *, ^, =, &lt;&gt;, &gt;, &gt;=, &lt; and &lt;=,
 * with Excel-like coercion and errors.
 */
public final class Symbols {
  private Symbols() {}

  private interface Arithmetic {
    double apply(double a, double b);
  }

  private static Object arithmetic(Object[] args, Arithmetic op) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object num1 = Common.parseNumber(args[0]);
    Object num2 = Common.parseNumber(args[1]);
    Object anyError = Common.anyError(num1, num2);
    if (anyError != Js.UNDEFINED) {
      return anyError;
    }
    return op.apply((Double) num1, (Double) num2);
  }

  public static Object ADD(Object... args) {
    return arithmetic(args, (a, b) -> a + b);
  }

  public static Object DIVIDE(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object dividend = Common.parseNumber(args[0]);
    Object divisor = Common.parseNumber(args[1]);
    Object anyError = Common.anyError(dividend, divisor);
    if (anyError != Js.UNDEFINED) {
      return anyError;
    }
    if ((Double) divisor == 0) {
      return Errors.DIV0;
    }
    return (Double) dividend / (Double) divisor;
  }

  private static Object equality(Object[] args, boolean equal) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object value1 = args[0];
    Object value2 = args[1];
    if (value1 instanceof JsError) {
      return value1;
    }
    if (value2 instanceof JsError) {
      return value2;
    }
    if (value1 == null) {
      value1 = Js.UNDEFINED;
    }
    if (value2 == null) {
      value2 = Js.UNDEFINED;
    }
    return Js.strictEquals(value1, value2) == equal;
  }

  public static Object EQ(Object... args) {
    return equality(args, true);
  }

  public static Object NE(Object... args) {
    return equality(args, false);
  }

  private static Object comparison(Object[] args, boolean errorsFirst, BiPredicate<Object, Object> op) {
    if (args.length != 2) {
      return Errors.NA;
    }
    Object num1 = args[0];
    Object num2 = args[1];
    if (errorsFirst) {
      if (num1 instanceof JsError) {
        return num1;
      }
      if (num2 instanceof JsError) {
        return num2;
      }
    }
    if (Common.anyIsString(num1, num2)) {
      num1 = Common.parseString(num1);
      num2 = Common.parseString(num2);
    } else {
      num1 = Common.parseNumber(num1);
      num2 = Common.parseNumber(num2);
    }
    Object anyError = Common.anyError(num1, num2);
    if (anyError != Js.UNDEFINED) {
      return anyError;
    }
    return op.test(num1, num2);
  }

  public static Object GT(Object... args) {
    return comparison(args, true, Js::gt);
  }

  public static Object GTE(Object... args) {
    return comparison(args, false, Js::ge);
  }

  public static Object LT(Object... args) {
    return comparison(args, false, Js::lt);
  }

  public static Object LTE(Object... args) {
    return comparison(args, false, Js::le);
  }

  public static Object MINUS(Object... args) {
    return arithmetic(args, (a, b) -> a - b);
  }

  public static Object MULTIPLY(Object... args) {
    return arithmetic(args, (a, b) -> a * b);
  }

  public static Object POW(Object... args) {
    if (args.length != 2) {
      return Errors.NA;
    }
    return MathTrig.POWER(args[0], args[1]);
  }

  public static void register(Map<String, Fn> ops) {
    ops.put("utils.symbols.ADD", Symbols::ADD);
    ops.put("utils.symbols.DIVIDE", Symbols::DIVIDE);
    ops.put("utils.symbols.EQ", Symbols::EQ);
    ops.put("utils.symbols.GT", Symbols::GT);
    ops.put("utils.symbols.GTE", Symbols::GTE);
    ops.put("utils.symbols.LT", Symbols::LT);
    ops.put("utils.symbols.LTE", Symbols::LTE);
    ops.put("utils.symbols.MINUS", Symbols::MINUS);
    ops.put("utils.symbols.MULTIPLY", Symbols::MULTIPLY);
    ops.put("utils.symbols.NE", Symbols::NE);
    ops.put("utils.symbols.POW", Symbols::POW);
  }
}
