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

import io.github.formulajs.bessel.Bessel;
import io.github.formulajs.js.Js;
import io.github.formulajs.js.JsError;
import io.github.formulajs.js.JsException;
import io.github.formulajs.js.JsMath;
import io.github.formulajs.jstat.JStat;
import io.github.formulajs.utils.Common;
import io.github.formulajs.utils.Errors;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.IntBinaryOperator;

/** Engineering functions. */
public final class Engineering {
  private Engineering() {}

  /** {@code /^-?[digits]{1,max}$/.test(value)} with the digits of the radix, the minus sign only when signed. */
  private static boolean test(Object value, boolean signed, int radix, int max) {
    String s = Js.str(value);
    int start = signed && s.startsWith("-") ? 1 : 0;
    boolean ok = s.length() - start >= 1 && s.length() - start <= max;
    for (int i = start; ok && i < s.length(); i++) {
      ok = s.charAt(i) < 128 && Character.digit(s.charAt(i), radix) >= 0;
    }
    return ok;
  }

  /** The common tail of the base conversions: result padded with zeros to places, or the error places causes. */
  private static Object pad(String result, Object places) {
    if (places == Js.UNDEFINED) {
      return result;
    }
    if (Js.isNaN(places)) {
      return Errors.VALUE;
    }
    if (Js.lt(places, 0.0)) {
      return Errors.NUM;
    }
    double p = Math.floor(Js.toNumber(places));
    return p >= result.length() ? Js.add(Text.REPT("0", p - result.length()), result) : Errors.NUM;
  }

  /** {@code '1' + text.REPT('0', 9 - b.length) + b} with {@code b = (512 + decimal).toString(2)}. */
  private static Object negativeBinary(double decimal) {
    String b = Js.toRadixString(512 + decimal, 2);
    return Js.add(Js.add("1", Text.REPT("0", (double) (9 - b.length()))), b);
  }

  /** The receiver of {@code name.substring(...)}, or V8's TypeError when it is not a string. */
  private static String stringFor(Object value, String name) {
    if (value instanceof String s) {
      return s;
    }
    throw Js.isNullish(value)
        ? JsException.cannotRead(value, "substring")
        : JsException.notAFunction(name + ".substring");
  }

  /** {@code name.substring(name.length - 1)}. */
  private static String last(Object inumber, String name) {
    String s = stringFor(inumber, name);
    return s.substring(Math.max(s.length() - 1, 0));
  }

  /** The imaginary unit of inumber: its last character when it is 'i' or 'j', else 'i'. */
  private static String unitOf(Object inumber) {
    String unit = last(inumber, "inumber");
    return unit.equals("i") || unit.equals("j") ? unit : "i";
  }

  /** The unit of a result of two complex numbers: 'j' when either ends with 'j'. */
  private static String unitOf(Object inumber1, Object inumber2) {
    String unit1 = last(inumber1, "inumber1");
    String unit2 = last(inumber2, "inumber2");
    return unit1.equals("j") || unit2.equals("j") ? "j" : "i";
  }

  private static boolean nan(String s) {
    return Double.isNaN(Js.stringToNumber(s));
  }

  private static double num(Object v) {
    return Js.toNumber(v);
  }

  private static Object bessel(Object[] args, DoubleBinaryOperator fn) {
    Object x = Common.parseNumber(Js.arg(args, 0));
    Object n = Common.parseNumber(Js.arg(args, 1));
    return Common.anyIsError(x, n) ? Errors.VALUE : (Object) fn.applyAsDouble((Double) x, (Double) n);
  }

  /** Returns the modified Bessel function In(x). */
  public static Object BESSELI(Object... args) {
    return bessel(args, Bessel::besseli);
  }

  /** Returns the Bessel function Jn(x). */
  public static Object BESSELJ(Object... args) {
    return bessel(args, Bessel::besselj);
  }

  /** Returns the modified Bessel function Kn(x). */
  public static Object BESSELK(Object... args) {
    return bessel(args, Bessel::besselk);
  }

  /** Returns the Bessel function Yn(x). */
  public static Object BESSELY(Object... args) {
    return bessel(args, Bessel::bessely);
  }

  /** Converts a binary number to decimal. */
  public static Object BIN2DEC(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 2, 10)) {
      return Errors.NUM;
    }
    double result = Js.parseInt(number, 2.0);
    String stringified = Js.str(number);
    return stringified.length() == 10 && stringified.startsWith("1")
        ? Js.parseInt(stringified.substring(1), 2.0) - 512
        : result;
  }

  /** BIN2HEX and BIN2OCT: a negative (10-digit) number is offset plus its 9 low bits. */
  private static Object binTo(Object[] args, double offset, int radix) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 2, 10)) {
      return Errors.NUM;
    }
    String stringified = Js.str(number);
    if (stringified.length() == 10 && stringified.startsWith("1")) {
      return Js.toRadixString(offset + Js.parseInt(stringified.substring(1), 2.0), radix);
    }
    return pad(Js.toRadixString(Js.parseInt(number, 2.0), radix), Js.arg(args, 1));
  }

  /** Converts a binary number to hexadecimal. */
  public static Object BIN2HEX(Object... args) {
    return binTo(args, 1099511627264.0, 16);
  }

  /** Converts a binary number to octal. */
  public static Object BIN2OCT(Object... args) {
    return binTo(args, 1073741312.0, 8);
  }

  /** BITAND, BITOR and BITXOR: the checks, then the 32-bit operator of JavaScript. */
  private static Object bitwise(Object[] args, IntBinaryOperator op) {
    Object parsed1 = Common.parseNumber(Js.arg(args, 0));
    Object parsed2 = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(parsed1, parsed2)) {
      return Errors.VALUE;
    }
    double number1 = (Double) parsed1;
    double number2 = (Double) parsed2;
    if (number1 < 0 || number2 < 0 || Math.floor(number1) != number1 || Math.floor(number2) != number2
        || number1 > 281474976710655.0 || number2 > 281474976710655.0) {
      return Errors.NUM;
    }
    return (double) op.applyAsInt(Js.toInt32(number1), Js.toInt32(number2));
  }

  /** BITLSHIFT and BITRSHIFT: the checks, then JavaScript's 32-bit {@code <<} or {@code >>}. */
  private static Object shift(Object[] args, boolean left) {
    Object parsedNumber = Common.parseNumber(Js.arg(args, 0));
    Object parsedShift = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(parsedNumber, parsedShift)) {
      return Errors.VALUE;
    }
    double number = (Double) parsedNumber;
    double shiftAmount = (Double) parsedShift;
    if (number < 0 || Math.floor(number) != number || number > 281474976710655.0 || Math.abs(shiftAmount) > 53) {
      return Errors.NUM;
    }
    int count = (int) (Js.toUint32(shiftAmount >= 0 ? shiftAmount : -shiftAmount) & 31);
    int value = Js.toInt32(number);
    return (double) ((shiftAmount >= 0) == left ? value << count : value >> count);
  }

  /** Returns a 'Bitwise And' of two numbers. */
  public static Object BITAND(Object... args) {
    return bitwise(args, (a, b) -> a & b);
  }

  /** Returns a value number shifted left by shift_amount bits. */
  public static Object BITLSHIFT(Object... args) {
    return shift(args, true);
  }

  /** Returns a bitwise OR of 2 numbers. */
  public static Object BITOR(Object... args) {
    return bitwise(args, (a, b) -> a | b);
  }

  /** Returns a value number shifted right by shift_amount bits. */
  public static Object BITRSHIFT(Object... args) {
    return shift(args, false);
  }

  /** Returns a bitwise 'Exclusive Or' of two numbers. */
  public static Object BITXOR(Object... args) {
    return bitwise(args, (a, b) -> a ^ b);
  }

  /** Converts real and imaginary coefficients into a complex number. */
  public static Object COMPLEX(Object... args) {
    Object realNum = Common.parseNumber(Js.arg(args, 0));
    Object iNum = Common.parseNumber(Js.arg(args, 1));
    if (Common.anyIsError(realNum, iNum)) {
      return realNum;
    }
    Object suffix = Js.arg(args, 2, "i");
    if (!Js.strictEquals(suffix, "i") && !Js.strictEquals(suffix, "j")) {
      return Errors.VALUE;
    }
    double re = (Double) realNum;
    double im = (Double) iNum;
    if (re == 0 && im == 0) {
      return 0.0;
    } else if (re == 0) {
      return im == 1 ? suffix : Js.numberToString(im) + suffix;
    } else if (im == 0) {
      return Js.numberToString(re);
    }
    return Js.numberToString(re) + (im > 0 ? "+" : "") + (im == 1 ? suffix : Js.numberToString(im) + suffix);
  }

  /** A unit of CONVERT: its symbol, alternate symbols, quantity and conversion ratio (the columns CONVERT reads). */
  private record Unit(String symbol, List<String> alt, String quantity, double ratio) {}

  private static Unit u(String symbol, String alt, String quantity, double ratio) {
    return new Unit(symbol, alt.isEmpty() ? List.of() : List.of(alt.split(" ")), quantity, ratio);
  }

  // List of units supported by CONVERT and units defined by the International System of Units, in the original order
  private static final List<Unit> UNITS = List.of(
      u("?", "", "action", 1.05457168181818e-34), u("e", "", "electric_charge", 1.60217653141414e-19),
      u("Eh", "", "energy", 4.35974417757576e-18), u("a?", "", "length", 5.29177210818182e-11),
      u("m?", "", "mass", 9.10938261616162e-31), u("?/Eh", "", "time", 2.41888432650516e-17),
      u("admkn", "", "speed", 0.514773333), u("A", "", "electric_current", 1),
      u("A/m", "", "magnetic_field_intensity", 1), u("\u00c5", "ang", "length", 1e-10), u("ar", "", "area", 100),
      u("ua", "", "length", 1.49597870691667e-11), u("bar", "", "pressure", 100000), u("b", "", "area", 1e-28),
      u("Bq", "", "radioactivity", 1), u("bit", "b", "information", 1), u("BTU", "btu", "energy", 1055.05585262),
      u("byte", "", "information", 8), u("cd", "", "luminous_intensity", 1), u("cd/m?", "", "luminance", 1),
      u("C", "", "electric_charge", 1), u("ang3", "ang^3", "volume", 1e-30), u("ft3", "ft^3", "volume", 0.028316846592),
      u("in3", "in^3", "volume", 0.000016387064), u("ly3", "ly^3", "volume", 8.46786664623715e-47),
      u("m3", "m^3", "volume", 1), u("mi3", "mi^3", "volume", 4168181825.44058),
      u("Nmi3", "Nmi^3", "volume", 6352182208.0), u("Pica3", "Picapt3 Pica^3 Picapt^3", "volume", 7.58660370370369e-8),
      u("yd3", "yd^3", "volume", 0.764554857984), u("cup", "", "volume", 0.0002365882365),
      u("Da", "u", "mass", 1.66053886282828e-27), u("d", "day", "time", 86400),
      u("\u00b0", "", "angle", 0.0174532925199433), u("Rank", "", "temperature", 0.555555555555556),
      u("dyn", "dy", "force", 0.00001), u("eV", "ev", "energy", 1.60217656514141), u("ell", "", "length", 1.143),
      u("erg", "e", "energy", 1e-7), u("F", "", "electric_capacitance", 1), u("oz", "", "volume", 0.0000295735295625),
      u("ft", "", "length", 0.3048), u("flb", "", "energy", 1.3558179483314), u("Gal", "", "acceleration", 0.01),
      u("gal", "", "volume", 0.003785411784), u("G", "ga", "magnetic_flux_density", 1),
      u("grain", "", "mass", 0.0000647989), u("g", "", "mass", 0.001), u("Gy", "", "absorbed_dose", 1),
      u("GRT", "regton", "volume", 2.8316846592), u("ha", "", "area", 10000), u("H", "", "inductance", 1),
      u("Hz", "", "frequency", 1), u("HP", "h", "power", 745.69987158227), u("HPh", "hh hph", "energy", 2684519.538),
      u("h", "hr", "time", 3600), u("uk_gal", "", "volume", 0.00454609), u("lcwt", "uk_cwt hweight", "mass", 50.802345),
      u("uk_qt", "", "volume", 0.0011365225), u("brton", "uk_ton LTON", "mass", 1016.046909),
      u("in", "", "length", 0.0254), u("uk_acre", "", "area", 4046.8564224), u("cal", "", "energy", 4.1868),
      u("J", "", "energy", 1), u("kat", "", "catalytic_activity", 1), u("K", "kel", "temperature", 1),
      u("kg", "", "mass", 1), u("kn", "", "speed", 0.514444444444444), u("ly", "", "length", 9460730472580800.0),
      u("L", "l lt", "volume", 0.001), u("lm", "", "luminous_flux", 1), u("lx", "", "illuminance", 1),
      u("Mx", "", "magnetic_flux", 1e-18), u("MTON", "", "volume", 1.13267386368),
      u("m/h", "m/hr", "speed", 0.00027777777777778), u("m/s", "m/sec", "speed", 1), u("m?s??", "", "acceleration", 1),
      u("pc", "parsec", "length", 30856775814671900.0), u("m?/s", "", "kinematic_viscosity", 1),
      u("m", "", "length", 1), u("mph", "", "speed", 0.44704), u("mmHg", "", "pressure", 133.322),
      u("?", "", "angle", 0.000290888208665722), u("min", "mn", "time", 60), u("tspm", "", "volume", 0.000005),
      u("mol", "", "amount_of_substance", 1), u("Morgen", "", "area", 2500), u("?", "", "action", 1.05457168181818e-34),
      u("m?", "", "mass", 9.10938261616162e-31), u("c?", "", "speed", 299792458),
      u("?/(me?c??)", "", "time", 1.28808866778687e-21), u("M", "Nmi", "length", 1852), u("N", "", "force", 1),
      u("Oe ", "", "magnetic_field_intensity", 79.5774715459477), u("\u03a9", "", "electric_resistance", 1),
      u("ozm", "", "mass", 0.028349523125), u("Pa", "", "pressure", 1), u("Pa?s", "", "dynamic_viscosity", 1),
      u("PS", "", "power", 735.49875), u("ph", "", "illuminance", 0.0001), u("pica", "", "length", 0.00035277777777778),
      u("Pica", "Picapt", "length", 0.00423333333333333), u("P", "", "dynamic_viscosity", 0.1),
      u("pond", "", "force", 0.00980665), u("lbf", "", "force", 4.4482216152605), u("lbm", "", "mass", 0.45359237),
      u("qt", "", "volume", 0.000946352946), u("rad", "", "angle", 1), u("?", "", "angle", 0.00000484813681109536),
      u("s", "sec", "time", 1), u("cwt", "shweight", "mass", 45.359237), u("S", "", "electrical_conductance", 1),
      u("Sv", "", "equivalent_dose", 1), u("sg", "", "mass", 14.59390294), u("ang2", "ang^2", "area", 1e-20),
      u("ft2", "ft^2", "area", 0.09290304), u("in2", "in^2", "area", 0.00064516),
      u("ly2", "ly^2", "area", 8.95054210748189e31), u("m?", "", "area", 1), u("mi2", "mi^2", "area", 2589988.110336),
      u("Nmi2", "Nmi^2", "area", 3429904), u("Pica2", "Picapt2 Pica^2 Picapt^2", "area", 0.00001792111111111),
      u("yd2", "yd^2", "area", 0.83612736), u("mi", "", "length", 1609.344), u("sr", "", "solid_angle", 1),
      u("sb", "", "luminance", 0.0001), u("St", "", "kinematic_viscosity", 0.0001), u("stone", "", "mass", 6.35029318),
      u("tbs", "", "volume", 0.0000147868), u("tsp", "", "volume", 0.00000492892),
      u("T", "", "magnetic_flux_density", 1), u("c", "", "energy", 4.184), u("ton", "", "mass", 907.18474),
      u("t", "", "mass", 1000), u("uk_pt", "", "volume", 0.00056826125), u("bushel", "", "volume", 0.03523907),
      u("barrel", "", "volume", 0.158987295), u("pt", "us_pt", "volume", 0.000473176473),
      u("survey_mi", "", "length", 1609.347219), u("us_acre", "", "area", 4046.87261), u("V", "", "voltage", 1),
      u("W", "", "power", 1), u("Wh", "wh", "energy", 3600), u("Wb", "", "magnetic_flux", 1),
      u("yd", "", "length", 0.9144), u("yr", "", "time", 31557600));

  // Binary prefixes: [Name, Prefix power of 2 value, Previx value, Abbreviation, Derived from], by abbreviation
  private static final Map<String, Double> BINARY_PREFIXES = Map.of("Yi", 1208925819614629174706176.0,
      "Zi", 1180591620717411303424.0, "Ei", 1152921504606846976.0, "Pi", 1125899906842624.0, "Ti", 1099511627776.0,
      "Gi", 1073741824.0, "Mi", 1048576.0, "ki", 1024.0);

  // Unit prefixes: [Name, Multiplier, Abbreviation], by abbreviation
  private static final Map<String, Double> UNIT_PREFIXES = Map.ofEntries(Map.entry("Y", 1e24), Map.entry("Z", 1e21),
      Map.entry("E", 1e18), Map.entry("P", 1e15), Map.entry("T", 1e12), Map.entry("G", 1e9), Map.entry("M", 1e6),
      Map.entry("k", 1e3), Map.entry("h", 1e2), Map.entry("e", 1e1), Map.entry("d", 1e-1), Map.entry("c", 1e-2),
      Map.entry("m", 1e-3), Map.entry("u", 1e-6), Map.entry("n", 1e-9), Map.entry("p", 1e-12), Map.entry("f", 1e-15),
      Map.entry("a", 1e-18), Map.entry("z", 1e-21), Map.entry("y", 1e-24));

  /** The last unit whose symbol or one of whose alternate symbols is (===) the given value, or null. */
  private static Unit lookup(Object symbol) {
    Unit found = null;
    for (Unit unit : UNITS) {
      if (Js.strictEquals(unit.symbol(), symbol) || Js.indexOf(unit.alt(), symbol) >= 0) {
        found = unit;
      }
    }
    return found;
  }

  /** {multiplier, unit} for a unit with a binary or decimal prefix: its multiplier and the unit without it. */
  private static Object[] prefixed(Object unitName, String name) {
    String unit = stringFor(unitName, name);
    Double binaryPrefix = BINARY_PREFIXES.get(unit.substring(0, Math.min(2, unit.length())));
    Double unitPrefix = UNIT_PREFIXES.get(unit.substring(0, Math.min(1, unit.length())));
    // Handle dekao unit prefix (only unit prefix with two characters); binary prefixes first ('Yi' before 'Y')
    boolean dekao = unit.startsWith("da");
    if (binaryPrefix != null) {
      return new Object[] {binaryPrefix, unit.substring(2)};
    }
    return dekao || unitPrefix != null ? new Object[] {dekao ? 1e1 : unitPrefix, unit.substring(dekao ? 2 : 1)}
        : new Object[] {1.0, unit};
  }

  /** Converts a number from one measurement system to another. */
  public static Object CONVERT(Object... args) {
    Object number = Common.parseNumber(Js.arg(args, 0));
    if (number instanceof JsError) {
      return number;
    }
    Unit from = lookup(Js.arg(args, 1));
    Unit to = lookup(Js.arg(args, 2));
    Object[] fromPrefix = from == null ? prefixed(Js.arg(args, 1), "from_unit") : new Object[] {1.0, null};
    from = from == null ? lookup(fromPrefix[1]) : from;
    Object[] toPrefix = to == null ? prefixed(Js.arg(args, 2), "to_unit") : new Object[] {1.0, null};
    to = to == null ? lookup(toPrefix[1]) : to;
    if (from == null || to == null || !from.quantity().equals(to.quantity())) {
      return Errors.NA;
    }
    return ((Double) number * from.ratio() * (Double) fromPrefix[0]) / (to.ratio() * (Double) toPrefix[0]);
  }

  /** DEC2BIN, DEC2HEX and DEC2OCT: numbers of up to digits digits in [-limit, limit), two's complement below 0. */
  private static Object decTo(Object[] args, int digits, double limit, int radix) {
    Object parsed = Common.parseNumber(Js.arg(args, 0));
    if (parsed instanceof JsError) {
      return parsed;
    }
    double number = (Double) parsed;
    if (!test(parsed, true, 10, digits) || number < -limit || number > limit - 1) {
      return Errors.NUM;
    }
    if (number < 0) {
      return radix == 2 ? negativeBinary(number) : Js.toRadixString(2 * limit + number, radix);
    }
    return pad(Js.toRadixString(Js.parseInt(parsed, 10.0), radix), Js.arg(args, 1));
  }

  /** Converts a decimal number to binary. */
  public static Object DEC2BIN(Object... args) {
    return decTo(args, 3, 512, 2);
  }

  /** Converts a decimal number to hexadecimal. */
  public static Object DEC2HEX(Object... args) {
    return decTo(args, 12, 549755813888.0, 16);
  }

  /** Converts a decimal number to octal. */
  public static Object DEC2OCT(Object... args) {
    return decTo(args, 9, 536870912, 8);
  }

  /** Tests whether two values are equal. */
  public static Object DELTA(Object... args) {
    Object number2 = Js.arg(args, 1, 0.0);
    Object number1 = Common.parseNumber(Js.arg(args, 0));
    number2 = Common.parseNumber(number2);
    if (Common.anyIsError(number1, number2)) {
      return Errors.VALUE;
    }
    return (Double) number1 == ((Double) number2).doubleValue() ? 1.0 : 0.0;
  }

  /** Returns the error function. */
  public static Object ERF(Object... args) {
    Object lowerLimit = Common.parseNumber(Js.arg(args, 0));
    Object upperLimit = Common.parseNumber(Js.arg(args, 1, 0.0));
    return Common.anyIsError(lowerLimit, upperLimit) ? Errors.VALUE : (Object) JStat.erf(lowerLimit);
  }

  /** Returns the complementary error function. */
  public static Object ERFC(Object... args) {
    Object x = Js.arg(args, 0);
    return Js.isNaN(x) ? Errors.VALUE : (Object) JStat.erfc(x);
  }

  /** Tests whether a number is greater than a threshold value. */
  public static Object GESTEP(Object... args) {
    Object step = Js.truthy(Js.arg(args, 1)) ? Js.arg(args, 1) : 0.0;
    Object number = Common.parseNumber(Js.arg(args, 0));
    if (Common.anyIsError(step, number)) {
      return number;
    }
    return Js.ge(number, step) ? 1.0 : 0.0;
  }

  /** HEX2BIN and OCT2BIN once the number is valid: negative when it is a 10-digit negative number. */
  private static Object toBinary(Object number, Object places, double radix, boolean negative, double offset) {
    double decimal = negative ? Js.parseInt(number, radix) - offset : Js.parseInt(number, radix);
    if (decimal < -512 || decimal > 511) {
      return Errors.NUM;
    }
    return negative ? negativeBinary(decimal) : pad(Js.toRadixString(decimal, 2), places);
  }

  /** Converts a hexadecimal number to binary. */
  public static Object HEX2BIN(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 16, 10)) {
      return Errors.NUM;
    }
    boolean negative =
        number instanceof String s && s.length() == 10 && Js.toLowerCase(s.substring(0, 1)).equals("f");
    return toBinary(number, Js.arg(args, 1), 16, negative, 1099511627776.0);
  }

  /** Converts a hexadecimal number to decimal. */
  public static Object HEX2DEC(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 16, 10)) {
      return Errors.NUM;
    }
    double decimal = Js.parseInt(number, 16.0);
    return decimal >= 549755813888.0 ? decimal - 1099511627776.0 : decimal;
  }

  /** Converts a hexadecimal number to octal. */
  public static Object HEX2OCT(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 16, 10)) {
      return Errors.NUM;
    }
    double decimal = Js.parseInt(number, 16.0);
    if (decimal > 536870911 && decimal < 1098974756864.0) {
      return Errors.NUM;
    }
    return decimal >= 1098974756864.0 ? Js.toRadixString(decimal - 1098437885952.0, 8)
        : pad(Js.toRadixString(decimal, 8), Js.arg(args, 1));
  }

  /** IMREAL and IMAGINARY of inumber, or null when either is an error. */
  private static Object[] parts(Object inumber) {
    Object x = IMREAL(inumber);
    Object y = IMAGINARY(inumber);
    return Common.anyIsError(x, y) ? null : new Object[] {x, y};
  }

  private interface Complex {
    Object apply(double x, double y, String unit);
  }

  /** A function of one complex number: #VALUE! when it is invalid, else fn of its coefficients and unit. */
  private static Object unary(Object inumber, Complex fn) {
    Object[] p = parts(inumber);
    if (p == null) {
      return Errors.VALUE;
    }
    String unit = unitOf(inumber);
    return fn.apply(num(p[0]), num(p[1]), unit);
  }

  /** IMCOT, IMCSC, IMCSCH, IMSEC, IMSECH and IMTAN: the checks, then IMDIV(top(inumber), bottom(inumber)). */
  private static Object quotient(Object inumber, boolean logical, JsError error, Fn top, Fn bottom) {
    if (logical && inumber instanceof Boolean) {
      return Errors.VALUE;
    }
    return parts(inumber) == null ? error : IMDIV(top.apply(inumber), bottom.apply(inumber));
  }

  /** Returns the absolute value (modulus) of a complex number. */
  public static Object IMABS(Object... args) {
    Object[] p = parts(Js.arg(args, 0));
    return p == null ? Errors.VALUE : (Object) Math.sqrt(Js.pow(num(p[0]), 2) + Js.pow(num(p[1]), 2));
  }

  /** The answer of IMREAL and IMAGINARY before parsing (#VALUE! for undefined and booleans, 0 for 0), or null. */
  private static Object special(Object inumber) {
    if (inumber == Js.UNDEFINED || inumber instanceof Boolean) {
      return Errors.VALUE;
    }
    return Js.strictEquals(inumber, 0.0) || Js.strictEquals(inumber, "0") ? 0.0 : null;
  }

  /** The sign between the coefficients: the first '+' (else '-') that is not the first character, or -1. */
  private static int sign(String s) {
    int plus = s.indexOf('+') == 0 ? s.indexOf('+', 1) : s.indexOf('+');
    int minus = s.indexOf('-') == 0 ? s.indexOf('-', 1) : s.indexOf('-');
    return plus >= 0 ? plus : minus;
  }

  /** Returns the imaginary coefficient of a complex number. */
  public static Object IMAGINARY(Object... args) {
    Object inumber = Js.arg(args, 0);
    Object special = special(inumber);
    if (special != null || Js.strictEquals(inumber, "i") || Js.strictEquals(inumber, "j")) {
      return special != null ? special : (Object) 1.0;
    }
    String s = Js.replaceFirst(Js.replaceFirst(Js.str(inumber), "+i", "+1i"), "-i", "-1i");
    s = Js.replaceFirst(Js.replaceFirst(s, "+j", "+1j"), "-j", "-1j");
    int at = sign(s);
    int end = s.length() - 1;
    boolean unit = s.endsWith("i") || s.endsWith("j");
    if (at >= 0) {
      if (!unit || nan(s.substring(0, at)) || nan(s.substring(at + 1, end))) {
        return Errors.NUM;
      }
      double coefficient = Js.stringToNumber(s.substring(at + 1, end));
      return s.charAt(at) == '+' ? coefficient : -coefficient;
    }
    return unit ? (nan(s.substring(0, end)) ? Errors.NUM : s.substring(0, end)) : nan(s) ? Errors.NUM : (Object) 0.0;
  }

  /** Returns the argument theta, an angle expressed in radians. */
  public static Object IMARGUMENT(Object... args) {
    Object[] p = parts(Js.arg(args, 0));
    if (p == null) {
      return Errors.VALUE;
    }
    Object x = p[0];
    Object y = p[1];
    boolean x0 = Js.strictEquals(x, 0.0);
    boolean y0 = Js.strictEquals(y, 0.0);
    if (x0 && y0) {
      return Errors.DIV0;
    }
    if (x0 && Js.gt(y, 0.0) || x0 && Js.lt(y, 0.0)) {
      return Js.gt(y, 0.0) ? Math.PI / 2 : -Math.PI / 2;
    }
    if (y0 && Js.gt(x, 0.0) || y0 && Js.lt(x, 0.0)) {
      return Js.gt(x, 0.0) ? 0.0 : -Math.PI;
    }
    double atan = JsMath.atan(num(y) / num(x));
    return Js.gt(x, 0.0) ? atan : Js.lt(x, 0.0) && Js.ge(y, 0.0) ? atan + Math.PI : atan - Math.PI;
  }

  /** Returns the complex conjugate of a complex number. */
  public static Object IMCONJUGATE(Object... args) {
    Object inumber = Js.arg(args, 0);
    Object[] p = parts(inumber);
    if (p == null) {
      return Errors.VALUE;
    }
    String unit = unitOf(inumber);
    return !Js.strictEquals(p[1], 0.0) ? COMPLEX(p[0], -num(p[1]), unit) : inumber;
  }

  /** Returns the cosine of a complex number. */
  public static Object IMCOS(Object... args) {
    return unary(Js.arg(args, 0), (x, y, unit) -> COMPLEX((StrictMath.cos(x) * (JsMath.exp(y) + JsMath.exp(-y))) / 2,
        (-StrictMath.sin(x) * (JsMath.exp(y) - JsMath.exp(-y))) / 2, unit));
  }

  /** Returns the hyperbolic cosine of a complex number. */
  public static Object IMCOSH(Object... args) {
    return unary(Js.arg(args, 0), (x, y, unit) -> COMPLEX((StrictMath.cos(y) * (JsMath.exp(x) + JsMath.exp(-x))) / 2,
        (StrictMath.sin(y) * (JsMath.exp(x) - JsMath.exp(-x))) / 2, unit));
  }

  /** Returns the cotangent of a complex number. */
  public static Object IMCOT(Object... args) {
    return quotient(Js.arg(args, 0), false, Errors.VALUE, Engineering::IMCOS, Engineering::IMSIN);
  }

  /** Returns the quotient of two complex numbers. */
  public static Object IMDIV(Object... args) {
    Object[] p = parts(Js.arg(args, 0));
    Object[] q = parts(Js.arg(args, 1));
    if (p == null || q == null) {
      return Errors.VALUE;
    }
    String unit = unitOf(Js.arg(args, 0), Js.arg(args, 1));
    if (Js.strictEquals(q[0], 0.0) && Js.strictEquals(q[1], 0.0)) {
      return Errors.NUM;
    }
    double a = num(p[0]);
    double b = num(p[1]);
    double c = num(q[0]);
    double d = num(q[1]);
    double den = c * c + d * d;
    return COMPLEX((a * c + b * d) / den, (b * c - a * d) / den, unit);
  }

  /** Returns the exponential of a complex number. */
  public static Object IMEXP(Object... args) {
    return unary(Js.arg(args, 0), (x, y, unit) -> {
      double e = JsMath.exp(x);
      return COMPLEX(e * StrictMath.cos(y), e * StrictMath.sin(y), unit);
    });
  }

  /** IMLN, IMLOG10 and IMLOG2: the natural logarithm divided by the logarithm of the base (1 for IMLN). */
  private static Object logarithm(Object inumber, double divisor) {
    return unary(inumber, (x, y, unit) ->
        COMPLEX(StrictMath.log(Math.sqrt(x * x + y * y)) / divisor, JsMath.atan(y / x) / divisor, unit));
  }

  /** Returns the natural logarithm of a complex number. */
  public static Object IMLN(Object... args) {
    return logarithm(Js.arg(args, 0), 1);
  }

  /** Returns the base-10 logarithm of a complex number. */
  public static Object IMLOG10(Object... args) {
    return logarithm(Js.arg(args, 0), StrictMath.log(10));
  }

  /** Returns the base-2 logarithm of a complex number. */
  public static Object IMLOG2(Object... args) {
    return logarithm(Js.arg(args, 0), StrictMath.log(2));
  }

  /** Returns a complex number raised to an integer power. */
  public static Object IMPOWER(Object... args) {
    Object inumber = Js.arg(args, 0);
    Object number = Common.parseNumber(Js.arg(args, 1));
    return number instanceof JsError ? Errors.VALUE : unary(inumber, (x, y, unit) -> {
      double n = (Double) number;
      double p = Js.pow(num(IMABS(inumber)), n);
      double t = num(IMARGUMENT(inumber));
      return COMPLEX(p * StrictMath.cos(n * t), p * StrictMath.sin(n * t), unit);
    });
  }

  /** Returns the product of complex numbers. */
  public static Object IMPRODUCT(Object... args) {
    Object result = Js.arg(args, 0);
    if (args.length == 0) {
      return Errors.VALUE;
    }
    for (int i = 1; i < args.length; i++) {
      Object[] p = parts(result);
      Object[] q = parts(args[i]);
      if (p == null || q == null) {
        return Errors.VALUE;
      }
      double a = num(p[0]);
      double b = num(p[1]);
      double c = num(q[0]);
      double d = num(q[1]);
      result = COMPLEX(a * c - b * d, a * d + b * c);
    }
    return result;
  }

  private static final List<String> REAL_ZERO =
      List.of("i", "+i", "1i", "+1i", "-i", "-1i", "j", "+j", "1j", "+1j", "-j", "-1j");

  /** Returns the real coefficient of a complex number. */
  public static Object IMREAL(Object... args) {
    Object inumber = Js.arg(args, 0);
    Object special = special(inumber);
    if (special != null || Js.indexOf(REAL_ZERO, inumber) >= 0) {
      return special != null ? special : (Object) 0.0;
    }
    String s = Js.str(inumber);
    int at = sign(s);
    int end = s.length() - 1;
    boolean unit = s.endsWith("i") || s.endsWith("j");
    if (at >= 0) {
      return !unit || nan(s.substring(0, at)) || nan(s.substring(at + 1, end))
          ? Errors.NUM
          : (Object) Js.stringToNumber(s.substring(0, at));
    }
    return unit ? (nan(s.substring(0, end)) ? Errors.NUM : (Object) 0.0) : nan(s) ? Errors.NUM : s;
  }

  /** Returns the secant of a complex number. */
  public static Object IMSEC(Object... args) {
    return quotient(Js.arg(args, 0), true, Errors.VALUE, z -> "1", Engineering::IMCOS);
  }

  /** Returns the hyperbolic secant of a complex number. */
  public static Object IMSECH(Object... args) {
    return quotient(Js.arg(args, 0), false, Errors.VALUE, z -> "1", Engineering::IMCOSH);
  }

  /** Returns the sine of a complex number. */
  public static Object IMSIN(Object... args) {
    return unary(Js.arg(args, 0), (x, y, unit) -> COMPLEX((StrictMath.sin(x) * (JsMath.exp(y) + JsMath.exp(-y))) / 2,
        (StrictMath.cos(x) * (JsMath.exp(y) - JsMath.exp(-y))) / 2, unit));
  }

  /** Returns the hyperbolic sine of a complex number. */
  public static Object IMSINH(Object... args) {
    return unary(Js.arg(args, 0), (x, y, unit) -> COMPLEX((StrictMath.cos(y) * (JsMath.exp(x) - JsMath.exp(-x))) / 2,
        (StrictMath.sin(y) * (JsMath.exp(x) + JsMath.exp(-x))) / 2, unit));
  }

  /** Returns the square root of a complex number. */
  public static Object IMSQRT(Object... args) {
    Object inumber = Js.arg(args, 0);
    return unary(inumber, (x, y, unit) -> {
      double s = Math.sqrt(num(IMABS(inumber)));
      double t = num(IMARGUMENT(inumber));
      return COMPLEX(s * StrictMath.cos(t / 2), s * StrictMath.sin(t / 2), unit);
    });
  }

  /** Returns the cosecant of a complex number. */
  public static Object IMCSC(Object... args) {
    return quotient(Js.arg(args, 0), true, Errors.NUM, z -> "1", Engineering::IMSIN);
  }

  /** Returns the hyperbolic cosecant of a complex number. */
  public static Object IMCSCH(Object... args) {
    return quotient(Js.arg(args, 0), true, Errors.NUM, z -> "1", Engineering::IMSINH);
  }

  /** Returns the difference between two complex numbers. */
  public static Object IMSUB(Object... args) {
    Object[] p = parts(Js.arg(args, 0));
    Object[] q = parts(Js.arg(args, 1));
    if (p == null || q == null) {
      return Errors.VALUE;
    }
    String unit = unitOf(Js.arg(args, 0), Js.arg(args, 1));
    return COMPLEX(num(p[0]) - num(q[0]), num(p[1]) - num(q[1]), unit);
  }

  /** Returns the sum of complex numbers. */
  public static Object IMSUM(Object... args) {
    if (args.length == 0) {
      return Errors.VALUE;
    }
    double numberSum = 0;
    double imaginarySum = 0;
    for (Object arg : Common.flatten(Common.arguments(args))) {
      numberSum += num(IMREAL(arg));
      imaginarySum += num(IMAGINARY(arg));
    }
    return COMPLEX(numberSum, imaginarySum, "i");
  }

  /** Returns the tangent of a complex number. */
  public static Object IMTAN(Object... args) {
    return quotient(Js.arg(args, 0), true, Errors.VALUE, Engineering::IMSIN, Engineering::IMCOS);
  }

  /** Converts an octal number to binary. */
  public static Object OCT2BIN(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 8, 10)) {
      return Errors.NUM;
    }
    boolean negative = number instanceof String s && s.length() == 10 && s.startsWith("7");
    return toBinary(number, Js.arg(args, 1), 8, negative, 1073741824);
  }

  /** Converts an octal number to decimal. */
  public static Object OCT2DEC(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 8, 10)) {
      return Errors.NUM;
    }
    double decimal = Js.parseInt(number, 8.0);
    return decimal >= 536870912 ? decimal - 1073741824 : decimal;
  }

  /** Converts an octal number to hexadecimal. */
  public static Object OCT2HEX(Object... args) {
    Object number = Js.arg(args, 0);
    if (!test(number, false, 8, 10)) {
      return Errors.NUM;
    }
    double decimal = Js.parseInt(number, 8.0);
    return decimal >= 536870912 ? "ff" + Js.toRadixString(decimal + 3221225472.0, 16)
        : pad(Js.toRadixString(decimal, 16), Js.arg(args, 1));
  }

  static void register(Map<String, Fn> ops) {
    ops.put("BESSELI", Engineering::BESSELI);
    ops.put("BESSELJ", Engineering::BESSELJ);
    ops.put("BESSELK", Engineering::BESSELK);
    ops.put("BESSELY", Engineering::BESSELY);
    ops.put("BIN2DEC", Engineering::BIN2DEC);
    ops.put("BIN2HEX", Engineering::BIN2HEX);
    ops.put("BIN2OCT", Engineering::BIN2OCT);
    ops.put("BITAND", Engineering::BITAND);
    ops.put("BITLSHIFT", Engineering::BITLSHIFT);
    ops.put("BITOR", Engineering::BITOR);
    ops.put("BITRSHIFT", Engineering::BITRSHIFT);
    ops.put("BITXOR", Engineering::BITXOR);
    ops.put("COMPLEX", Engineering::COMPLEX);
    ops.put("CONVERT", Engineering::CONVERT);
    ops.put("DEC2BIN", Engineering::DEC2BIN);
    ops.put("DEC2HEX", Engineering::DEC2HEX);
    ops.put("DEC2OCT", Engineering::DEC2OCT);
    ops.put("DELTA", Engineering::DELTA);
    ops.put("ERF", Engineering::ERF);
    ops.put("ERFC", Engineering::ERFC);
    ops.put("GESTEP", Engineering::GESTEP);
    ops.put("HEX2BIN", Engineering::HEX2BIN);
    ops.put("HEX2DEC", Engineering::HEX2DEC);
    ops.put("HEX2OCT", Engineering::HEX2OCT);
    ops.put("IMABS", Engineering::IMABS);
    ops.put("IMAGINARY", Engineering::IMAGINARY);
    ops.put("IMARGUMENT", Engineering::IMARGUMENT);
    ops.put("IMCONJUGATE", Engineering::IMCONJUGATE);
    ops.put("IMCOS", Engineering::IMCOS);
    ops.put("IMCOSH", Engineering::IMCOSH);
    ops.put("IMCOT", Engineering::IMCOT);
    ops.put("IMCSC", Engineering::IMCSC);
    ops.put("IMCSCH", Engineering::IMCSCH);
    ops.put("IMDIV", Engineering::IMDIV);
    ops.put("IMEXP", Engineering::IMEXP);
    ops.put("IMLN", Engineering::IMLN);
    ops.put("IMLOG10", Engineering::IMLOG10);
    ops.put("IMLOG2", Engineering::IMLOG2);
    ops.put("IMPOWER", Engineering::IMPOWER);
    ops.put("IMPRODUCT", Engineering::IMPRODUCT);
    ops.put("IMREAL", Engineering::IMREAL);
    ops.put("IMSEC", Engineering::IMSEC);
    ops.put("IMSECH", Engineering::IMSECH);
    ops.put("IMSIN", Engineering::IMSIN);
    ops.put("IMSINH", Engineering::IMSINH);
    ops.put("IMSQRT", Engineering::IMSQRT);
    ops.put("IMSUB", Engineering::IMSUB);
    ops.put("IMSUM", Engineering::IMSUM);
    ops.put("IMTAN", Engineering::IMTAN);
    ops.put("OCT2BIN", Engineering::OCT2BIN);
    ops.put("OCT2DEC", Engineering::OCT2DEC);
    ops.put("OCT2HEX", Engineering::OCT2HEX);
  }
}
