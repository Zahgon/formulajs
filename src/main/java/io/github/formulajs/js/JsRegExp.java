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

import java.util.regex.Pattern;

/**
 * {@code new RegExp(source).test(input)} without flags: the JavaScript pattern (with the Annex B syntax of V8, and its
 * SyntaxError messages) is translated to an equivalent java.util.regex pattern over UTF-16 code units. Lone and
 * paired surrogates are each mapped to their own supplementary code point so that every code unit is one character.
 */
public final class JsRegExp {
  private static final String WORD = "[a-zA-Z0-9_]";
  private static final String SPACE = "[\\x{9}-\\x{d}\\x{20}\\x{a0}\\x{1680}\\x{2000}-\\x{200a}\\x{2028}\\x{2029}"
      + "\\x{202f}\\x{205f}\\x{3000}\\x{feff}]";

  private final String src;
  private final StringBuilder out = new StringBuilder();
  private int pos;
  private int captureCount;
  private boolean hasNamedGroups;

  private JsRegExp(String src) {
    this.src = src;
  }

  public static boolean test(String source, String input) {
    JsRegExp parser = new JsRegExp(source);
    parser.scanCaptures();
    parser.disjunction(0);
    return Pattern.compile(parser.out.toString()).matcher(units(input)).find();
  }

  private static String units(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      sb.appendCodePoint(codePoint(s.charAt(i)));
    }
    return sb.toString();
  }

  private static int codePoint(char c) {
    return Character.isSurrogate(c) ? 0xF0000 + (c - 0xD800) : c;
  }

  private static String literal(int c) {
    return "\\x{" + Integer.toHexString(codePoint((char) c)) + "}";
  }

  private JsException error(String message) {
    return new JsException("SyntaxError", "Invalid regular expression: /" + src + "/: " + message);
  }

  private boolean more() {
    return pos < src.length();
  }

  private int peek(int offset) {
    return pos + offset < src.length() ? src.charAt(pos + offset) : -1;
  }

  private void scanCaptures() {
    for (int i = 0; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '[') {
        for (i++; i < src.length() && src.charAt(i) != ']'; i++) {
          if (src.charAt(i) == '\\') {
            i++;
          }
        }
      } else if (c == '(') {
        if (i + 1 < src.length() && src.charAt(i + 1) == '?') {
          if (i + 2 < src.length() && src.charAt(i + 2) == '<' && i + 3 < src.length()
              && src.charAt(i + 3) != '=' && src.charAt(i + 3) != '!') {
            captureCount++;
            hasNamedGroups = true;
          }
        } else {
          captureCount++;
        }
      }
    }
  }

  private void disjunction(int depth) {
    while (more()) {
      char c = src.charAt(pos);
      switch (c) {
        case '|' -> {
          out.append('|');
          pos++;
          continue;
        }
        case ')' -> {
          if (depth == 0) {
            throw error("Unmatched ')'");
          }
          return;
        }
        case '^' -> {
          out.append('^');
          pos++;
          continue;
        }
        case '$' -> {
          out.append("\\z");
          pos++;
          continue;
        }
        case '*', '+', '?' -> throw error("Nothing to repeat");
        case '{' -> {
          if (interval(false) != null) {
            throw error("Nothing to repeat");
          }
          out.append(literal('{'));
          pos++;
        }
        case '(' -> {
          if (!group(depth)) {
            continue;
          }
        }
        case '[' -> characterClass();
        case '.' -> {
          out.append("[^\\n\\r\\x{2028}\\x{2029}]");
          pos++;
        }
        case '\\' -> {
          if (!atomEscape()) {
            continue;
          }
        }
        default -> {
          out.append(literal(c));
          pos++;
        }
      }
      quantifier();
    }
    if (depth > 0) {
      throw error("Unterminated group");
    }
  }

  /** A group; answers whether it can be quantified. */
  private boolean group(int depth) {
    pos++;
    boolean quantifiable = true;
    if (peek(0) == '?') {
      int k = peek(1);
      if (k == ':' || k == '=' || k == '!') {
        out.append("(?").append((char) k);
        pos += 2;
      } else if (k == '<' && (peek(2) == '=' || peek(2) == '!')) {
        out.append("(?<").append((char) peek(2));
        pos += 3;
        quantifiable = false;
      } else if (k == '<') {
        int end = src.indexOf('>', pos + 2);
        if (end < 0 || end == pos + 2 || !src.substring(pos + 2, end).matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
          throw error("Invalid capture group name");
        }
        out.append('(');
        pos = end + 1;
      } else {
        throw error("Invalid group");
      }
    } else {
      out.append('(');
    }
    disjunction(depth + 1);
    out.append(')');
    pos++;
    if (!quantifiable && more() && "*+?".indexOf(src.charAt(pos)) >= 0) {
      throw error("Nothing to repeat");
    }
    return quantifiable;
  }

  /** {n}, {n,} or {n,m} at pos: [min, max] (max -1 for no limit), or null when it is not one. */
  private int[] interval(boolean consume) {
    int i = pos + 1;
    int start = i;
    long min = 0;
    while (i < src.length() && Character.isDigit(src.charAt(i)) && src.charAt(i) < 128) {
      min = Math.min(min * 10 + src.charAt(i++) - '0', Integer.MAX_VALUE);
    }
    if (i == start) {
      return null;
    }
    long max = min;
    if (i < src.length() && src.charAt(i) == ',') {
      i++;
      int maxStart = i;
      max = 0;
      while (i < src.length() && src.charAt(i) >= '0' && src.charAt(i) <= '9') {
        max = Math.min(max * 10 + src.charAt(i++) - '0', Integer.MAX_VALUE);
      }
      if (i == maxStart) {
        max = -1;
      }
    }
    if (i >= src.length() || src.charAt(i) != '}') {
      return null;
    }
    if (consume) {
      pos = i + 1;
    }
    return new int[] {(int) min, (int) max};
  }

  private void quantifier() {
    if (!more()) {
      return;
    }
    char c = src.charAt(pos);
    if (c == '*' || c == '+' || c == '?') {
      out.append(c);
      pos++;
    } else if (c == '{') {
      int[] range = interval(true);
      if (range == null) {
        return;
      }
      if (range[1] != -1 && range[0] > range[1]) {
        throw error("numbers out of order in {} quantifier");
      }
      out.append('{').append(range[0]).append(range[1] == range[0] ? "" : ",")
          .append(range[1] > 0 && range[1] != range[0] ? String.valueOf(range[1]) : "").append('}');
    } else {
      return;
    }
    if (more() && src.charAt(pos) == '?') {
      out.append('?');
      pos++;
    }
  }

  /** An escape outside a class; answers whether it can be quantified. */
  private boolean atomEscape() {
    if (pos + 1 >= src.length()) {
      throw error("\\ at end of pattern");
    }
    char c = src.charAt(pos + 1);
    switch (c) {
      case 'b', 'B' -> {
        String w = "(?<=" + WORD + ")(?!" + WORD + ")|(?<!" + WORD + ")(?=" + WORD + ")";
        String nw = "(?<=" + WORD + ")(?=" + WORD + ")|(?<!" + WORD + ")(?!" + WORD + ")";
        out.append("(?:").append(c == 'b' ? w : nw).append(')');
        pos += 2;
        if (more() && "*+?".indexOf(src.charAt(pos)) >= 0) {
          throw error("Nothing to repeat");
        }
        return false;
      }
      case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
        int i = pos + 1;
        long value = 0;
        while (i < src.length() && src.charAt(i) >= '0' && src.charAt(i) <= '9' && value <= 65535) {
          value = value * 10 + src.charAt(i++) - '0';
        }
        if (value <= captureCount) {
          out.append("(?:\\").append(value).append(')');
          pos = i;
          return true;
        }
        if (c >= '8') {
          out.append(literal(c));
          pos += 2;
          return true;
        }
        pos++;
        out.append(literal(octal()));
        return true;
      }
      default -> {
        pos++;
        String escape = characterEscape(false);
        out.append(escape);
        return true;
      }
    }
  }

  /** At an escaped character (pos just after the backslash): its translation; pos moves past it. */
  private String characterEscape(boolean inClass) {
    String cls = switch (src.charAt(pos)) {
      case 'd' -> "[0-9]";
      case 'D' -> "[^0-9]";
      case 'w' -> WORD;
      case 'W' -> "[^a-zA-Z0-9_]";
      case 's' -> SPACE;
      case 'S' -> "[^" + SPACE.substring(1);
      default -> null;
    };
    if (cls != null) {
      pos++;
      return cls;
    }
    return literal(escapedChar(inClass));
  }

  /** At an escaped character that stands for one character: the character; pos moves past it. */
  private int escapedChar(boolean inClass) {
    char c = src.charAt(pos);
    int control = "fnrtvb".indexOf(c);
    if (control >= 0) {
      pos++;
      return "\f\n\r\t\u000b\b".charAt(control);
    }
    if (c == 'c') {
      int letter = peek(1);
      if ((letter | 0x20) >= 'a' && (letter | 0x20) <= 'z'
          || inClass && (letter >= '0' && letter <= '9' || letter == '_')) {
        pos += 2;
        return letter & 0x1F;
      }
      return '\\';
    }
    if (c >= '0' && c <= '7') {
      return octal();
    }
    int n = c == 'x' ? 2 : c == 'u' ? 4 : 0;
    if (n > 0 && pos + n < src.length() && src.substring(pos + 1, pos + 1 + n).matches("[0-9a-fA-F]+")) {
      int value = Integer.parseInt(src.substring(pos + 1, pos + 1 + n), 16);
      pos += 1 + n;
      return value;
    }
    pos++;
    return c;
  }

  private int octal() {
    int value = src.charAt(pos++) - '0';
    if (more() && src.charAt(pos) >= '0' && src.charAt(pos) <= '7') {
      value = value * 8 + src.charAt(pos++) - '0';
      if (value < 32 && more() && src.charAt(pos) >= '0' && src.charAt(pos) <= '7') {
        value = value * 8 + src.charAt(pos++) - '0';
      }
    }
    return value;
  }

  private void characterClass() {
    pos++;
    boolean negated = false;
    if (peek(0) == '^') {
      negated = true;
      pos++;
    }
    StringBuilder items = new StringBuilder();
    while (true) {
      if (!more()) {
        throw error("Unterminated character class");
      }
      if (src.charAt(pos) == ']') {
        pos++;
        break;
      }
      Object first = classAtom();
      if (peek(0) == '-' && peek(1) != -1 && peek(1) != ']') {
        pos++;
        Object second = classAtom();
        if (first instanceof Integer a && second instanceof Integer b) {
          if (a > b) {
            throw error("Range out of order in character class");
          }
          items.append(literal(a)).append('-').append(literal(b));
          continue;
        }
        items.append(atomText(first)).append(literal('-')).append(atomText(second));
        continue;
      }
      items.append(atomText(first));
    }
    if (items.isEmpty()) {
      out.append(negated ? "[\\x{0}-\\x{10ffff}]" : "(?:(?!))");
    } else {
      out.append('[').append(negated ? "^" : "").append(items).append(']');
    }
  }

  private static String atomText(Object atom) {
    return atom instanceof Integer c ? literal(c) : (String) atom;
  }

  /** One class atom: an Integer for a character, a String for a class escape. */
  private Object classAtom() {
    char c = src.charAt(pos);
    if (c != '\\') {
      pos++;
      return (int) c;
    }
    if (pos + 1 >= src.length()) {
      throw error("\\ at end of pattern");
    }
    pos++;
    char e = src.charAt(pos);
    if ("dDwWsS".indexOf(e) >= 0) {
      return characterEscape(true);
    }
    if (e == '-') {
      pos++;
      return (int) '-';
    }
    if (e == '8' || e == '9') {
      pos++;
      return (int) e;
    }
    return escapedChar(true);
  }
}
