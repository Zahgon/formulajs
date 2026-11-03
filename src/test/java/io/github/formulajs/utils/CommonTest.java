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

import io.github.formulajs.Check;
import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** test/utils/common.js. */
public class CommonTest {
  private static final Object U = Js.UNDEFINED;

  private static List<Object> l(Object... items) {
    return Js.list(items);
  }

  private static double time(Object date) {
    return ((JsDate) date).getTime();
  }

  @Test
  public void testArrays() {
    Check.equal(l(1.0, 2.0, 3.0), Common.argsToArray(Arrays.asList(1.0, 2.0, 3.0)), "argsToArray");
    Check.equal(l(1.4), Common.arrayValuesToNumbers(l("1.4")), "arrayValuesToNumbers");
    Check.equal(l(0.0), Common.arrayValuesToNumbers(l("not convertible")), "arrayValuesToNumbers text");
    Check.equal(2, ((List<?>) Common.initial(l(1.0, 2.0, 3.0), 1.0)).size(), "initial array");
    Check.equal("a", Common.initial("abc", 2.0), "initial string");
    Check.equal(true, Common.initial(true, 1.0), "initial boolean");
    Check.equal(false, Common.isFlat(l(l(1.0, 2.0, 3.0), l(4.0, 5.0, 6.0))), "isFlat nested");
    Check.equal(true, Common.isFlat(l(1.0, 2.0, 3.0)), "isFlat");
    Check.equal(false, Common.isFlat(U), "isFlat undefined");
    Check.equal(1, ((List<?>) Common.rest(l(1.0, 2.0, 3.0), 2.0)).size(), "rest array");
    Check.equal("c", Common.rest("abc", 2.0), "rest string");
    Check.equal(true, Common.rest(true, 2.0), "rest boolean");
  }

  @Test
  public void testTranspose() {
    Check.equal(Errors.VALUE, Common.transpose(U), "undefined");
    Check.equal(l(l(1.0, 2.0)), Common.transpose(l(l(1.0), l(2.0))), "column");
    Check.equal(l(l(1.0, 5.0, 9.0), l(2.0, 6.0, 10.0), l(3.0, 7.0, 11.0), l(4.0, 8.0, 12.0)),
        Common.transpose(l(l(1.0, 2.0, 3.0, 4.0), l(5.0, 6.0, 7.0, 8.0), l(9.0, 10.0, 11.0, 12.0))), "3x4");
  }

  @Test
  public void testMatrices() {
    Check.isTrue(Arrays.equals(new double[] {3, 5}, Common.getMatrixSize(l(l(1, 2, 3, 4, 5), l(6, 7, 8, 9, 10),
        l(11, 12, 13, 14, 15)))), "regular size");
    Check.isTrue(Arrays.equals(new double[] {4, 5}, Common.getMatrixSize(l(l(1, 2, 3), l(4, 5, 6, 7, 8), l(9, 10),
        l(11, 12, 13, 14)))), "irregular size");
    Check.equal(l(l(U, U, U), l(U, U, U)), Common.createMatrix(2.0, 3.0), "createMatrix");
    Check.equal(Errors.NA, Common.createMatrix(U, U), "createMatrix undefined");
    Check.equal(Errors.NA, Common.createMatrix(5.0, U), "createMatrix undefined columns");
    Check.equal(Errors.VALUE, Common.createMatrix(-5.0, -2.0), "createMatrix negative");
    Check.equal(Errors.VALUE, Common.createMatrix(3.0, 0.0), "createMatrix zero");
    Check.equal(l(l(0.0, 0.0, 0.0), l(0.0, 0.0, 0.0)), Common.fillMatrix(l(l("", "", ""), l("", "", "")), U),
        "fillMatrix blanks");
    Check.equal(l(l(1.0, 2.0, 0.0), l(4.0, 0.0, 6.0), l(8.0, 9.0, 0.0)),
        Common.fillMatrix(l(l(1.0, 2.0), l(4.0, "", 6.0), l(8.0, 9.0)), U), "fillMatrix ragged");
    Check.equal(l(l(1.0, 2.0, 0.0), l(4.0, 0.0, 6.0), l(0.0, 8.0, 9.0)),
        Common.fillMatrix(l(l(1.0, 2.0, null), l(4.0, U, 6.0), l("", 8.0, 9.0)), U), "fillMatrix null");
  }

  @Test
  public void testFlatten() {
    Check.equal(l(1.0, 2.0, 3.0, 4.0), Common.flatten(l(l(1.0, 2.0), l(3.0, 4.0))), "nested");
    Check.equal(l(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), Common.flatten(l(1.0, 2.0, l(3.0, 4.0), l(l(5.0, 6.0)))), "deep");
    Check.equal(l(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), Common.flatten(l(l(1.0)), 2.0, l(3.0, 4.0), l(l(5.0, 6.0))),
        "several arguments");
    Check.equal(l("test"), Common.flatten(l("test")), "one string");
    Check.equal(l(12.0), Common.flatten(l(l(12.0))), "nested number");
    Check.equal(l((Object) null), Common.flatten((Object) null), "null");
    Check.equal(l("test"), Common.flatten("test"), "string");
    Check.equal(l(), Common.flatten(), "nothing");
    Check.equal(l(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
        Common.flatten(Common.arguments(new Object[] {l(l(1.0)), 2.0, l(3.0, 4.0), l(l(5.0, 6.0))})), "arguments");
    Check.equal(l(), Common.flatten(Common.arguments(new Object[0])), "no arguments");
    Check.equal(l(1.0, 2.0, 3.0, 4.0), Common.flattenShallow(l(l(1.0, 2.0), l(3.0, 4.0))), "flattenShallow");
    Check.equal(l("not array"), Common.flattenShallow("not array"), "flattenShallow value");
  }

  @Test
  public void testDatabasesAndErrors() {
    List<Object> db = l(l("Tree", "Apple", "Pear"), l("Height", 18.0, 12.0), l("Age", 20.0, 12.0),
        l("Yield", 14.0, 10.0));
    Check.equal(3.0, Common.findField(db, "Yield"), "findField");
    Check.equal(Errors.VALUE, Common.findField(db, "invalid"), "findField missing");
    Check.equal(U, Common.anyError(), "anyError none");
    Check.equal(U, Common.anyError(U, null, 1.0, "", "text"), "anyError values");
    Check.equal(Errors.NA, Common.anyError(1.0, Errors.NA), "anyError one");
    Check.equal(Errors.VALUE, Common.anyError(Errors.VALUE, Errors.NA), "anyError first");
    Check.equal(false, Common.anyIsError(U, null, 1.0, "", "text"), "anyIsError values");
    Check.equal(true, Common.anyIsError(1.0, Errors.NA), "anyIsError");
    Check.equal(3.1, Common.cleanFloat(3.0999999999999996), "cleanFloat");
  }

  @Test
  public void testParsers() {
    Check.equal(true, Common.parseBool(true), "parseBool true");
    Check.equal(false, Common.parseBool(0.0), "parseBool 0");
    Check.equal(true, Common.parseBool("TRUE"), "parseBool TRUE");
    Check.equal(false, Common.parseBool("FALSE"), "parseBool FALSE");
    Check.equal(true, Common.parseBool(JsDate.fromTime(0)), "parseBool date");
    Check.equal(true, Common.parseBool(Double.NaN), "parseBool NaN");
    Check.equal(Errors.NA, Common.parseBool(Errors.NA), "parseBool error");
    for (String bad : new String[] {"a", "2009 -07-01", "31/12/2009", "2009-31-12"}) {
      Check.equal(Errors.VALUE, Common.parseDate(bad), "parseDate " + bad);
    }
    Check.equal(Errors.NUM, Common.parseDate(-1.0), "parseDate -1");
    Check.equal(Errors.NUM, Common.parseDate(2958466.0), "parseDate too large");
    Check.equal(JsDate.parse("1/1/1900"), time(Common.parseDate(1.0)), "serial 1");
    Check.equal(JsDate.parse("3/1/1900"), time(Common.parseDate(61.0)), "serial 61");
    Check.equal(JsDate.parse("2/29/1900 01:12:00"), time(Common.parseDate(60.05)), "serial 60.05");
    Check.equal(JsDate.parse("7/5/2011 04:20:00"), time(Common.parseDate(40729.1805555556)), "serial with time");
    Check.equal(JsDate.of(2009, 6, 1).getTime(), time(Common.parseDate("2009/07-01")), "parseDate string");
    Check.equal(JsDate.of(2009, 11, 31, 12, 13).getTime(), time(Common.parseDate("2009-12-31  12:13")), "with time");
    Check.equal(JsDate.of(2011, 10, 11, 12, 12, 12).getTime(), time(Common.parseDate("11/11/11 12:12:12")),
        "two-digit year");
    Check.equal(Errors.VALUE, Common.parseDateArray(l("01/jan/2009", "invalid")), "parseDateArray");
    Check.equal(Errors.VALUE, Common.parseMatrix(U), "parseMatrix undefined");
    Check.equal(l(l(1.0, 2.0, 3.0), l(4.0, 5.0, 6.0)), Common.parseMatrix(l(l(1.0, 2.0, 3.0), l("4", 5.0, 6.0))),
        "parseMatrix");
    Check.equal(Errors.VALUE, Common.parseMatrix(l(l(1.0, 2.0, 3.0), l("foo", 5.0, 6.0))), "parseMatrix text");
    Check.equal(0.0, Common.parseNumber(U), "parseNumber undefined");
    Check.equal(Errors.VALUE, Common.parseNumber(""), "parseNumber empty");
    Check.equal(Errors.NA, Common.parseNumber(Errors.NA), "parseNumber error");
    Check.equal(Errors.VALUE, Common.parseNumberArray(U), "parseNumberArray undefined");
    Check.equal(Errors.VALUE, Common.parseNumberArray(l(2.0, 0.0, "", null, U)), "parseNumberArray blank");
    Check.equal(Errors.NA, Common.parseNumberArray(l(2.0, "a", 1.0, Errors.NA)), "parseNumberArray error");
    Check.equal("", Common.parseString(null), "parseString null");
    Check.equal("2", Common.parseString(2.0), "parseString number");
    Check.equal(Errors.NA, Common.parseString(Errors.NA), "parseString error");
    Check.equal(false, Common.anyIsString(U, null, 1.0, 2.5), "anyIsString");
    Check.equal(true, Common.anyIsString(1.0, ""), "anyIsString empty");
    Check.equal(l(1.0, 2.0), Common.numbers(l(1.0, Errors.DIV0, 2.0)), "numbers");
  }
}
