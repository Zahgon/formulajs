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

import io.github.formulajs.Check;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The formulajs command: requests, responses, value notation, errors, usage text and exit status. */
public class MainTest {
  private static String[] run(String input, String... argv) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int status = Main.onCommandStack(() -> Main.run(argv, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
        new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8)));
    return new String[] {out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8),
      String.valueOf(status)};
  }

  @Test
  public void testResults() throws Exception {
    String[] r = run("{\"op\":\"SUM\",\"args\":[1,2,[3,\"4\"]]}\n\n{\"op\":\"DATE\",\"args\":[2020,2,30]}\n"
        + "{\"op\":\"DATE\",\"args\":[2020,2,30],\"serial\":true}\n{\"op\":\"utils.symbols.DIVIDE\",\"args\":[1,0]}\n"
        + "{\"op\":\"IF\",\"args\":[true,{\"number\":\"-0\"}]}\n{\"op\":\"COMPLEX\",\"args\":[3.1,-4e-7]}\n");
    Check.equal("{\"result\":10}\n{\"result\":{\"date\":\"2020-03-01T00:00:00.000Z\"}}\n{\"result\":43891}\n"
        + "{\"result\":{\"error\":\"#DIV/0!\"}}\n{\"result\":{\"number\":\"-0\"}}\n{\"result\":\"3.1-4e-7i\"}\n",
        r[0], "stdout");
    Check.equal("", r[1], "stderr");
    Check.equal("0", r[2], "status");
  }

  @Test
  public void testErrors() throws Exception {
    String[] r = run("{x\n[1]\n{\"op\":\"NOW\"}\n{\"op\":\"SUM\",\"args\":5}\n{\"op\":\"SUM\",\"args\":[{\"a\":1}]}\n"
        + "{\"op\":\"DATEDIF\",\"args\":[1,2]}\n{\"op\":\"PI\"}\n");
    Check.equal("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Expected property name or '}' in JSON at "
        + "position 1 (line 1 column 2)\"}}\n"
        + "{\"error\":{\"name\":\"RequestError\",\"message\":\"a request must be a JSON object\"}}\n"
        + "{\"error\":{\"name\":\"RequestError\",\"message\":\"unknown op: \\\"NOW\\\"\"}}\n"
        + "{\"error\":{\"name\":\"RequestError\",\"message\":\"args must be an array\"}}\n"
        + "{\"error\":{\"name\":\"RequestError\",\"message\":\"unknown tag: \\\"a\\\"\"}}\n"
        + "{\"error\":{\"name\":\"TypeError\",\"message\":\"Cannot read properties of undefined (reading "
        + "'toUpperCase')\"}}\n"
        + "{\"result\":3.141592653589793}\n", r[0], "stdout");
    Check.equal("1", r[2], "status");
  }

  @Test
  public void testDeepNesting() throws Exception {
    String deep = "[".repeat(2615) + "1" + "]".repeat(2615);
    String deeper = "[".repeat(2616) + "1" + "]".repeat(2616);
    String[] r = run("{\"op\":\"ISERROR\",\"args\":[" + deep + "]}\n{\"op\":\"ISERROR\",\"args\":[" + deeper
        + "]}\n" + "[".repeat(100000) + "]".repeat(100000) + "\n{\"op\":\"SUM\",\"args\":[1,2]}\n");
    Check.equal("{\"result\":false}\n"
        + "{\"error\":{\"name\":\"RangeError\",\"message\":\"Maximum call stack size exceeded\"}}\n"
        + "{\"error\":{\"name\":\"RequestError\",\"message\":\"a request must be a JSON object\"}}\n"
        + "{\"result\":3}\n", r[0], "stdout");
    Check.equal("1", r[2], "status");
  }

  @Test
  public void testUsage() throws Exception {
    String[] r = run("{\"op\":\"PI\"}\n", "--help");
    Check.equal("", r[0], "stdout");
    Check.equal(Main.USAGE, r[1], "stderr");
    Check.equal("2", r[2], "status");
  }
}
