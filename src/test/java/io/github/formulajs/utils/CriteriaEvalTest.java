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
import io.github.formulajs.js.JsException;
import io.github.formulajs.utils.CriteriaEval.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

/** test/utils/criteria-eval.js. */
public class CriteriaEvalTest {
  private static Token op(String value) {
    return new Token(value, CriteriaEval.TOKEN_TYPE_OPERATOR);
  }

  private static Token lit(Object value) {
    return new Token(value, CriteriaEval.TOKEN_TYPE_LITERAL);
  }

  @Test
  public void testParse() {
    Check.equal(List.of(), CriteriaEval.parse(""), "''");
    Check.equal(List.of(op("="), lit("test")), CriteriaEval.parse("test"), "test");
    Check.equal(List.of(op("="), lit(10.0)), CriteriaEval.parse("10"), "10");
    Check.equal(List.of(op("="), lit(10.0)), CriteriaEval.parse("=10"), "=10");
    Check.equal(List.of(op("<"), lit(10.0)), CriteriaEval.parse("<10"), "<10");
    Check.equal(List.of(op("<="), lit(10.0)), CriteriaEval.parse("<=10"), "<=10");
    Check.equal(List.of(op("<>"), lit(10.0)), CriteriaEval.parse("<>10"), "<>10");
    Check.equal(List.of(op(">"), lit(10.0)), CriteriaEval.parse(">10"), ">10");
    Check.equal(List.of(op(">="), lit(10.0)), CriteriaEval.parse(">=10"), ">=10");
    Check.equal(List.of(op("="), lit("==10")), CriteriaEval.parse("==10"), "==10");
    Check.equal(List.of(op("="), lit("==10>")), CriteriaEval.parse("==10>"), "==10>");
    Check.equal(List.of(op(">"), lit("test")), CriteriaEval.parse(">test"), ">test");
    Check.equal(List.of(op(">="), lit("test")), CriteriaEval.parse(">=test"), ">=test");
    Check.equal(List.of(op("<="), lit("test")), CriteriaEval.parse("<=test"), "<=test");
    Check.equal(List.of(op("="), lit("==test")), CriteriaEval.parse("==test"), "==test");
    Check.equal(List.of(op("="), lit("`")), CriteriaEval.parse("`"), "`");
    Check.equal(List.of(op("="), lit("!@#$%^&*()_+")), CriteriaEval.parse("!@#$%^&*()_+"), "symbols");
    Check.equal(List.of(op(">"), lit("!@#$%^&*()_+")), CriteriaEval.parse(">!@#$%^&*()_+"), ">symbols");
    Check.equal(List.of(op(">="), lit("!@#$%^&*()_+")), CriteriaEval.parse(">=!@#$%^&*()_+"), ">=symbols");
  }

  @Test
  public void testCreateToken() {
    for (String type : new String[] {null, "operatorr", "literall"}) {
      try {
        CriteriaEval.createToken("test", type);
        throw new AssertionError("accepted token type " + type);
      } catch (JsException e) {
        Check.equal("Unsupported token type: " + type, e.getMessage(), "message");
      }
    }
    Check.equal(op("test"), CriteriaEval.createToken("test", "operator"), "operator");
    Check.equal(lit("test"), CriteriaEval.createToken("test", "literal"), "literal");
  }

  @Test
  public void testCompute() {
    Check.equal(false, CriteriaEval.compute(List.of(lit("1"), op(">"), lit("1"))), "1 > 1");
    Check.equal(true, CriteriaEval.compute(List.of(lit("1"), op(">="), lit("1"))), "1 >= 1");
    Check.equal(false, CriteriaEval.compute(List.of(lit("3"), op("="), lit("1"))), "3 = 1");
    Check.equal(true, CriteriaEval.compute(List.of(lit("test"), op("="), lit("test"))), "test = test");
    Check.equal(false, CriteriaEval.compute(List.of(lit("a"), op(">"), lit("b"))), "a > b");
    Check.equal(true, CriteriaEval.compute(List.of(lit("z"), op(">"), lit("b"))), "z > b");
    Check.equal(false, CriteriaEval.compute(List.of(lit("3"), op("="), lit("1"), op("="), lit("6"))), "3=1=6");
    Check.equal(false, CriteriaEval.compute(List.of(lit("3"), op(">"), lit("1"), op("<"), lit("6"))), "3>1<6");
  }
}
