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

import io.github.formulajs.cli.Codec;
import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.Json;
import io.github.formulajs.utils.DateUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The upstream test suite (test/*.js), ported as data: every assertion of the mocha files whose actual value is a
 * public function's result, one JSON line each in src/test/resources/upstream/FILE.jsonl, with the function, its
 * arguments (in the command's VALUE notation), the chai matcher and the expected value.
 */
public class UpstreamTest {
  @Test
  public void testDatabase() throws IOException {
    check("database");
  }

  @Test
  public void testDateTime() throws IOException {
    check("date-time");
  }

  @Test
  public void testEngineering() throws IOException {
    check("engineering");
  }

  @Test
  public void testFinancial() throws IOException {
    check("financial");
  }

  @Test
  public void testInformation() throws IOException {
    check("information");
  }

  @Test
  public void testLogical() throws IOException {
    check("logical");
  }

  @Test
  public void testLookupReference() throws IOException {
    check("lookup-reference");
  }

  @Test
  public void testMathTrig() throws IOException {
    check("math-trig");
  }

  @Test
  public void testStatistical() throws IOException {
    check("statistical");
  }

  @Test
  public void testText() throws IOException {
    check("text");
  }

  @Test
  public void testDateUtils() throws IOException {
    check("utils-date");
  }

  @Test
  public void testSymbols() throws IOException {
    check("utils-symbol");
  }

  private static void check(String file) throws IOException {
    List<String> failures = new ArrayList<>();
    int count = 0;
    try (InputStream in = UpstreamTest.class.getResourceAsStream("/upstream/" + file + ".jsonl")) {
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        if (line.isEmpty()) {
          continue;
        }
        count++;
        String failure = verify(line);
        if (failure != null) {
          failures.add(failure);
        }
      }
    }
    Check.isTrue(count > 0, file + ": no assertions");
    if (!failures.isEmpty()) {
      throw new AssertionError(failures.size() + " of " + count + " assertions failed in " + file + ":\n"
          + String.join("\n", failures.subList(0, Math.min(20, failures.size()))));
    }
  }

  private static String verify(String line) {
    Map<?, ?> spec = (Map<?, ?>) Json.parse(line);
    List<Object> args = new ArrayList<>();
    for (Object arg : (List<?>) spec.get("args")) {
      args.add(Codec.decode(arg));
    }
    Object result;
    if (Boolean.TRUE.equals(spec.get("serial"))) {
      DateUtils.useSerial();
    }
    try {
      result = Formulajs.operation((String) spec.get("op")).apply(args.toArray());
    } finally {
      DateUtils.useDate();
    }
    Object expected = spec.get("expected");
    String actual = Json.stringify(Codec.encode(result));
    boolean ok = switch ((String) spec.get("assert")) {
      case "equal", "eql" -> expected instanceof Double e && result instanceof Double r
          ? e.doubleValue() == r.doubleValue()
          : actual.equals(Json.stringify(expected));
      case "approximately" -> result instanceof Double r
          && Math.abs(r - (Double) expected) <= (Double) spec.get("delta");
      case "true" -> Boolean.TRUE.equals(result);
      case "false" -> Boolean.FALSE.equals(result);
      case "undefined" -> result == Js.UNDEFINED;
      case "notexist" -> Js.isNullish(result);
      case "contain" -> result instanceof String s ? s.contains((String) expected)
          : result instanceof List<?> l && Js.includes(l, Codec.decode(expected));
      case "error" -> result instanceof JsError;
      default -> false;
    };
    return ok ? null : line + "\n    got " + actual;
  }
}
