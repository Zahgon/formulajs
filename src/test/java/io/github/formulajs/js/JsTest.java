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

import io.github.formulajs.Check;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The JavaScript semantics the library relies on: number printing and parsing, coercion, dates, JSON, sorting. */
public class JsTest {
  @Test
  public void testNumberToString() {
    Check.equal("1e+21", Js.numberToString(1e21), "1e21");
    Check.equal("100000000000000000000", Js.numberToString(1e20), "1e20");
    Check.equal("1e-7", Js.numberToString(1e-7), "1e-7");
    Check.equal("0.000001", Js.numberToString(1e-6), "1e-6");
    Check.equal("0.30000000000000004", Js.numberToString(0.1 + 0.2), "0.1 + 0.2");
    Check.equal("5e-324", Js.numberToString(Double.MIN_VALUE), "MIN_VALUE");
    Check.equal("1.7976931348623157e+308", Js.numberToString(Double.MAX_VALUE), "MAX_VALUE");
    Check.equal("-0.5", Js.numberToString(-0.5), "-0.5");
    Check.equal("0", Js.numberToString(-0.0), "-0");
    Check.equal("NaN", Js.numberToString(Double.NaN), "NaN");
    Check.equal("-Infinity", Js.numberToString(Double.NEGATIVE_INFINITY), "-Infinity");
    Check.equal("123456789012345680000", Js.numberToString(123456789012345678901.0), "21 digits");
  }

  @Test
  public void testToFixedAndRadix() {
    Check.equal("1.00", Js.toFixed(1.005, 2.0), "1.005");
    Check.equal("-0.00", Js.toFixed(-0.001, 2.0), "-0.001");
    Check.equal("1e+21", Js.toFixed(1e21, 2.0), "1e21");
    Check.equal("3", Js.toFixed(2.5, 0.0), "2.5");
    Check.equal("ff", Js.toRadixString(255, 16), "255 hex");
    Check.equal("-1010", Js.toRadixString(-10, 2), "-10 binary");
    Check.equal("0.1", Js.toRadixString(0.5, 2), "0.5 binary");
    try {
      Js.toFixed(1, 101.0);
      throw new AssertionError("toFixed(101) did not throw");
    } catch (JsException e) {
      Check.equal("RangeError", e.name(), "toFixed range");
    }
  }

  @Test
  public void testConversions() {
    Check.equal(16.0, Js.toNumber("0x10"), "hex string");
    Check.equal(0.0, Js.parseFloat("0x10"), "parseFloat hex");
    Check.isTrue(Double.isNaN(Js.toNumber("1,2")), "1,2");
    Check.equal(12.0, Js.toNumber(" 12 "), "unicode white space");
    Check.equal(5.0, Js.toNumber(Js.list(5.0)), "[5]");
    Check.equal(0.0, Js.toNumber(null), "null");
    Check.equal(255.0, Js.parseInt("ff", 16.0), "parseInt hex");
    Check.equal("1,,a", Js.str(Js.list(1.0, null, "a")), "array to string");
    Check.isTrue(Js.looseEquals("1", 1.0), "'1' == 1");
    Check.isTrue(!Js.looseEquals(null, 0.0), "null != 0");
    Check.isTrue(Js.lt("10", "9"), "string comparison");
    Check.equal("12", Js.add("1", 2.0), "string concatenation");
    Check.equal(-0.0, Js.round(-0.4), "round(-0.4)");
    Check.equal(-2.0, Js.round(-2.5), "round(-2.5)");
  }

  @Test
  public void testDates() {
    Check.equal("2001-01-02T00:00:00.000Z", JsDate.of("1/2").toISOString(), "1/2");
    Check.equal("2020-03-01T00:00:00.000Z", JsDate.of("2020/02/30").toISOString(), "2020/02/30");
    Check.equal("2020-01-02T22:00:00.000Z", JsDate.of("Jan 2 2020 10:00 PM").toISOString(), "named month");
    Check.isTrue(!JsDate.of("2020-1-5T00:00:00.000").isValid(), "T after a legacy date");
    Check.equal("2013-01-01T00:00:00.000Z", JsDate.of(2013, 0, 1).toISOString(), "constructor");
    Check.equal("1999-12-31T00:00:00.000Z", JsDate.of(99, 11, 31).toISOString(), "two-digit year");
    Check.equal("Tue Jan 01 2013 00:00:00 GMT+0000 (Coordinated Universal Time)", JsDate.of(2013, 0, 1).toString(),
        "toString");
    JsDate d = JsDate.of(2020, 0, 31);
    d.setMonth(1.0);
    Check.equal("2020-03-02T00:00:00.000Z", d.toISOString(), "setMonth overflow");
  }

  @Test
  public void testJson() {
    Check.equal(List.of(1.0, "a"), Json.parse("[1,\"a\"]"), "parse");
    Check.equal("\"\\ud800\\n\"", Json.stringify("\ud800\n"), "lone surrogate");
    String[][] errors = {
      {"{x", "Expected property name or '}' in JSON at position 1 (line 1 column 2)"},
      {"[1,]", "Unexpected token ']', \"[1,]\" is not valid JSON"},
      {"", "Unexpected end of JSON input"},
      {"01", "Unexpected number in JSON at position 1 (line 1 column 2)"},
    };
    for (String[] error : errors) {
      try {
        Json.parse(error[0]);
        throw new AssertionError(error[0] + " parsed");
      } catch (JsException e) {
        Check.equal(error[1], e.getMessage(), error[0]);
      }
    }
  }

  @Test
  public void testSortAndCollation() {
    List<Object> list = Js.list(3.0, Js.UNDEFINED, 1.0, "2", 10.0);
    Js.sort(list, (a, b) -> Js.toNumber(a) - Js.toNumber(b));
    Check.equal(List.of(1.0, "2", 3.0, 10.0, Js.UNDEFINED), list, "sort");
    Check.isTrue(Js.localeCompare("a", "B") < 0, "a < B");
    Check.isTrue(Js.localeCompare("a", "A") < 0, "a < A");
    Check.isTrue(Js.localeCompare("_", "-") < 0, "_ < -");
  }

  @Test
  public void testRegExp() {
    Check.isTrue(JsRegExp.test("^a.c$", "abc"), "dot");
    Check.isTrue(!JsRegExp.test("^a.c$", "a\nc"), "dot and line feed");
    Check.isTrue(JsRegExp.test("^a{2}$", "aa"), "interval");
    Check.isTrue(JsRegExp.test("^a{,2}$", "a{,2}"), "literal brace");
    Check.isTrue(JsRegExp.test("^\\1$", "\u0001"), "octal escape");
    try {
      JsRegExp.test("^a\\\\(b$", "x");
      throw new AssertionError("unterminated group compiled");
    } catch (JsException e) {
      Check.equal("Invalid regular expression: /^a\\\\(b$/: Unterminated group", e.getMessage(), "group");
    }
  }
}
