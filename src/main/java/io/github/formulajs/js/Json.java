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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON.parse with V8's SyntaxError messages, and JSON.stringify for the values the command writes. Objects are
 * {@link LinkedHashMap}s of their members in order, arrays are {@link List}s, numbers are {@link Double}s.
 */
public final class Json {
  private static final int CONTEXT = 10;

  private final String src;
  private int pos;

  private Json(String src) {
    this.src = src;
  }

  public static Object parse(String text) {
    Json p = new Json(text);
    p.skipWhitespace();
    Object value = p.parseValue();
    p.skipWhitespace();
    if (p.pos < text.length()) {
      throw p.error("Unexpected non-whitespace character after JSON");
    }
    return value;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Errors

  /** A SyntaxError at the current position: the message, then where in the input it happened. */
  private JsException error(String message) {
    int line = 1;
    int lineStart = 0;
    for (int i = 0; i < pos; i++) {
      char c = src.charAt(i);
      if (c == '\r' && i < pos - 1 && src.charAt(i + 1) == '\n') {
        i++;
      }
      if (src.charAt(i) == '\r' || src.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    }
    return syntaxError(message + " at position " + pos + " (line " + line + " column " + (pos - lineStart + 1) + ")");
  }

  private static JsException syntaxError(String message) {
    return new JsException("SyntaxError", message);
  }

  /** The error for an unexpected character (or the end of the input) where a value or token was expected. */
  private JsException unexpected() {
    if (pos >= src.length()) {
      return syntaxError("Unexpected end of JSON input");
    }
    char c = src.charAt(pos);
    if (c == '"') {
      return error("Unexpected string in JSON");
    }
    if (c == '-' || (c >= '0' && c <= '9')) {
      return error("Unexpected number in JSON");
    }
    return unexpectedToken();
  }

  private JsException unexpectedToken() {
    char c = src.charAt(pos);
    switch (src) {
      case "[object Object]", "undefined", "Infinity", "NaN" -> {
        return syntaxError("\"" + src + "\" is not valid JSON");
      }
      default -> {}
    }
    int length = src.length();
    String token = "Unexpected token '" + c + "', ";
    if (length <= 2 * CONTEXT) {
      return syntaxError(token + "\"" + src + "\" is not valid JSON");
    }
    if (pos < CONTEXT) {
      return syntaxError(token + "\"" + src.substring(0, pos + CONTEXT) + "\"... is not valid JSON");
    }
    if (pos < length - CONTEXT) {
      return syntaxError(token + "...\"" + src.substring(pos - CONTEXT, pos + CONTEXT) + "\"... is not valid JSON");
    }
    return syntaxError(token + "...\"" + src.substring(pos - CONTEXT) + "\" is not valid JSON");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Parsing

  private void skipWhitespace() {
    while (pos < src.length()) {
      char c = src.charAt(pos);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      pos++;
    }
  }

  private int peek() {
    return pos < src.length() ? src.charAt(pos) : -1;
  }

  private Object parseValue() {
    int c = peek();
    switch (c) {
      case '"':
        return parseString();
      case '{':
        return parseObject();
      case '[':
        return parseArray();
      case 't':
        return parseLiteral("true", Boolean.TRUE);
      case 'f':
        return parseLiteral("false", Boolean.FALSE);
      case 'n':
        return parseLiteral("null", null);
      default:
        if (c == '-' || (c >= '0' && c <= '9')) {
          return parseNumber();
        }
        throw unexpected();
    }
  }

  private Object parseLiteral(String word, Object value) {
    for (int i = 0; i < word.length(); i++) {
      if (pos >= src.length()) {
        throw unexpected();
      }
      if (src.charAt(pos) != word.charAt(i)) {
        throw unexpectedToken();
      }
      pos++;
    }
    return value;
  }

  private Map<String, Object> parseObject() {
    Map<String, Object> out = new LinkedHashMap<>();
    pos++;
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return out;
    }
    if (peek() != '"') {
      throw error("Expected property name or '}' in JSON");
    }
    while (true) {
      String key = parseString();
      skipWhitespace();
      if (peek() != ':') {
        throw error("Expected ':' after property name in JSON");
      }
      pos++;
      skipWhitespace();
      Object value = parseValue();
      out.put(key, value);
      skipWhitespace();
      if (peek() == ',') {
        pos++;
        skipWhitespace();
        if (peek() != '"') {
          throw error("Expected double-quoted property name in JSON");
        }
        continue;
      }
      if (peek() == '}') {
        pos++;
        return out;
      }
      throw error("Expected ',' or '}' after property value in JSON");
    }
  }

  private List<Object> parseArray() {
    List<Object> out = new ArrayList<>();
    pos++;
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return out;
    }
    while (true) {
      out.add(parseValue());
      skipWhitespace();
      if (peek() == ',') {
        pos++;
        skipWhitespace();
        continue;
      }
      if (peek() == ']') {
        pos++;
        return out;
      }
      throw error("Expected ',' or ']' after array element in JSON");
    }
  }

  private Double parseNumber() {
    int start = pos;
    if (peek() == '-') {
      pos++;
      if (!isDigit(peek())) {
        throw error("No number after minus sign in JSON");
      }
    }
    if (peek() == '0') {
      pos++;
      if (isDigit(peek())) {
        throw error("Unexpected number in JSON");
      }
    } else {
      while (isDigit(peek())) {
        pos++;
      }
    }
    if (peek() == '.') {
      pos++;
      if (!isDigit(peek())) {
        throw error("Unterminated fractional number in JSON");
      }
      while (isDigit(peek())) {
        pos++;
      }
    }
    if (peek() == 'e' || peek() == 'E') {
      pos++;
      if (peek() == '+' || peek() == '-') {
        pos++;
      }
      if (!isDigit(peek())) {
        throw error("Exponent part is missing a number in JSON");
      }
      while (isDigit(peek())) {
        pos++;
      }
    }
    return Double.parseDouble(src.substring(start, pos));
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  private String parseString() {
    StringBuilder sb = new StringBuilder();
    pos++;
    while (true) {
      if (pos >= src.length()) {
        throw error("Unterminated string in JSON");
      }
      char c = src.charAt(pos);
      if (c == '"') {
        pos++;
        return sb.toString();
      }
      if (c < 0x20) {
        throw error("Bad control character in string literal in JSON");
      }
      if (c != '\\') {
        sb.append(c);
        pos++;
        continue;
      }
      pos++;
      if (pos >= src.length()) {
        throw syntaxError("Unexpected end of JSON input");
      }
      char e = src.charAt(pos);
      switch (e) {
        case '"', '\\', '/' -> sb.append(e);
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> {
          int value = 0;
          for (int i = 0; i < 4; i++) {
            pos++;
            int d = pos < src.length() ? Character.digit(src.charAt(pos), 16) : -1;
            if (d < 0 || src.charAt(pos) > 'f') {
              throw error("Bad Unicode escape in JSON");
            }
            value = value * 16 + d;
          }
          sb.append((char) value);
        }
        default -> throw error("Bad escaped character in JSON");
      }
      pos++;
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Stringify

  /** JSON.stringify of a value made of maps, lists, strings, finite doubles, booleans and null. */
  public static String stringify(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value);
    return sb.toString();
  }

  private static void write(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String s) {
      quote(sb, s);
    } else if (value instanceof Double d) {
      sb.append(Double.isFinite(d) ? Js.numberToString(d) : "null");
    } else if (value instanceof Boolean b) {
      sb.append(b);
    } else if (value instanceof List<?> list) {
      sb.append('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        write(sb, list.get(i));
      }
      sb.append(']');
    } else if (value instanceof Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        quote(sb, (String) entry.getKey());
        sb.append(':');
        write(sb, entry.getValue());
      }
      sb.append('}');
    } else {
      throw new IllegalArgumentException("not a JSON value: " + value);
    }
  }

  private static void quote(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          boolean lone = Character.isHighSurrogate(c)
              ? i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))
              : Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(s.charAt(i - 1)));
          if (c < 0x20 || lone) {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
