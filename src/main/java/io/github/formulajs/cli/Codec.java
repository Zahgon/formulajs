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
package io.github.formulajs.cli;

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsDate;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import io.github.formulajs.js.Json;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The command's VALUE notation: JSON for what JSON can write, one-member tagged objects for the rest. */
public final class Codec {
  /**
   * V8's stack depth, in calls of the reference front end's recursive {@code decode}, for an argument of a request
   * (measured on the reference: arrays nested 2,615 deep decode, one level more overflows the stack).
   */
  private static final int DECODE_FRAMES = 2616;

  /**
   * The deepest decode call at which the reference can still build a tag's RequestError: the error's construction
   * needs more stack, so a bad tag nested 2,493 arrays deep or more is V8's RangeError (measured on the reference).
   */
  private static final int TAG_ERROR_FRAMES = 2493;

  private Codec() {}

  /** A tag's RequestError, or V8's RangeError where building it overflows the reference's stack. */
  private static JsException tagError(String message, int frames) {
    return frames > TAG_ERROR_FRAMES
        ? JsException.rangeError("Maximum call stack size exceeded")
        : Main.requestError(message);
  }

  /** A request argument as a JavaScript value. */
  public static Object decode(Object value) {
    return decode(value, 1);
  }

  /** {@code frames} counts the decode calls on the stack, this one included. */
  private static Object decode(Object value, int frames) {
    if (frames > DECODE_FRAMES) {
      throw JsException.rangeError("Maximum call stack size exceeded");
    }
    if (value instanceof List<?> list) {
      List<Object> out = new ArrayList<>();
      for (Object item : list) {
        out.add(decode(item, frames + 1));
      }
      return out;
    }
    if (!(value instanceof Map<?, ?> map)) {
      return value;
    }
    if (map.size() != 1) {
      throw tagError("a tagged value must have one member", frames);
    }
    Map.Entry<?, ?> entry = map.entrySet().iterator().next();
    String tag = (String) entry.getKey();
    Object member = entry.getValue();
    switch (tag) {
      case "number" -> {
        if (member instanceof String s) {
          switch (s) {
            case "NaN" -> {
              return Double.NaN;
            }
            case "Infinity" -> {
              return Double.POSITIVE_INFINITY;
            }
            case "-Infinity" -> {
              return Double.NEGATIVE_INFINITY;
            }
            case "-0" -> {
              return -0.0;
            }
            default -> {}
          }
        }
        throw tagError("bad number tag: " + Json.stringify(member), frames);
      }
      case "error" -> {
        for (JsError error : Errors.ALL) {
          if (error.message().equals(member)) {
            return error;
          }
        }
        throw tagError("bad error tag: " + Json.stringify(member), frames);
      }
      case "date" -> {
        if (!(member instanceof String) && !(member instanceof Double)) {
          throw tagError("bad date tag: " + Json.stringify(member), frames);
        }
        return JsDate.of(member);
      }
      case "undefined" -> {
        if (!Boolean.TRUE.equals(member)) {
          throw tagError("bad undefined tag: " + Json.stringify(member), frames);
        }
        return Js.UNDEFINED;
      }
      default -> throw tagError("unknown tag: " + Json.stringify(tag), frames);
    }
  }

  /** A result as the JSON the command writes. */
  public static Object encode(Object value) {
    if (value == Js.UNDEFINED) {
      return tag("undefined", true);
    }
    if (value instanceof Double d) {
      if (d.isNaN()) {
        return tag("number", "NaN");
      }
      if (d.isInfinite()) {
        return tag("number", d > 0 ? "Infinity" : "-Infinity");
      }
      if (d == 0 && 1 / d < 0) {
        return tag("number", "-0");
      }
      return d;
    }
    if (value instanceof List<?> list) {
      List<Object> out = new ArrayList<>();
      for (Object item : list) {
        out.add(encode(item));
      }
      return out;
    }
    if (value instanceof JsDate date) {
      return tag("date", date.toJSON());
    }
    if (value instanceof JsError error) {
      return tag("error", error.message());
    }
    return value;
  }

  private static Map<String, Object> tag(String name, Object member) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(name, member);
    return out;
  }
}
