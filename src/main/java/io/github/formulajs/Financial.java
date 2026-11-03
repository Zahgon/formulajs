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
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Financial functions. */
public final class Financial {
  private static final double LN10 = 2.302585092994046;
  private static final double LN2 = 0.6931471805599453;

  private Financial() {}

  private static double num(Object v) {
    return Js.toNumber(v);
  }

  private static Object orZero(Object v) {
    return Js.truthy(v) ? v : 0.0;
  }

  /** utils.parseNumber of each value, or null when one of them is an error. */
  private static double[] numbers(Object... values) {
    double[] out = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      Object n = Common.parseNumber(values[i]);
      if (n instanceof JsError) {
        return null;
      }
      out[i] = (Double) n;
    }
    return out;
  }

  /** The first n arguments through utils.parseNumber, or null when one of them is an error. */
  private static double[] numbers(Object[] args, int n) {
    Object[] values = new Object[n];
    for (int i = 0; i < n; i++) {
      values[i] = Js.arg(args, i);
    }
    return numbers(values);
  }

  private static Object validate(Object value, double... allowed) {
    Object n = Common.parseNumber(value);
    for (double a : allowed) {
      if (n instanceof Double d && d == a) {
        return n;
      }
    }
    return Errors.NUM;
  }

  private static Object validateFrequency(Object frequency) {
    return validate(frequency, 1, 2, 4);
  }

  private static Object validateBasis(Object basis) {
    return validate(basis, 0, 1, 2, 3, 4);
  }

  private static JsDate ensureDate(Object d) {
    return d instanceof JsDate date ? date : JsDate.of(d);
  }

  private static JsDate lastCoupDateBeforeSettlement(JsDate settlement, Object maturity, double frequency) {
    Object parsed = Common.parseDate(maturity);
    double year = settlement.getFullYear();
    JsDate date = DateTime.call(parsed, "date.setFullYear");
    date.setFullYear(year);
    if (date.getTime() < settlement.getTime()) {
      date.setFullYear(date.getFullYear() + 1);
    }
    while (date.getTime() > settlement.getTime()) {
      date.setMonth(date.getMonth() + -12 / frequency);
    }
    return date;
  }

  /** Returns the accrued interest for a security that pays periodic interest. */
  public static Object ACCRINT(Object... args) {
    JsDate issue = ensureDate(Js.arg(args, 0));
    JsDate firstInterest = ensureDate(Js.arg(args, 1));
    JsDate settlement = ensureDate(Js.arg(args, 2));
    Object rate = Js.arg(args, 3);
    Object par = Js.arg(args, 4);
    Object frequency = validateFrequency(Js.arg(args, 5));
    Object basis = validateBasis(Js.arg(args, 6));
    if (Common.anyError(frequency, basis) != Js.UNDEFINED) {
      return Errors.NUM;
    }
    if (!issue.isValid() || !firstInterest.isValid() || !settlement.isValid()) {
      return Errors.VALUE;
    }
    if (Js.le(rate, 0.0) || Js.le(par, 0.0) || settlement.getTime() <= issue.getTime()) {
      return Errors.NUM;
    }
    return num(orZero(par)) * num(rate) * num(DateTime.YEARFRAC(issue, settlement, orZero(basis)));
  }

  /** Returns the number of days in the coupon period that contains the settlement date. */
  public static Object COUPDAYS(Object... args) {
    Object basis = validateBasis(Js.arg(args, 3));
    Object frequency = validateFrequency(Js.arg(args, 2));
    Object settlement = Common.parseDate(Js.arg(args, 0));
    Object maturity = Common.parseDate(Js.arg(args, 1));
    if (Common.anyError(settlement, maturity) != Js.UNDEFINED) {
      return Errors.VALUE;
    }
    if (Common.anyError(frequency, basis) != Js.UNDEFINED || Js.ge(settlement, maturity)) {
      return Errors.NUM;
    }
    double f = (Double) frequency;
    double b = (Double) basis;
    if (b == 1) {
      JsDate date = lastCoupDateBeforeSettlement((JsDate) settlement, maturity, f);
      JsDate nextDate = DateTime.call(Common.parseDate(date), "nextDate.getMonth");
      nextDate.setMonth(nextDate.getMonth() + 12 / f);
      return DateTime.DATEDIF(date, nextDate, "D");
    }
    return b == 3 ? 365 / f : 360 / f;
  }

  /** Returns the cumulative interest paid between two periods. */
  public static Object CUMIPMT(Object... args) {
    return cumulative(args, true);
  }

  /** Returns the cumulative principal paid on a loan between two periods. */
  public static Object CUMPRINC(Object... args) {
    return cumulative(args, false);
  }

  private static Object cumulative(Object[] args, boolean interestOnly) {
    double[] a = numbers(args, 3);
    Object startPeriod = Js.arg(args, 3);
    Object endPeriod = Js.arg(args, 4);
    Object type = Js.arg(args, 5);
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double pv = a[2];
    if (rate <= 0 || a[1] <= 0 || pv <= 0) {
      return Errors.NUM;
    }
    if (Js.lt(startPeriod, 1.0) || Js.lt(endPeriod, 1.0) || Js.gt(startPeriod, endPeriod)) {
      return Errors.NUM;
    }
    boolean type0 = Js.strictEquals(type, 0.0);
    boolean type1 = Js.strictEquals(type, 1.0);
    if (!type0 && !type1) {
      return Errors.NUM;
    }
    Object payment = PMT(rate, a[1], pv, 0.0, type);
    double p = num(payment);
    Object total = 0.0;
    if (Js.strictEquals(startPeriod, 1.0)) {
      if (!interestOnly) {
        total = type0 ? Js.add(payment, pv * rate) : payment;
      } else if (type0) {
        total = -pv;
      }
      startPeriod = num(startPeriod) + 1;
    }
    for (Object i = startPeriod; Js.le(i, endPeriod); i = num(i) + 1) {
      double d = type1 ? num(FV(rate, num(i) - 2, payment, pv, 1.0)) - p : num(FV(rate, num(i) - 1, payment, pv, 0.0));
      total = Js.add(total, interestOnly ? d : p - d * rate);
    }
    return interestOnly ? (Object) (num(total) * rate) : total;
  }

  /** Returns the depreciation of an asset for a specified period by using the fixed-declining balance method. */
  public static Object DB(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3), Js.arg(args, 4, 12.0));
    if (a == null) {
      return Errors.VALUE;
    }
    double cost = a[0];
    double salvage = a[1];
    double life = a[2];
    double period = a[3];
    double month = a[4];
    if (cost < 0 || salvage < 0 || life < 0 || period < 0) {
      return Errors.NUM;
    }
    if (!(month >= 1 && month <= 12 && month == Math.floor(month)) || period > life) {
      return Errors.NUM;
    }
    if (salvage >= cost) {
      return 0.0;
    }
    double rate = num(Js.toFixed(1 - Js.pow(salvage / cost, 1 / life), 3.0));
    double initial = (cost * rate * month) / 12;
    double total = initial;
    double current = 0;
    double ceiling = period == life ? life - 1 : period;
    for (double i = 2; i <= ceiling; i++) {
      current = (cost - total) * rate;
      total += current;
    }
    return period == 1 ? initial : period == life ? (cost - total) * rate : current;
  }

  /** Returns the depreciation of an asset for a specified period by the double-declining balance method. */
  public static Object DDB(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3), Js.arg(args, 4, 2.0));
    if (a == null) {
      return Errors.VALUE;
    }
    double cost = a[0];
    double salvage = a[1];
    double life = a[2];
    double period = a[3];
    double factor = a[4];
    if (cost < 0 || salvage < 0 || life < 0 || period < 0 || factor <= 0 || period > life) {
      return Errors.NUM;
    }
    if (salvage >= cost) {
      return 0.0;
    }
    double total = 0;
    double current = 0;
    for (double i = 1; i <= period; i++) {
      current = Math.min((cost - total) * (factor / life), cost - salvage - total);
      total += current;
    }
    return current;
  }

  /** The checks DISC and PRICEDISC share: an error, or {price or discount, redemption, basis days, period days}. */
  private static Object discount(Object[] args) {
    Object settlement = Common.parseDate(Js.arg(args, 0));
    Object maturity = Common.parseDate(Js.arg(args, 1));
    double[] a = numbers(Js.arg(args, 2), Js.arg(args, 3), Js.arg(args, 4));
    if (a == null || Common.anyIsError(settlement, maturity)) {
      return Errors.VALUE;
    }
    if (a[0] <= 0 || a[1] <= 0) {
      return Errors.NUM;
    }
    if (Js.ge(settlement, maturity)) {
      return Errors.VALUE;
    }
    double b = Js.truthy(a[2]) ? a[2] : 0;
    if (b == 0 || b == 4) {
      return new double[] {a[0], a[1], 360, num(DateTime.DAYS360(settlement, maturity, b == 4))};
    } else if (b == 1 || b == 2 || b == 3) {
      return new double[] {a[0], a[1], b == 2 ? 360 : 365, num(DateTime.DATEDIF(settlement, maturity, "D"))};
    }
    return Errors.NUM;
  }

  /** Returns the discount rate for a security. */
  public static Object DISC(Object... args) {
    Object d = discount(args);
    return d instanceof double[] v ? (Object) ((((v[1] - v[0]) / v[1]) * v[2]) / v[3]) : d;
  }

  private static Object dollar(Object[] args, boolean toDecimal) {
    double[] a = numbers(args, 2);
    if (a == null) {
      return Errors.VALUE;
    }
    double dollar = a[0];
    double fraction = a[1];
    if (fraction < 0) {
      return Errors.NUM;
    }
    if (fraction >= 0 && fraction < 1) {
      return Errors.DIV0;
    }
    fraction = Js.parseInt(fraction, 10.0);
    double result = Js.parseInt(dollar, 10.0);
    if (!toDecimal) {
      return result + (dollar % 1) * Js.pow(10, -Math.ceil(StrictMath.log(fraction) / LN10)) * fraction;
    }
    result += ((dollar % 1) * Js.pow(10, Math.ceil(StrictMath.log(fraction) / LN10))) / fraction;
    double power = Js.pow(10, Math.ceil(StrictMath.log(fraction) / LN2) + 1);
    return Js.round(result * power) / power;
  }

  /** Converts a dollar price expressed as a fraction into a dollar price expressed as a decimal number. */
  public static Object DOLLARDE(Object... args) {
    return dollar(args, true);
  }

  /** Converts a dollar price expressed as a decimal number into a dollar price expressed as a fraction. */
  public static Object DOLLARFR(Object... args) {
    return dollar(args, false);
  }

  /** Returns the effective annual interest rate. */
  public static Object EFFECT(Object... args) {
    return effectOrNominal(args, true);
  }

  /** Returns the annual nominal interest rate. */
  public static Object NOMINAL(Object... args) {
    return effectOrNominal(args, false);
  }

  private static Object effectOrNominal(Object[] args, boolean effect) {
    double[] a = numbers(args, 2);
    if (a == null) {
      return Errors.VALUE;
    }
    if (a[0] <= 0 || a[1] < 1) {
      return Errors.NUM;
    }
    double npery = Js.parseInt(a[1], 10.0);
    return effect ? Js.pow(1 + a[0] / npery, npery) - 1 : (Js.pow(a[0] + 1, 1 / npery) - 1) * npery;
  }

  /** Returns the future value of an investment. */
  public static Object FV(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), orZero(Js.arg(args, 3)),
        orZero(Js.arg(args, 4)));
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double nper = a[1];
    double payment = a[2];
    double value = a[3];
    if (rate == 0) {
      return -(value + payment * nper);
    }
    double term = Js.pow(1 + rate, nper);
    return -(a[4] == 1 ? value * term + (payment * (1 + rate) * (term - 1)) / rate
        : value * term + (payment * (term - 1)) / rate);
  }

  /** Returns the future value of an initial principal after applying a series of compound interest rates. */
  public static Object FVSCHEDULE(Object... args) {
    Object principal = Common.parseNumber(Js.arg(args, 0));
    Object schedule = Common.parseNumberArray(Common.flatten(Js.arg(args, 1)));
    if (Common.anyIsError(principal, schedule)) {
      return Errors.VALUE;
    }
    double future = (Double) principal;
    for (Object rate : Js.asList(schedule)) {
      future *= 1 + (Double) rate;
    }
    return future;
  }

  /** Returns the interest payment for an investment for a given period. */
  public static Object IPMT(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3), orZero(Js.arg(args, 4)),
        orZero(Js.arg(args, 5)));
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double per = a[1];
    double pv = a[3];
    boolean type1 = a[5] == 1;
    Object payment = PMT(rate, a[2], pv, a[4], a[5]);
    double interest = per == 1 ? (type1 ? 0 : -pv)
        : type1 ? num(FV(rate, per - 2, payment, pv, 1.0)) - num(payment) : num(FV(rate, per - 1, payment, pv, 0.0));
    return interest * rate;
  }

  private static double irrNpv(double[] cashFlows, double rate) {
    rate = Js.round(rate * 1e10) / 1e10;
    if (rate <= -1) {
      rate = -0.999999999;
    }
    double result = cashFlows[0];
    double r = 1 + rate;
    double factor = 1;
    for (int i = 1; i < cashFlows.length; i++) {
      factor *= r;
      result += cashFlows[i] / factor;
    }
    return result;
  }

  /** Returns the internal rate of return for a series of cash flows. */
  public static Object IRR(Object... args) {
    Object guessArg = Js.arg(args, 1);
    Object guess = guessArg instanceof Double ? guessArg : guessArg == Js.UNDEFINED ? 0.1
        : Common.parseNumber(guessArg);
    List<Object> values = new ArrayList<>();
    for (Object v : Common.flatten(Js.arg(args, 0))) {
      if (Common.isDefined(v)) {
        values.add(v);
      }
    }
    if (Common.anyIsError(Common.parseNumberArray(values), guess)) {
      return Errors.VALUE;
    }
    double[] cf = new double[values.size()];
    boolean positive = false;
    boolean negative = false;
    for (int i = 0; i < cf.length; i++) {
      cf[i] = (Double) values.get(i);
      positive |= cf[i] > 0;
      negative |= cf[i] < 0;
    }
    if (!positive || !negative) {
      return Errors.NUM;
    }
    final double epsMax = 1e-10;
    double rate = (Double) guess;
    double prevRate = rate;
    for (int iteration = 0; iteration < 1000; iteration++) {
      double currentNpv = irrNpv(cf, rate);
      if (Math.abs(currentNpv) < epsMax) {
        return rate;
      }
      if (iteration > 0 && Math.abs(rate - prevRate) < epsMax * 10) {
        break;
      }
      double delta = Math.max(0.0001, Math.abs(rate * 0.0001));
      double derivNpv = (irrNpv(cf, rate + delta) - currentNpv) / delta;
      if (Math.abs(derivNpv) < epsMax) {
        break;
      }
      prevRate = rate;
      double newtonStep = currentNpv / derivNpv;
      double maxStep = Math.max(0.1, Math.abs(rate) * 0.5);
      rate -= Math.abs(newtonStep) > maxStep ? Js.sign(newtonStep) * maxStep : newtonStep;
      if (rate <= -1) {
        rate = -0.99999999;
      }
      if (rate > 1000) {
        rate = 1000;
      }
    }
    double npvAtRate = irrNpv(cf, rate);
    if (Math.abs(npvAtRate) < epsMax) {
      return rate;
    }
    double a = rate;
    double b = rate;
    if (npvAtRate > 0) {
      b = rate + 0.1;
      while (irrNpv(cf, b) > 0 && b < 1000) {
        b = b * 2 + 0.1;
      }
      if (b >= 1000) {
        return rate;
      }
    } else {
      a = Math.max(-0.99999999, rate - 0.1);
      while (irrNpv(cf, a) < 0 && a > -0.99999999) {
        a = Math.max(-0.99999999, a - 0.1);
      }
      if (a <= -0.99999999) {
        return rate;
      }
    }
    double c = Double.NaN;
    for (int i = 0; i < 1000; i++) {
      c = (a + b) / 2;
      double npvC = irrNpv(cf, c);
      if (Math.abs(npvC) < epsMax || Math.abs(b - a) < epsMax) {
        return c;
      }
      if (npvC * irrNpv(cf, a) < 0) {
        b = c;
      } else {
        a = c;
      }
    }
    return c;
  }

  /** Calculates the interest paid during a specific period of an investment. */
  public static Object ISPMT(Object... args) {
    double[] a = numbers(args, 4);
    return a == null ? Errors.VALUE : (Object) (a[3] * a[0] * (a[1] / a[2] - 1));
  }

  /** Returns the internal rate of return where positive and negative cash flows are financed at different rates. */
  public static Object MIRR(Object... args) {
    Object values = Common.parseNumberArray(Common.flatten(Js.arg(args, 0)));
    double[] a = numbers(Js.arg(args, 1), Js.arg(args, 2));
    if (a == null || values instanceof JsError) {
      return Errors.VALUE;
    }
    List<Object> list = Js.asList(values);
    double n = list.size();
    List<Object> payments = new ArrayList<>();
    List<Object> incomes = new ArrayList<>();
    for (Object v : list) {
      ((Double) v < 0 ? payments : incomes).add(v);
    }
    double num = -num(NPV(a[1], incomes)) * Js.pow(1 + a[1], n - 1);
    double den = num(NPV(a[0], payments)) * (1 + a[0]);
    return Js.pow(num / den, 1 / (n - 1)) - 1;
  }

  /** Returns the number of periods for an investment. */
  public static Object NPER(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3, 0.0),
        Js.arg(args, 4, 0.0));
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double pmt = a[1];
    if (rate == 0) {
      return -(a[2] + a[3]) / pmt;
    }
    double num = pmt * (1 + rate * a[4]) - a[3] * rate;
    double den = a[2] * rate + pmt * (1 + rate * a[4]);
    return StrictMath.log(num / den) / StrictMath.log(1 + rate);
  }

  /** Returns the net present value of an investment based on a series of periodic cash flows and a discount rate. */
  public static Object NPV(Object... args) {
    Object parsed = Common.parseNumberArray(Common.flatten(Common.arguments(args)));
    if (parsed instanceof JsError) {
      return parsed;
    }
    List<Object> list = Js.asList(parsed);
    double rate = (Double) list.get(0);
    double value = 0;
    for (int j = 1; j < list.size(); j++) {
      value += (Double) list.get(j) / Js.pow(1 + rate, j);
    }
    return value;
  }

  /** Returns the number of periods required by an investment to reach a specified value. */
  public static Object PDURATION(Object... args) {
    double[] a = numbers(args, 3);
    if (a == null) {
      return Errors.VALUE;
    }
    return a[0] <= 0 ? Errors.NUM
        : (Object) ((StrictMath.log(a[2]) - StrictMath.log(a[1])) / StrictMath.log(1 + a[0]));
  }

  /** Returns the periodic payment for an annuity. */
  public static Object PMT(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), orZero(Js.arg(args, 3)),
        orZero(Js.arg(args, 4)));
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double pv = a[2];
    double fv = a[3];
    if (rate == 0) {
      return -((pv + fv) / a[1]);
    }
    double term = Js.pow(1 + rate, a[1]);
    return -(a[4] == 1 ? ((fv * rate) / (term - 1) + (pv * rate) / (1 - 1 / term)) / (1 + rate)
        : (fv * rate) / (term - 1) + (pv * rate) / (1 - 1 / term));
  }

  /** Returns the payment on the principal for an investment for a given period. */
  public static Object PPMT(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 2), Js.arg(args, 3), orZero(Js.arg(args, 4)),
        orZero(Js.arg(args, 5)));
    if (a == null) {
      return Errors.VALUE;
    }
    return num(PMT(a[0], a[1], a[2], a[3], a[4])) - num(IPMT(a[0], Js.arg(args, 1), a[1], a[2], a[3], a[4]));
  }

  /** Returns the price per $100 face value of a discounted security. */
  public static Object PRICEDISC(Object... args) {
    Object d = discount(args);
    return d instanceof double[] v ? (Object) (v[1] - (v[0] * v[1] * v[3]) / v[2]) : d;
  }

  /** Returns the present value of an investment. */
  public static Object PV(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), orZero(Js.arg(args, 3)),
        orZero(Js.arg(args, 4)));
    if (a == null) {
      return Errors.VALUE;
    }
    double rate = a[0];
    double per = a[1];
    return rate == 0 ? -a[2] * per - a[3]
        : (((1 - Js.pow(1 + rate, per)) / rate) * a[2] * (1 + rate * a[4]) - a[3]) / Js.pow(1 + rate, per);
  }

  /** Returns the interest rate per period of an annuity. */
  public static Object RATE(Object... args) {
    double[] a = numbers(Js.arg(args, 0), Js.arg(args, 1), Js.arg(args, 2), Js.arg(args, 3, 0.0),
        Js.arg(args, 4, 0.0), Js.arg(args, 5, 0.1));
    if (a == null) {
      return Errors.VALUE;
    }
    double nper = a[0];
    double pmt = a[1];
    double pv = a[2];
    double fv = a[3];
    double type = Js.truthy(a[4]) ? 1 : 0;
    final double epsMax = 1e-10;
    double rate = a[5];
    for (int i = 0; i < 100; i++) {
      if (rate <= -1) {
        return Errors.NUM;
      }
      boolean small = Math.abs(rate) < epsMax;
      double f = Js.pow(1 + rate, nper);
      double y = small ? pv * (1 + nper * rate) + pmt * (1 + rate * type) * nper + fv
          : pv * f + pmt * (1 / rate + type) * (f - 1) + fv;
      if (Math.abs(y) < epsMax) {
        return rate;
      }
      double df = nper * Js.pow(1 + rate, nper - 1);
      double dy = small ? pv * nper + pmt * type * nper
          : pv * df + pmt * (1 / rate + type) * df + pmt * (-1 / (rate * rate)) * (f - 1);
      rate -= y / dy;
    }
    return rate;
  }

  /** Returns an equivalent interest rate for the growth of an investment. */
  public static Object RRI(Object... args) {
    double[] a = numbers(args, 3);
    if (a == null) {
      return Errors.VALUE;
    }
    return a[0] == 0 || a[1] == 0 ? Errors.NUM : (Object) (Js.pow(a[2] / a[1], 1 / a[0]) - 1);
  }

  /** Returns the straight-line depreciation of an asset for one period. */
  public static Object SLN(Object... args) {
    double[] a = numbers(args, 3);
    if (a == null) {
      return Errors.VALUE;
    }
    return a[2] == 0 ? Errors.NUM : (Object) ((a[0] - a[1]) / a[2]);
  }

  /** Returns the sum-of-years' digits depreciation of an asset for a specified period. */
  public static Object SYD(Object... args) {
    double[] a = numbers(args, 4);
    if (a == null) {
      return Errors.VALUE;
    }
    double life = a[2];
    if (life == 0 || a[3] < 1 || a[3] > life) {
      return Errors.NUM;
    }
    double per = Js.parseInt(a[3], 10.0);
    return ((a[0] - a[1]) * (life - per + 1) * 2) / (life * (life + 1));
  }

  /** The checks the Treasury bill functions share: an error, or {third argument, DAYS360 of the period}. */
  private static Object tbill(Object[] args) {
    Object settlement = Common.parseDate(Js.arg(args, 0));
    Object maturity = Common.parseDate(Js.arg(args, 1));
    double[] a = numbers(Js.arg(args, 2));
    if (a == null || Common.anyIsError(settlement, maturity)) {
      return Errors.VALUE;
    }
    if (a[0] <= 0 || Js.gt(settlement, maturity) || num(maturity) - num(settlement) > 31536000000.0) {
      return Errors.NUM;
    }
    return new double[] {a[0], num(DateTime.DAYS360(settlement, maturity, false))};
  }

  /** Returns the bond-equivalent yield for a Treasury bill. */
  public static Object TBILLEQ(Object... args) {
    Object t = tbill(args);
    return t instanceof double[] v ? (Object) ((365 * v[0]) / (360 - v[0] * v[1])) : t;
  }

  /** Returns the price per $100 face value for a Treasury bill. */
  public static Object TBILLPRICE(Object... args) {
    Object t = tbill(args);
    return t instanceof double[] v ? (Object) (100 * (1 - (v[0] * v[1]) / 360)) : t;
  }

  /** Returns the yield for a Treasury bill. */
  public static Object TBILLYIELD(Object... args) {
    Object t = tbill(args);
    return t instanceof double[] v ? (Object) (((100 - v[0]) * 360) / (v[0] * v[1])) : t;
  }

  private static double days(List<Object> dates, int i) {
    return num(DateTime.DAYS(Js.get(dates, (double) i), Js.get(dates, 0.0))) / 365;
  }

  /** Returns the internal rate of return for a schedule of cash flows that is not necessarily periodic. */
  public static Object XIRR(Object... args) {
    Object valuesArg = Common.parseNumberArray(Common.flatten(Js.arg(args, 0)));
    Object datesArg = Common.parseDateArray(Common.flatten(Js.arg(args, 1)));
    Object guess = Common.parseNumber(Js.arg(args, 2));
    if (Common.anyIsError(valuesArg, datesArg, guess)) {
      return Errors.VALUE;
    }
    List<Object> values = Js.asList(valuesArg);
    List<Object> dates = Js.asList(datesArg);
    boolean positive = false;
    boolean negative = false;
    for (Object v : values) {
      positive |= (Double) v > 0;
      negative |= (Double) v < 0;
    }
    if (!positive || !negative) {
      return Errors.NUM;
    }
    double resultRate = Js.truthy(guess) ? (Double) guess : 0.1;
    boolean contLoop;
    do {
      double r = resultRate + 1;
      double resultValue = (Double) values.get(0);
      double deriv = 0;
      for (int i = 1; i < values.size(); i++) {
        resultValue += (Double) values.get(i) / Js.pow(r, days(dates, i));
      }
      for (int i = 1; i < values.size(); i++) {
        double frac = days(dates, i);
        deriv -= (frac * (Double) values.get(i)) / Js.pow(r, frac + 1);
      }
      double newRate = resultRate - resultValue / deriv;
      double epsRate = Math.abs(newRate - resultRate);
      resultRate = newRate;
      contLoop = epsRate > 1e-10 && Math.abs(resultValue) > 1e-10;
    } while (contLoop);
    return resultRate;
  }

  /** Returns the net present value for a schedule of cash flows that is not necessarily periodic. */
  public static Object XNPV(Object... args) {
    Object rate = Common.parseNumber(Js.arg(args, 0));
    Object values = Common.parseNumberArray(Common.flatten(Js.arg(args, 1)));
    Object dates = Common.parseDateArray(Common.flatten(Js.arg(args, 2)));
    if (Common.anyIsError(rate, values, dates)) {
      return Errors.VALUE;
    }
    List<Object> list = Js.asList(values);
    double result = 0;
    for (int i = 0; i < list.size(); i++) {
      result += (Double) list.get(i) / Js.pow(1 + (Double) rate, days(Js.asList(dates), i));
    }
    return result;
  }

  static void register(Map<String, Fn> ops) {
    ops.put("ACCRINT", Financial::ACCRINT);
    ops.put("COUPDAYS", Financial::COUPDAYS);
    ops.put("CUMIPMT", Financial::CUMIPMT);
    ops.put("CUMPRINC", Financial::CUMPRINC);
    ops.put("DB", Financial::DB);
    ops.put("DDB", Financial::DDB);
    ops.put("DISC", Financial::DISC);
    ops.put("DOLLARDE", Financial::DOLLARDE);
    ops.put("DOLLARFR", Financial::DOLLARFR);
    ops.put("EFFECT", Financial::EFFECT);
    ops.put("FV", Financial::FV);
    ops.put("FVSCHEDULE", Financial::FVSCHEDULE);
    ops.put("IPMT", Financial::IPMT);
    ops.put("IRR", Financial::IRR);
    ops.put("ISPMT", Financial::ISPMT);
    ops.put("MIRR", Financial::MIRR);
    ops.put("NOMINAL", Financial::NOMINAL);
    ops.put("NPER", Financial::NPER);
    ops.put("NPV", Financial::NPV);
    ops.put("PDURATION", Financial::PDURATION);
    ops.put("PMT", Financial::PMT);
    ops.put("PPMT", Financial::PPMT);
    ops.put("PRICEDISC", Financial::PRICEDISC);
    ops.put("PV", Financial::PV);
    ops.put("RATE", Financial::RATE);
    ops.put("RRI", Financial::RRI);
    ops.put("SLN", Financial::SLN);
    ops.put("SYD", Financial::SYD);
    ops.put("TBILLEQ", Financial::TBILLEQ);
    ops.put("TBILLPRICE", Financial::TBILLPRICE);
    ops.put("TBILLYIELD", Financial::TBILLYIELD);
    ops.put("XIRR", Financial::XIRR);
    ops.put("XNPV", Financial::XNPV);
  }
}
