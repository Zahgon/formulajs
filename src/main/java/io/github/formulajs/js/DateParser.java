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

/**
 * Date.parse as V8 does it: the ECMAScript date-time string format first, then V8's legacy parser for anything else
 * (month names, AM/PM, time zone names and offsets, M/D/Y numbers, two-digit years and day overflow included).
 * Local time is UTC. The input ends at its end or at a NUL character, as in V8.
 */
final class DateParser {
  private static final int NONE = Integer.MAX_VALUE;
  private static final int MAX_SIGNIFICANT_DIGITS = 9;
  // Token kinds; keywords are KEYWORD with a keyword type.
  private static final int INVALID = 0;
  private static final int UNKNOWN = 1;
  private static final int SPACE = 2;
  private static final int NUMBER = 3;
  private static final int SYMBOL = 4;
  private static final int END = 5;
  private static final int KEYWORD = 6;
  // Keyword types.
  private static final int NOT_A_KEYWORD = 0;
  private static final int MONTH_NAME = 1;
  private static final int TIME_ZONE_NAME = 2;
  private static final int TIME_SEPARATOR = 3;
  private static final int AM_PM = 4;
  private static final String[] KEYWORDS = {
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec", "am", "pm", "ut", "utc", "z",
    "gmt", "cdt", "cst", "edt", "est", "mdt", "mst", "pdt", "pst", "t"
  };
  private static final int[][] KEYWORD_INFO = {
    {1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {1, 6}, {1, 7}, {1, 8}, {1, 9}, {1, 10}, {1, 11}, {1, 12}, {4, 0},
    {4, 12}, {2, 0}, {2, 0}, {2, 0}, {2, 0}, {2, -5}, {2, -6}, {2, -4}, {2, -5}, {2, -6}, {2, -7}, {2, -7}, {2, -8},
    {3, 0}
  };

  /** A token: kind, keyword type, length in characters and value (number, symbol character or keyword value). */
  private record Token(int kind, int keyword, int length, int value) {
    boolean is(int symbol) {
      return kind == SYMBOL && value == symbol;
    }

    boolean isSign() {
      return is('+') || is('-');
    }

    boolean isNumber(int digits) {
      return kind == NUMBER && length == digits;
    }

    boolean isZ() {
      return kind == KEYWORD && keyword == TIME_ZONE_NAME && length == 1 && value == 0;
    }
  }

  private final String s;
  private int index;
  private int ch;
  private Token next;
  private final int[] day = new int[3];
  private int dayCount;
  private int namedMonth = NONE;
  private boolean isoDate;
  private final int[] time = new int[4];
  private int timeCount;
  private int hourOffset = NONE;
  private int tzSign = NONE;
  private int tzHour = NONE;
  private int tzMinute = NONE;

  private DateParser(String s) {
    this.s = s;
    advance();
    next = scan();
  }

  /** The time value Date.parse gives for the string, NaN when it does not parse. */
  static double parse(String s) {
    double[] out = new DateParser(s).parse();
    if (out == null) {
      return Double.NaN;
    }
    double date = JsDate.makeDate(JsDate.makeDay(out[0], out[1], out[2]), JsDate.makeTime(out[3], out[4], out[5],
        out[6]));
    if (Double.isNaN(out[7])) {
      date = JsDate.localToUtc(date);
    } else {
      date -= out[7] * 1000.0;
      if (date < -8.64e15 || date > 8.64e15) {
        return Double.NaN;
      }
    }
    return JsDate.timeClip(date);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Characters and tokens

  private void advance() {
    ch = index < s.length() ? s.charAt(index) : 0;
    index++;
  }

  private static boolean isWhiteSpace(int c) {
    return Js.isSpace(c) && c != '\n' && c != '\r' && c != 0x2028 && c != 0x2029;
  }

  private boolean isDigit() {
    return ch >= '0' && ch <= '9';
  }

  private boolean isWordChar() {
    return ch >= 'A' && !isWhiteSpace(ch);
  }

  private Token scan() {
    int start = index - 1;
    if (ch == 0) {
      return new Token(END, 0, 0, -1);
    }
    if (isDigit()) {
      int n = 0;
      int digits = 0;
      while (ch == '0') {
        advance();
      }
      for (; isDigit(); advance(), digits++) {
        if (digits < MAX_SIGNIFICANT_DIGITS) {
          n = n * 10 + ch - '0';
        }
      }
      return new Token(NUMBER, 0, index - 1 - start, n);
    }
    if (":-+.)".indexOf(ch) >= 0) {
      int c = ch;
      advance();
      return new Token(SYMBOL, 0, 1, c);
    }
    if (isWordChar()) {
      StringBuilder prefix = new StringBuilder();
      int length = 0;
      for (; isWordChar(); advance(), length++) {
        if (length < 3) {
          prefix.append((char) (ch | 0x20));
        }
      }
      for (int k = 0; k < KEYWORDS.length; k++) {
        if (KEYWORDS[k].contentEquals(prefix) && (length <= 3 || KEYWORD_INFO[k][0] == MONTH_NAME)) {
          return new Token(KEYWORD, KEYWORD_INFO[k][0], length, KEYWORD_INFO[k][1]);
        }
      }
      return new Token(KEYWORD, NOT_A_KEYWORD, length, 0);
    }
    if (Js.isSpace(ch)) {
      advance();
      return new Token(SPACE, 0, 1, 0);
    }
    if (ch == '(') {
      int balance = 0;
      do {
        balance += ch == ')' ? -1 : ch == '(' ? 1 : 0;
        advance();
      } while (balance > 0 && ch != 0);
      return new Token(UNKNOWN, 0, 1, -1);
    }
    advance();
    return new Token(UNKNOWN, 0, 1, -1);
  }

  private Token next() {
    Token result = next;
    next = scan();
    return result;
  }

  private boolean skip(int symbol) {
    if (next.is(symbol)) {
      next = scan();
      return true;
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Composers

  private static boolean between(int x, int lo, int hi) {
    return x >= lo && x <= hi;
  }

  private boolean addDay(int n) {
    if (dayCount >= 3) {
      return false;
    }
    day[dayCount++] = n;
    return true;
  }

  private boolean addTime(int n) {
    if (timeCount >= 4) {
      return false;
    }
    time[timeCount++] = n;
    return true;
  }

  private boolean addFinalTime(int n) {
    if (!addTime(n)) {
      return false;
    }
    while (timeCount < 4) {
      time[timeCount++] = 0;
    }
    return true;
  }

  private boolean timeExpects(int n) {
    return (timeCount == 1 && between(n, 0, 59)) || (timeCount == 2 && between(n, 0, 59))
        || (timeCount == 3 && between(n, 0, 999));
  }

  private void setZone(int offsetInHours) {
    tzSign = offsetInHours < 0 ? -1 : 1;
    tzHour = offsetInHours * tzSign;
    tzMinute = 0;
  }

  private static int readMilliseconds(Token token) {
    int number = token.value;
    int length = token.length;
    if (length == 1) {
      return number * 100;
    }
    if (length == 2) {
      return number * 10;
    }
    length = Math.min(length, MAX_SIGNIFICANT_DIGITS);
    for (; length > 3; length--) {
      number /= 10;
    }
    return number;
  }

  /** [year, month, day, hour, minute, second, ms, UTC offset in seconds or NaN], or null. */
  private double[] write() {
    double[] out = new double[8];
    if (dayCount < 1) {
      return null;
    }
    while (dayCount < 3) {
      day[dayCount++] = 1;
    }
    int year = 0;
    int month = namedMonth;
    int date;
    if (namedMonth == NONE) {
      boolean ymd = isoDate || !between(day[0], 1, 31);
      year = ymd ? day[0] : day[2];
      month = ymd ? day[1] : day[0];
      date = ymd ? day[2] : day[1];
    } else {
      boolean dayFirst = between(day[0], 1, 31);
      year = dayFirst ? day[1] : day[0];
      date = dayFirst ? day[0] : day[1];
    }
    if (!isoDate) {
      year += between(year, 0, 49) ? 2000 : between(year, 50, 99) ? 1900 : 0;
    }
    if (year < -(1 << 30) || year >= (1 << 30) || !between(month, 1, 12) || !between(date, 1, 31)) {
      return null;
    }
    while (timeCount < 4) {
      time[timeCount++] = 0;
    }
    int hour = time[0];
    if (hourOffset != NONE) {
      if (!between(hour, 0, 12)) {
        return null;
      }
      hour = hour % 12 + hourOffset;
    }
    if ((!between(hour, 0, 23) || !between(time[1], 0, 59) || !between(time[2], 0, 59) || !between(time[3], 0, 999))
        && (hour != 24 || time[1] != 0 || time[2] != 0 || time[3] != 0)) {
      return null;
    }
    double offset = Double.NaN;
    if (tzSign != NONE) {
      long total = ((tzHour == NONE ? 0 : tzHour) * 3600L + (tzMinute == NONE ? 0 : tzMinute) * 60L) & 0xFFFFFFFFL;
      if (total > (1L << 30) - 1) {
        return null;
      }
      offset = tzSign < 0 ? -total : total;
    }
    return new double[] {year, month - 1, date, hour, time[1], time[2], time[3], offset};
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Parsing

  private double[] parse() {
    Token token = parseIso();
    if (token.kind == INVALID) {
      return null;
    }
    boolean hasReadNumber = dayCount != 0;
    for (; token.kind != END; token = next()) {
      if (token.kind == NUMBER) {
        hasReadNumber = true;
        int n = token.value;
        if (skip(':')) {
          if (skip(':')) {
            if (timeCount != 0) {
              return null;
            }
            addTime(n);
            addTime(0);
          } else {
            if (!addTime(n)) {
              return null;
            }
            if (next.is('.')) {
              next();
            }
          }
        } else if (skip('.') && timeExpects(n)) {
          addTime(n);
          if (next.kind != NUMBER) {
            return null;
          }
          addFinalTime(readMilliseconds(next()));
        } else if (tzHour != NONE && tzMinute == NONE && between(n, 0, 59)) {
          tzMinute = n;
        } else if (timeExpects(n)) {
          addFinalTime(n);
          if (next.kind != END && next.kind != SPACE && !next.isZ() && !next.isSign()) {
            return null;
          }
        } else {
          if (!addDay(n)) {
            return null;
          }
          skip('-');
        }
      } else if (token.kind == KEYWORD) {
        if (token.keyword == AM_PM && timeCount != 0) {
          hourOffset = token.value;
        } else if (token.keyword == MONTH_NAME) {
          namedMonth = token.value;
          skip('-');
        } else if (token.keyword == TIME_ZONE_NAME && hasReadNumber) {
          setZone(token.value);
        } else if (hasReadNumber || next.kind == NUMBER) {
          return null;
        }
      } else if (token.isSign() && ((tzHour == 0 && tzMinute == 0) || timeCount != 0)) {
        tzSign = token.value == '-' ? -1 : 1;
        int n = 0;
        int length = 0;
        if (next.kind == NUMBER) {
          Token number = next();
          length = number.length;
          n = number.value;
        }
        hasReadNumber = true;
        if (next.is(':')) {
          tzHour = n;
          tzMinute = NONE;
        } else if (length == 1 || length == 2) {
          tzHour = n;
          tzMinute = 0;
        } else if (length == 3 || length == 4) {
          tzHour = n / 100;
          tzMinute = n % 100;
        } else {
          return null;
        }
      } else if ((token.isSign() || token.is(')')) && hasReadNumber) {
        return null;
      }
    }
    return write();
  }

  private static final Token INVALID_TOKEN = new Token(INVALID, 0, 0, -1);

  /** The ECMAScript date-time string format; answers the first token it does not handle. */
  private Token parseIso() {
    if (next.isSign()) {
      Token sign = next();
      if (!next.isNumber(6)) {
        return sign;
      }
      int year = next().value;
      if (sign.value == '-' && year == 0) {
        return sign;
      }
      addDay((sign.value == '-' ? -1 : 1) * year);
    } else if (next.isNumber(4)) {
      addDay(next().value);
    } else {
      return next();
    }
    if (skip('-')) {
      if (!next.isNumber(2) || !between(next.value, 1, 12)) {
        return next();
      }
      addDay(next().value);
      if (skip('-')) {
        if (!next.isNumber(2) || !between(next.value, 1, 31)) {
          return next();
        }
        addDay(next().value);
      }
    }
    if (!(next.kind == KEYWORD && next.keyword == TIME_SEPARATOR)) {
      if (next.kind != END) {
        return next();
      }
    } else {
      next();
      if (!next.isNumber(2) || !between(next.value, 0, 24)) {
        return INVALID_TOKEN;
      }
      boolean hourIs24 = next.value == 24;
      addTime(next().value);
      if (!skip(':') || !next.isNumber(2) || !between(next.value, 0, 59) || (hourIs24 && next.value > 0)) {
        return INVALID_TOKEN;
      }
      addTime(next().value);
      if (skip(':')) {
        if (!next.isNumber(2) || !between(next.value, 0, 59) || (hourIs24 && next.value > 0)) {
          return INVALID_TOKEN;
        }
        addTime(next().value);
        if (skip('.')) {
          if (next.kind != NUMBER || (hourIs24 && next.value > 0)) {
            return INVALID_TOKEN;
          }
          addTime(readMilliseconds(next()));
        }
      }
      if (next.isZ()) {
        next();
        setZone(0);
      } else if (next.isSign()) {
        tzSign = next().value == '+' ? 1 : -1;
        if (next.isNumber(4)) {
          int hourmin = next().value;
          if (!between(hourmin / 100, 0, 23) || !between(hourmin % 100, 0, 59)) {
            return INVALID_TOKEN;
          }
          tzHour = hourmin / 100;
          tzMinute = hourmin % 100;
        } else {
          if (!next.isNumber(2) || !between(next.value, 0, 23)) {
            return INVALID_TOKEN;
          }
          tzHour = next().value;
          if (!skip(':') || !next.isNumber(2) || !between(next.value, 0, 59)) {
            return INVALID_TOKEN;
          }
          tzMinute = next().value;
        }
      }
      if (next.kind != END) {
        return INVALID_TOKEN;
      }
    }
    if (tzHour == NONE && timeCount == 0) {
      setZone(0);
    }
    isoDate = true;
    return new Token(END, 0, 0, -1);
  }
}
