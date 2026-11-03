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
import io.github.formulajs.js.JsDate;

/** utils/date.js: the serial-number switch and the conversions between Excel serial numbers and dates. */
public final class DateUtils {
  private static boolean returnSerial;

  private DateUtils() {}

  public static boolean returnSerial() {
    return returnSerial;
  }

  public static void useSerial() {
    returnSerial = true;
  }

  public static void useDate() {
    returnSerial = false;
  }

  public static JsDate serialToDate(Object serial) {
    if (Js.lt(serial, 60.0)) {
      serial = Js.add(serial, 1.0);
    }
    double s = Js.toNumber(serial);
    double utcDays = Math.floor(s - 25569);
    double utcValue = utcDays * 86400;
    JsDate dateInfo = JsDate.fromTime(utcValue * 1000);
    double fractionalDay = s - Math.floor(s) + 0.0000001;
    double totalSeconds = Math.floor(86400 * fractionalDay);
    double seconds = totalSeconds % 60;
    totalSeconds -= seconds;
    double hours = Math.floor(totalSeconds / (60 * 60));
    double minutes = Math.floor(totalSeconds / 60) % 60;
    double days = dateInfo.getDate();
    double month = dateInfo.getMonth();
    if (Js.ge(serial, 60.0) && Js.lt(serial, 61.0)) {
      days = 29;
      month = 1;
    }
    return JsDate.of(dateInfo.getFullYear(), month, days, hours, minutes, seconds);
  }

  public static double dateToSerial(Object date) {
    JsDate d1900 = JsDate.of(1900, 0, 1);
    double addOn = Js.gt(date, -2203891200000.0) ? 2 : 1;
    return Math.ceil((Js.toNumber(date) - d1900.getTime()) / 86400000) + addOn;
  }

  /** A date, or its serial number while the serial switch is on. */
  public static Object dateOrSerial(Object date) {
    return returnSerial ? (Object) dateToSerial(date) : date;
  }
}
