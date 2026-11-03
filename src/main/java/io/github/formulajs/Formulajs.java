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
import io.github.formulajs.utils.DateUtils;
import io.github.formulajs.utils.Symbols;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The library's public functions by the names they are exported under (index.js): every function of every module,
 * dotted paths such as {@code CHISQ.DIST.RT} for functions hanging off other exports, and the {@code utils.symbols}
 * and {@code utils.date} helpers. NOW, TODAY, RAND and RANDBETWEEN are not deterministic and are not included.
 */
public final class Formulajs {
  private static final Map<String, Fn> OPERATIONS;

  static {
    Map<String, Fn> ops = new LinkedHashMap<>();
    Compatibility.register(ops);
    Database.register(ops);
    DateTime.register(ops);
    Engineering.register(ops);
    Financial.register(ops);
    Information.register(ops);
    Logical.register(ops);
    LookupReference.register(ops);
    MathTrig.register(ops);
    Statistical.register(ops);
    Text.register(ops);
    Symbols.register(ops);
    ops.put("utils.date.dateToSerial", args -> DateUtils.dateToSerial(Js.arg(args, 0)));
    ops.put("utils.date.serialToDate", args -> DateUtils.serialToDate(Js.arg(args, 0)));
    OPERATIONS = Collections.unmodifiableMap(ops);
  }

  private Formulajs() {}

  /** The function exported under this name, or null. */
  public static Fn operation(String name) {
    return OPERATIONS.get(name);
  }

  public static Map<String, Fn> operations() {
    return OPERATIONS;
  }
}
