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

import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsDate;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.DateUtils;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** DateTime functions. */
public final class DateTime {
  private static final Object U = Js.UNDEFINED;
  private static final List<Object> WEEK_STARTS =
      Js.list(U, 0.0, 1.0, U, U, U, U, U, U, U, U, U, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 0.0);
  private static final List<Object> WEEK_TYPES = Js.list(nums(), nums(1, 2, 3, 4, 5, 6, 7), nums(7, 1, 2, 3, 4, 5, 6),
      nums(6, 0, 1, 2, 3, 4, 5), nums(), nums(), nums(), nums(), nums(), nums(), nums(), nums(7, 1, 2, 3, 4, 5, 6),
      nums(6, 7, 1, 2, 3, 4, 5), nums(5, 6, 7, 1, 2, 3, 4), nums(4, 5, 6, 7, 1, 2, 3), nums(3, 4, 5, 6, 7, 1, 2),
      nums(2, 3, 4, 5, 6, 7, 1), nums(1, 2, 3, 4, 5, 6, 7));
  private static final List<Object> WEEKEND_TYPES = Js.list(nums(), nums(6, 0), nums(0, 1), nums(1, 2), nums(2, 3),
      nums(3, 4), nums(4, 5), nums(5, 6), U, U, U, nums(0, 0), nums(1, 1), nums(2, 2), nums(3, 3), nums(4, 4),
      nums(5, 5), nums(6, 6));
  private static final double[] MASK_INDEX = {1, 2, 3, 4, 5, 6, 0};
  private static final double[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  private DateTime() {}

  private static List<Object> nums(double... values) {
    List<Object> out = new ArrayList<>();
    for (double v : values) {
      out.add(v);
    }
    return out;
  }

  /** The date a method is called on: anything else (a parse error) makes the call throw V8's TypeError. */
  static JsDate call(Object value, String callee) {
    if (value instanceof JsDate d) {
      return d;
    }
    throw JsException.notAFunction(callee);
  }

  private static Object part(Object[] args, ToDoubleFunction<JsDate> getter) {
    Object date = Common.parseDate(Js.arg(args, 0));
    return date instanceof JsDate d ? (Object) getter.applyAsDouble(d) : date;
  }

  private static JsDate startOfDay(JsDate date) {
    JsDate newDate = JsDate.of(date);
    newDate.setHours(0.0, 0.0, 0.0, 0.0);
    return newDate;
  }

  /** Holidays as an array of parsed dates (the argument array is converted in place), or the first parse error. */
  private static Object holidays(Object holidays) {
    List<Object> list = holidays == U ? new ArrayList<>() : holidays instanceof List ? Js.asList(holidays)
        : Js.list(holidays);
    for (int i = 0; i < list.size(); i++) {
      Object h = Common.parseDate(list.get(i));
      if (h instanceof JsError) {
        return h;
      }
      list.set(i, h);
    }
    return list;
  }

  private static boolean isHoliday(List<Object> holidays, JsDate day) {
    for (Object h : holidays) {
      JsDate holiday = (JsDate) h;
      if (holiday.getDate() == day.getDate() && holiday.getMonth() == day.getMonth()
          && holiday.getFullYear() == day.getFullYear()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isMask(String s) {
    if (s.length() != 7) {
      return false;
    }
    for (int i = 0; i < 7; i++) {
      char c = s.charAt(i);
      if (c != '0' && c != '1' && c != '|') {
        return false;
      }
    }
    return true;
  }

  /** Returns the number of whole workdays between two dates using parameters to indicate the weekend days. */
  public static Object NETWORKDAYS_INTL(Object... args) {
    Object start = Common.parseDate(Js.arg(args, 0));
    if (start instanceof JsError) {
      return start;
    }
    Object end = Common.parseDate(Js.arg(args, 1));
    if (end instanceof JsError) {
      return end;
    }
    boolean isMask = false;
    List<Object> maskDays = new ArrayList<>();
    Object weekend = Js.arg(args, 2);
    if (weekend == U) {
      weekend = WEEKEND_TYPES.get(1);
    } else if (weekend instanceof String s && isMask(s)) {
      isMask = true;
      weekend = Js.split(s, "");
      for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == '1') {
          maskDays.add(MASK_INDEX[i]);
        }
      }
    } else {
      weekend = Js.get(WEEKEND_TYPES, weekend);
    }
    if (!(weekend instanceof List)) {
      return Errors.VALUE;
    }
    Object hs = holidays(Js.arg(args, 3));
    if (hs instanceof JsError) {
      return hs;
    }
    List<Object> holidays = Js.asList(hs);
    Object w0 = Js.get(weekend, 0.0);
    Object w1 = Js.get(weekend, 1.0);
    JsDate day = (JsDate) start;
    double days = Js.round((((JsDate) end).getTime() - day.getTime()) / (1000 * 60 * 60 * 24)) + 1;
    double total = days;
    for (double i = 0; i < days; i++) {
      Object d = day.getDay();
      boolean dec = isMask ? Js.includes(maskDays, d) : Js.strictEquals(d, w0) || Js.strictEquals(d, w1);
      if (isHoliday(holidays, day)) {
        dec = true;
      }
      if (dec) {
        total--;
      }
      day.setDate(day.getDate() + 1);
    }
    return total;
  }

  /** Returns the serial number of the date before or after a number of workdays, with custom weekend days. */
  public static Object WORKDAY_INTL(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    Object daysArg = Common.parseNumber(Js.arg(args, 1));
    if (daysArg instanceof JsError) {
      return daysArg;
    }
    Object weekend = Js.arg(args, 2);
    weekend = weekend == U ? WEEKEND_TYPES.get(1) : Js.get(WEEKEND_TYPES, weekend);
    if (!(weekend instanceof List)) {
      return Errors.VALUE;
    }
    Object hs = holidays(Js.arg(args, 3));
    if (hs instanceof JsError) {
      return hs;
    }
    List<Object> holidays = Js.asList(hs);
    Object w0 = Js.get(weekend, 0.0);
    Object w1 = Js.get(weekend, 1.0);
    JsDate start = (JsDate) parsed;
    double days = (Double) daysArg;
    double d = 0;
    double sign = Js.sign(days);
    while (d < days * sign) {
      start.setDate(start.getDate() + sign);
      Object day = start.getDay();
      if (Js.strictEquals(day, w0) || Js.strictEquals(day, w1)) {
        continue;
      }
      if (isHoliday(holidays, start)) {
        d--;
      }
      d++;
    }
    if (start.getFullYear() < 1900) {
      return Errors.VALUE;
    }
    return start;
  }

  /** Returns the serial number of a particular date. */
  public static Object DATE(Object... args) {
    Object year = Common.parseNumber(Js.arg(args, 0));
    Object month = Common.parseNumber(Js.arg(args, 1));
    Object day = Common.parseNumber(Js.arg(args, 2));
    Object result;
    if (Common.anyIsError(year, month, day)) {
      result = Errors.VALUE;
    } else {
      JsDate date = JsDate.of((Double) year, (Double) month - 1, (Double) day);
      result = date.getFullYear() < 0 ? Errors.NUM : date;
    }
    return DateUtils.dateOrSerial(result);
  }

  /** Calculates the number of days, months, or years between two dates. */
  public static Object DATEDIF(Object... args) {
    Object unitArg = Js.arg(args, 2);
    if (Js.isNullish(unitArg)) {
      throw JsException.cannotRead(unitArg, "toUpperCase");
    }
    if (!(unitArg instanceof String)) {
      throw JsException.notAFunction("unit.toUpperCase");
    }
    String unit = Js.toUpperCase((String) unitArg);
    JsDate start = call(Common.parseDate(Js.arg(args, 0)), "start_date.getFullYear");
    JsDate end = call(Common.parseDate(Js.arg(args, 1)), "end_date.getFullYear");
    double sy = start.getFullYear();
    double sm = start.getMonth();
    double sd = start.getDate();
    double ey = end.getFullYear();
    double em = end.getMonth();
    double ed = end.getDate();
    switch (unit) {
      case "Y":
        return Math.floor(Js.toNumber(YEARFRAC(start, end)));
      case "D":
        return DAYS(end, start);
      case "M":
      case "YM": {
        double result = em - sm + 12 * (ey - sy);
        if (ed < sd) {
          result--;
        }
        return unit.equals("M") ? result : result % 12;
      }
      case "MD":
        if (sd <= ed) {
          return ed - sd;
        }
        if (em == 0) {
          start.setFullYear(ey - 1);
          start.setMonth(12.0);
        } else {
          start.setFullYear(ey);
          start.setMonth(em - 1);
        }
        return DAYS(end, start);
      case "YD":
        start.setFullYear(em > sm || (em == sm && ed < sd) ? ey : ey - 1);
        return DAYS(end, start);
      default:
        return U;
    }
  }

  /** Converts a date in the form of text to a serial number. */
  public static Object DATEVALUE(Object... args) {
    Object text = Js.arg(args, 0);
    if (!(text instanceof String) || Double.isNaN(JsDate.parse(text))) {
      return Errors.VALUE;
    }
    return DateUtils.dateOrSerial(JsDate.of(text));
  }

  /** Converts a serial number to a day of the month. */
  public static Object DAY(Object... args) {
    return part(args, JsDate::getDate);
  }

  /** Returns the number of days between two dates. */
  public static Object DAYS(Object... args) {
    Object end = Common.parseDate(Js.arg(args, 0));
    Object start = Common.parseDate(Js.arg(args, 1));
    if (end instanceof JsError) {
      return end;
    }
    if (start instanceof JsError) {
      return start;
    }
    return DateUtils.dateToSerial(startOfDay((JsDate) end)) - DateUtils.dateToSerial(startOfDay((JsDate) start));
  }

  /** Calculates the number of days between two dates based on a 360-day year. */
  public static Object DAYS360(Object... args) {
    Object methodArg = Js.arg(args, 2);
    Object method = Common.parseBool(Js.truthy(methodArg) ? methodArg : "false");
    Object startArg = Common.parseDate(Js.arg(args, 0));
    Object endArg = Common.parseDate(Js.arg(args, 1));
    if (startArg instanceof JsError) {
      return startArg;
    }
    if (endArg instanceof JsError) {
      return endArg;
    }
    if (method instanceof JsError) {
      return method;
    }
    JsDate start = (JsDate) startArg;
    JsDate end = (JsDate) endArg;
    double sm = start.getMonth();
    double em = end.getMonth();
    double sd;
    double ed;
    if (Js.truthy(method)) {
      sd = start.getDate() == 31 ? 30 : start.getDate();
      ed = end.getDate() == 31 ? 30 : end.getDate();
    } else {
      double smd = JsDate.of(start.getFullYear(), sm + 1, 0).getDate();
      double emd = JsDate.of(end.getFullYear(), em + 1, 0).getDate();
      sd = start.getDate() == smd ? 30 : start.getDate();
      if (end.getDate() == emd) {
        if (sd < 30) {
          em++;
          ed = 1;
        } else {
          ed = 30;
        }
      } else {
        ed = end.getDate();
      }
    }
    return 360 * (end.getFullYear() - start.getFullYear()) + 30 * (em - sm) + (ed - sd);
  }

  /** Returns the serial number of the date that is the indicated number of months before or after the start date. */
  public static Object EDATE(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    Object months = Js.arg(args, 1);
    if (Js.isNaN(months)) {
      return Errors.VALUE;
    }
    JsDate start = (JsDate) parsed;
    double storedDay = start.getDate();
    start.setDate(1.0);
    start.setMonth(start.getMonth() + Js.parseInt(months, 10.0));
    double targetMonth = start.getMonth();
    if (storedDay > 28) {
      double daysInTargetMonth = targetMonth >= 0 && targetMonth < 12 ? DAYS_IN_MONTH[(int) targetMonth] : Double.NaN;
      double targetYear = start.getFullYear();
      if (targetMonth == 1 && ((targetYear % 4 == 0 && targetYear % 100 != 0) || targetYear % 400 == 0)) {
        daysInTargetMonth = 29;
      }
      storedDay = Math.min(storedDay, daysInTargetMonth);
    }
    start.setDate(storedDay);
    return DateUtils.dateOrSerial(start);
  }

  /** Returns the serial number of the last day of the month before or after a specified number of months. */
  public static Object EOMONTH(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    Object months = Js.arg(args, 1);
    if (Js.isNaN(months)) {
      return Errors.VALUE;
    }
    JsDate start = (JsDate) parsed;
    double m = Js.parseInt(months, 10.0);
    return DateUtils.dateOrSerial(JsDate.of(start.getFullYear(), start.getMonth() + m + 1, 0));
  }

  /** Converts a serial number to an hour. */
  public static Object HOUR(Object... args) {
    return part(args, JsDate::getHours);
  }

  /** Returns the number of the ISO week number of the year for a given date. */
  public static Object ISOWEEKNUM(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    JsDate date = startOfDay((JsDate) parsed);
    double day = date.getDay();
    date.setDate(date.getDate() + 4 - (day == 0 || Double.isNaN(day) ? 7 : day));
    JsDate yearStart = JsDate.of(date.getFullYear(), 0, 1);
    return Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  }

  /** Converts a serial number to a minute. */
  public static Object MINUTE(Object... args) {
    return part(args, JsDate::getMinutes);
  }

  /** Converts a serial number to a month. */
  public static Object MONTH(Object... args) {
    return part(args, d -> d.getMonth() + 1);
  }

  /** Returns the number of whole workdays between two dates. */
  public static Object NETWORKDAYS(Object... args) {
    return NETWORKDAYS_INTL(Js.arg(args, 0), Js.arg(args, 1), 1.0, Js.arg(args, 2));
  }

  /** Converts a serial number to a second. */
  public static Object SECOND(Object... args) {
    return part(args, JsDate::getSeconds);
  }

  /** Returns the serial number of a particular time. */
  public static Object TIME(Object... args) {
    Object hour = Common.parseNumber(Js.arg(args, 0));
    Object minute = Common.parseNumber(Js.arg(args, 1));
    Object second = Common.parseNumber(Js.arg(args, 2));
    if (Common.anyIsError(hour, minute, second)) {
      return Errors.VALUE;
    }
    double h = (Double) hour;
    double m = (Double) minute;
    double s = (Double) second;
    if (h < 0 || m < 0 || s < 0) {
      return Errors.NUM;
    }
    return (3600 * h + 60 * m + s) / 86400;
  }

  /** Converts a time in the form of text to a serial number. */
  public static Object TIMEVALUE(Object... args) {
    return part(args, d -> (3600 * d.getHours() + 60 * d.getMinutes() + d.getSeconds()) / 86400);
  }

  /** Converts a serial number to a day of the week. */
  public static Object WEEKDAY(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    Object returnType = Js.arg(args, 1, 1.0);
    return Js.get(Js.get(WEEK_TYPES, returnType), ((JsDate) parsed).getDay());
  }

  /** Converts a serial number to a number representing where the week falls numerically with a year. */
  public static Object WEEKNUM(Object... args) {
    Object parsed = Common.parseDate(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    JsDate date = (JsDate) parsed;
    Object returnType = Js.arg(args, 1, 1.0);
    if (Js.strictEquals(returnType, 21.0)) {
      return ISOWEEKNUM(date);
    }
    double weekStart = Js.toNumber(Js.get(WEEK_STARTS, returnType));
    JsDate jan = JsDate.of(date.getFullYear(), 0, 1);
    double inc = jan.getDay() < weekStart ? 1 : 0;
    double janTime = jan.getTime() - Math.abs(jan.getDay() - weekStart) * 24 * 60 * 60 * 1000;
    return Math.floor((date.getTime() - janTime) / (1000 * 60 * 60 * 24) / 7 + 1) + inc;
  }

  /** Returns the serial number of the date before or after a specified number of workdays. */
  public static Object WORKDAY(Object... args) {
    return WORKDAY_INTL(Js.arg(args, 0), Js.arg(args, 1), 1.0, Js.arg(args, 2));
  }

  /** Converts a serial number to a year. */
  public static Object YEAR(Object... args) {
    return part(args, JsDate::getFullYear);
  }

  private static boolean isLeapYear(double year) {
    return JsDate.of(year, 1, 29).getMonth() == 1;
  }

  private static double daysBetween(JsDate start, JsDate end) {
    return Math.ceil((end.getTime() - start.getTime()) / 1000 / 60 / 60 / 24);
  }

  private static boolean feb29Between(JsDate date1, JsDate date2) {
    double year1 = date1.getFullYear();
    double mar1year1 = JsDate.of(year1, 2, 1).getTime();
    if (isLeapYear(year1) && date1.getTime() < mar1year1 && date2.getTime() >= mar1year1) {
      return true;
    }
    double year2 = date2.getFullYear();
    double mar1year2 = JsDate.of(year2, 2, 1).getTime();
    return isLeapYear(year2) && date2.getTime() >= mar1year2 && date1.getTime() < mar1year2;
  }

  /** Returns the year fraction representing the number of whole days between start_date and end_date. */
  public static Object YEARFRAC(Object... args) {
    Object startArg = Common.parseDate(Js.arg(args, 0));
    if (startArg instanceof JsError) {
      return startArg;
    }
    Object endArg = Common.parseDate(Js.arg(args, 1));
    if (endArg instanceof JsError) {
      return endArg;
    }
    JsDate start = (JsDate) startArg;
    JsDate end = (JsDate) endArg;
    Object basis = Js.arg(args, 2);
    if (!Js.truthy(basis)) {
      basis = 0.0;
    }
    double sd = start.getDate();
    double sm = start.getMonth() + 1;
    double sy = start.getFullYear();
    double ed = end.getDate();
    double em = end.getMonth() + 1;
    double ey = end.getFullYear();
    if (!(basis instanceof Double b)) {
      return U;
    }
    if (b == 0) {
      if (sd == 31 && ed == 31) {
        sd = 30;
        ed = 30;
      } else if (sd == 31) {
        sd = 30;
      } else if (sd == 30 && ed == 31) {
        ed = 30;
      }
      return (ed + em * 30 + ey * 360 - (sd + sm * 30 + sy * 360)) / 360;
    }
    if (b == 1) {
      double ylength = 365;
      if (sy == ey || (sy + 1 == ey && (sm > em || (sm == em && sd >= ed)))) {
        if ((sy == ey && isLeapYear(sy)) || feb29Between(start, end) || (em == 1 && ed == 29)) {
          ylength = 366;
        }
        return daysBetween(start, end) / ylength;
      }
      double years = ey - sy + 1;
      double days = (JsDate.of(ey + 1, 0, 1).getTime() - JsDate.of(sy, 0, 1).getTime()) / 1000 / 60 / 60 / 24;
      double average = days / years;
      return daysBetween(start, end) / average;
    }
    if (b == 2) {
      return daysBetween(start, end) / 360;
    }
    if (b == 3) {
      return daysBetween(start, end) / 365;
    }
    if (b == 4) {
      return (ed + em * 30 + ey * 360 - (sd + sm * 30 + sy * 360)) / 360;
    }
    return U;
  }

  static void register(Map<String, Fn> ops) {
    ops.put("DATE", DateTime::DATE);
    ops.put("DATEDIF", DateTime::DATEDIF);
    ops.put("DATEVALUE", DateTime::DATEVALUE);
    ops.put("DAY", DateTime::DAY);
    ops.put("DAYS", DateTime::DAYS);
    ops.put("DAYS360", DateTime::DAYS360);
    ops.put("EDATE", DateTime::EDATE);
    ops.put("EOMONTH", DateTime::EOMONTH);
    ops.put("HOUR", DateTime::HOUR);
    ops.put("ISOWEEKNUM", DateTime::ISOWEEKNUM);
    ops.put("MINUTE", DateTime::MINUTE);
    ops.put("MONTH", DateTime::MONTH);
    ops.put("NETWORKDAYS", DateTime::NETWORKDAYS);
    ops.put("NETWORKDAYS.INTL", DateTime::NETWORKDAYS_INTL);
    ops.put("SECOND", DateTime::SECOND);
    ops.put("TIME", DateTime::TIME);
    ops.put("TIMEVALUE", DateTime::TIMEVALUE);
    ops.put("WEEKDAY", DateTime::WEEKDAY);
    ops.put("WEEKNUM", DateTime::WEEKNUM);
    ops.put("WORKDAY", DateTime::WORKDAY);
    ops.put("WORKDAY.INTL", DateTime::WORKDAY_INTL);
    ops.put("YEAR", DateTime::YEAR);
    ops.put("YEARFRAC", DateTime::YEARFRAC);
  }
}
