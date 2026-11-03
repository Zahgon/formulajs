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

import java.time.LocalDate;
import java.util.Locale;

/**
 * A JavaScript Date in a UTC time zone (local time is UTC): a mutable time value in milliseconds, NaN when invalid,
 * with the constructor, getters and setters computed as V8 computes them.
 */
public final class JsDate {
  private static final double MS_PER_DAY = 86400000.0;
  private static final double MAX_TIME = 8.64e15;
  private static final double MAX_TIME_BEFORE_UTC = MAX_TIME + 864000000.0;
  private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  private static final String[] MONTHS = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  private double time;

  private JsDate(double time) {
    this.time = timeClip(time);
  }

  /** {@code new Date(value)}. */
  public static JsDate of(Object value) {
    if (value instanceof JsDate d) {
      return new JsDate(d.time);
    }
    Object primitive = Js.toPrimitive(value, false);
    if (primitive instanceof String s) {
      return new JsDate(DateParser.parse(s));
    }
    return new JsDate(Js.toNumber(primitive));
  }

  /** {@code new Date(year, month[, day[, hours[, minutes[, seconds[, ms]]]]])} with numbers already converted. */
  public static JsDate of(double year, double month, double... rest) {
    double day = rest.length > 0 ? rest[0] : 1;
    double hours = rest.length > 1 ? rest[1] : 0;
    double minutes = rest.length > 2 ? rest[2] : 0;
    double seconds = rest.length > 3 ? rest[3] : 0;
    double ms = rest.length > 4 ? rest[4] : 0;
    if (!Double.isNaN(year)) {
      double y = Js.toIntegerOrInfinity(year);
      if (0 <= y && y <= 99) {
        year = 1900 + y;
      }
    }
    return new JsDate(localToUtc(makeDate(makeDay(year, month, day), makeTime(hours, minutes, seconds, ms))));
  }

  /** {@code new Date(year, month, ...)} with JavaScript values, each converted with ToNumber in order. */
  public static JsDate ofValues(Object... parts) {
    double[] n = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      n[i] = Js.toNumber(parts[i]);
    }
    double[] rest = java.util.Arrays.copyOfRange(n, 2, n.length);
    return of(n[0], n[1], rest);
  }

  public static JsDate fromTime(double time) {
    return new JsDate(time);
  }

  /** Date.parse. */
  public static double parse(Object value) {
    return DateParser.parse(Js.str(value));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // The date arithmetic of ECMA-262 as V8 implements it.

  static double makeDay(double year, double month, double date) {
    if (-1000000 <= year && year <= 1000000 && -10000000 <= month && month <= 10000000 && isFinite(date)) {
      int y = (int) year;
      int m = (int) month;
      y += m / 12;
      m %= 12;
      if (m < 0) {
        m += 12;
        y -= 1;
      }
      return LocalDate.of(y, m + 1, 1).toEpochDay() - 1 + Js.toIntegerOrInfinity(date);
    }
    return Double.NaN;
  }

  static double makeTime(double h, double m, double s, double ms) {
    if (isFinite(h) && isFinite(m) && isFinite(s) && isFinite(ms)) {
      return Js.toIntegerOrInfinity(h) * 3600000.0
          + Js.toIntegerOrInfinity(m) * 60000.0
          + Js.toIntegerOrInfinity(s) * 1000.0
          + Js.toIntegerOrInfinity(ms);
    }
    return Double.NaN;
  }

  static double makeDate(double day, double time) {
    if (isFinite(day) && isFinite(time)) {
      return time + day * MS_PER_DAY;
    }
    return Double.NaN;
  }

  static double localToUtc(double t) {
    if (t >= -MAX_TIME_BEFORE_UTC && t <= MAX_TIME_BEFORE_UTC) {
      return (double) (long) t;
    }
    return Double.NaN;
  }

  static double timeClip(double t) {
    if (!isFinite(t) || Math.abs(t) > MAX_TIME) {
      return Double.NaN;
    }
    return Js.toIntegerOrInfinity(t) + 0.0;
  }

  private static boolean isFinite(double d) {
    return !Double.isNaN(d) && !Double.isInfinite(d);
  }

  private long days() {
    return Math.floorDiv((long) time, 86400000L);
  }

  private LocalDate civil() {
    return LocalDate.ofEpochDay(days());
  }

  private double timeInDay() {
    return Math.floorMod((long) time, 86400000L);
  }

  public boolean isValid() {
    return !Double.isNaN(time);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Getters (local time is UTC)

  public double getTime() {
    return time;
  }

  public double getFullYear() {
    return isValid() ? civil().getYear() : Double.NaN;
  }

  public double getMonth() {
    return isValid() ? civil().getMonthValue() - 1 : Double.NaN;
  }

  public double getDate() {
    return isValid() ? civil().getDayOfMonth() : Double.NaN;
  }

  public double getDay() {
    return isValid() ? Math.floorMod(days() + 4, 7) : Double.NaN;
  }

  public double getHours() {
    return isValid() ? Math.floor(timeInDay() / 3600000.0) : Double.NaN;
  }

  public double getMinutes() {
    return isValid() ? Math.floor(timeInDay() / 60000.0) % 60 : Double.NaN;
  }

  public double getSeconds() {
    return isValid() ? Math.floor(timeInDay() / 1000.0) % 60 : Double.NaN;
  }

  public double getMilliseconds() {
    return isValid() ? timeInDay() % 1000 : Double.NaN;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Setters (local time is UTC); each takes JavaScript values and converts them with ToNumber.

  private double setLocal(double t) {
    time = timeClip(localToUtc(t));
    return time;
  }

  public double setFullYear(Object... args) {
    double y = Js.toNumber(args[0]);
    double m = 0;
    double dt = 1;
    double tid = 0;
    if (isValid()) {
      LocalDate c = civil();
      m = c.getMonthValue() - 1;
      dt = c.getDayOfMonth();
      tid = timeInDay();
    }
    if (args.length >= 2) {
      m = Js.toNumber(args[1]);
      if (args.length >= 3) {
        dt = Js.toNumber(args[2]);
      }
    }
    return setLocal(makeDate(makeDay(y, m, dt), tid));
  }

  public double setMonth(Object... args) {
    double m = Js.toNumber(args[0]);
    double dt = args.length >= 2 ? Js.toNumber(args[1]) : Double.NaN;
    if (!isValid()) {
      return time;
    }
    LocalDate c = civil();
    if (args.length < 2) {
      dt = c.getDayOfMonth();
    }
    return setLocal(makeDate(makeDay(c.getYear(), m, dt), timeInDay()));
  }

  public double setDate(Object value) {
    double dt = Js.toNumber(value);
    if (!isValid()) {
      return time;
    }
    LocalDate c = civil();
    return setLocal(makeDate(makeDay(c.getYear(), c.getMonthValue() - 1, dt), timeInDay()));
  }

  public double setHours(Object... args) {
    double h = Js.toNumber(args[0]);
    double m = args.length >= 2 ? Js.toNumber(args[1]) : Double.NaN;
    double s = args.length >= 3 ? Js.toNumber(args[2]) : Double.NaN;
    double ms = args.length >= 4 ? Js.toNumber(args[3]) : Double.NaN;
    if (!isValid()) {
      return time;
    }
    double tid = timeInDay();
    if (args.length < 2) {
      m = Math.floor(tid / 60000.0) % 60;
    }
    if (args.length < 3) {
      s = Math.floor(tid / 1000.0) % 60;
    }
    if (args.length < 4) {
      ms = tid % 1000;
    }
    return setLocal(makeDate(days(), makeTime(h, m, s, ms)));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Strings

  /** Date.prototype.toISOString. */
  public String toISOString() {
    if (!isValid()) {
      throw JsException.rangeError("Invalid time value");
    }
    LocalDate c = civil();
    int year = c.getYear();
    String y;
    if (year >= 0 && year <= 9999) {
      y = String.format(Locale.ROOT, "%04d", year);
    } else {
      y = (year < 0 ? "-" : "+") + String.format(Locale.ROOT, "%06d", Math.abs(year));
    }
    double tid = timeInDay();
    return String.format(Locale.ROOT, "%s-%02d-%02dT%02d:%02d:%02d.%03dZ", y, c.getMonthValue(), c.getDayOfMonth(),
        (int) (tid / 3600000), (int) (tid / 60000) % 60, (int) (tid / 1000) % 60, (int) (tid % 1000));
  }

  /** Date.prototype.toJSON: the ISO string, or null for an invalid date. */
  public Object toJSON() {
    return isValid() ? toISOString() : null;
  }

  /** Date.prototype.toString in the UTC time zone. */
  @Override
  public String toString() {
    if (!isValid()) {
      return "Invalid Date";
    }
    LocalDate c = civil();
    double tid = timeInDay();
    return String.format(Locale.ROOT, "%s %s %02d %04d %02d:%02d:%02d GMT+0000 (Coordinated Universal Time)",
        WEEKDAYS[(int) getDay()], MONTHS[c.getMonthValue() - 1], c.getDayOfMonth(), c.getYear(),
        (int) (tid / 3600000), (int) (tid / 60000) % 60, (int) (tid / 1000) % 60);
  }
}
