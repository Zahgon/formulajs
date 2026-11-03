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

import io.github.formulajs.js.JsError;
import java.util.List;

/** The library's error values (utils/error.js): one shared instance each, compared by identity. */
public final class Errors {
  public static final JsError NIL = new JsError("Error", "#NULL!");
  public static final JsError DIV0 = new JsError("Error", "#DIV/0!");
  public static final JsError VALUE = new JsError("Error", "#VALUE!");
  public static final JsError REF = new JsError("Error", "#REF!");
  public static final JsError NAME = new JsError("Error", "#NAME?");
  public static final JsError NUM = new JsError("Error", "#NUM!");
  public static final JsError NA = new JsError("Error", "#N/A");
  public static final JsError ERROR = new JsError("Error", "#ERROR!");
  public static final JsError DATA = new JsError("Error", "#GETTING_DATA");
  public static final JsError CALC = new JsError("Error", "#CALC!");

  /** All of them, in the order utils/error.js exports them. */
  public static final List<JsError> ALL = List.of(NIL, DIV0, VALUE, REF, NAME, NUM, NA, ERROR, DATA, CALC);

  private Errors() {}
}
