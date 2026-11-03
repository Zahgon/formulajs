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

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsException;
import java.util.ArrayList;
import java.util.List;

/** utils/criteria-eval.js: criteria such as {@code ">=10"} or {@code "<>a"} turned into tokens and evaluated. */
public final class CriteriaEval {
  public static final String TOKEN_TYPE_OPERATOR = "operator";
  public static final String TOKEN_TYPE_LITERAL = "literal";
  private static final String DEFAULT_OPERATOR = "=";
  private static final List<String> VALID_SYMBOLS = List.of(">", ">=", "<", "<=", "=", "<>");

  /** One token: an operator or a literal value. */
  public record Token(Object value, String type) {}

  private CriteriaEval() {}

  /** A token describing a value or a symbol; the type must be "operator" or "literal". */
  public static Token createToken(Object value, String type) {
    if (!TOKEN_TYPE_OPERATOR.equals(type) && !TOKEN_TYPE_LITERAL.equals(type)) {
      throw new JsException("Error", "Unsupported token type: " + type);
    }
    return new Token(value, type);
  }

  private static Object castValueToCorrectType(Object value) {
    if (!(value instanceof String s)) {
      return value;
    }
    if (isDecimal(s)) {
      return s.indexOf('.') == -1 ? Js.parseInt(s, 10.0) : Js.parseFloat(s);
    }
    return value;
  }

  /** {@code /^\d+(\.\d+)?$/}. */
  private static boolean isDecimal(String s) {
    int i = 0;
    while (i < s.length() && isDigit(s.charAt(i))) {
      i++;
    }
    if (i == 0) {
      return false;
    }
    if (i == s.length()) {
      return true;
    }
    if (s.charAt(i) != '.') {
      return false;
    }
    int j = i + 1;
    while (j < s.length() && isDigit(s.charAt(j))) {
      j++;
    }
    return j > i + 1 && j == s.length();
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static List<String> tokenizeExpression(String expression) {
    List<String> tokens = new ArrayList<>();
    StringBuilder value = new StringBuilder();
    StringBuilder symbol = new StringBuilder();
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '>' || c == '<' || c == '=') {
        symbol.append(c);
        if (!value.isEmpty()) {
          tokens.add(value.toString());
          value.setLength(0);
        }
      } else {
        if (!symbol.isEmpty()) {
          tokens.add(symbol.toString());
          symbol.setLength(0);
        }
        value.append(c);
      }
    }
    if (!value.isEmpty()) {
      tokens.add(value.toString());
    }
    if (!symbol.isEmpty()) {
      tokens.add(symbol.toString());
    }
    return tokens;
  }

  private static List<Token> analyzeTokens(List<String> tokens) {
    StringBuilder literal = new StringBuilder();
    List<Token> analyzed = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (i == 0 && VALID_SYMBOLS.contains(token)) {
        analyzed.add(createToken(token, TOKEN_TYPE_OPERATOR));
      } else {
        literal.append(token);
      }
    }
    if (!literal.isEmpty()) {
      analyzed.add(createToken(castValueToCorrectType(literal.toString()), TOKEN_TYPE_LITERAL));
    }
    if (!analyzed.isEmpty() && !analyzed.get(0).type().equals(TOKEN_TYPE_OPERATOR)) {
      analyzed.add(0, createToken(DEFAULT_OPERATOR, TOKEN_TYPE_OPERATOR));
    }
    return analyzed;
  }

  public static List<Token> parse(String expression) {
    return analyzeTokens(tokenizeExpression(expression));
  }

  public static boolean compute(List<Token> tokens) {
    List<Object> values = new ArrayList<>();
    Object operator = Js.UNDEFINED;
    for (Token token : tokens) {
      if (token.type().equals(TOKEN_TYPE_OPERATOR)) {
        operator = token.value();
      } else if (token.type().equals(TOKEN_TYPE_LITERAL)) {
        values.add(token.value());
      }
    }
    return evaluate(values, operator);
  }

  private static boolean evaluate(List<Object> values, Object operator) {
    Object a = Js.get(values, 0);
    Object b = Js.get(values, 1);
    if (!(operator instanceof String op)) {
      return false;
    }
    return switch (op) {
      case ">" -> Js.gt(a, b);
      case ">=" -> Js.ge(a, b);
      case "<" -> Js.lt(a, b);
      case "<=" -> Js.le(a, b);
      case "=" -> Js.looseEquals(a, b);
      case "<>" -> !Js.looseEquals(a, b);
      default -> false;
    };
  }
}
