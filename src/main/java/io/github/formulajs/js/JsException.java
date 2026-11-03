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

/** A thrown JavaScript error (TypeError, RangeError, SyntaxError, ...) with its name and message. */
public final class JsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String name;

  public JsException(String name, String message) {
    super(message, null, false, false);
    this.name = name;
  }

  public String name() {
    return name;
  }

  public static JsException typeError(String message) {
    return new JsException("TypeError", message);
  }

  public static JsException rangeError(String message) {
    return new JsException("RangeError", message);
  }

  /** V8's message for reading a member of null or undefined. */
  public static JsException cannotRead(Object target, Object key) {
    return typeError("Cannot read properties of " + Js.str(target) + " (reading '" + Js.str(key) + "')");
  }

  /** V8's message for assigning a member of undefined or null. */
  public static JsException cannotSet(Object target, Object key) {
    return typeError("Cannot set properties of " + Js.str(target) + " (setting '" + Js.str(key) + "')");
  }

  /** V8's message for spreading a value that is not iterable; {@code expression} is its source text. */
  public static JsException notIterable(String expression) {
    return typeError(expression + " is not iterable");
  }

  /** V8's message for calling something that is not a function; {@code callee} is the call's source text. */
  public static JsException notAFunction(String callee) {
    return typeError(callee + " is not a function");
  }
}
