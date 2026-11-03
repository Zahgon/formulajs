# Formula.js

[![Tests](https://github.com/formulajs/formulajs/actions/workflows/unit-tests.yaml/badge.svg)](https://github.com/formulajs/formulajs/actions/workflows/unit-tests.yaml)

[Official website and documentation is here](https://formulajs.info)

Formula.js implements most Microsoft Excel formula functions. This is the Java implementation: a library and a
`formulajs` command that answer exactly as the JavaScript library (version 4.6.1) does, including its JavaScript number
formatting, date parsing, coercions and error values. It needs Java 21 and nothing else.

## Build

```sh
mvn -q -DskipTests package   # target/formulajs.jar
mvn test                     # the upstream test suite (ported to Java) and the runtime and command tests
```

## Use it

### As a library

Every Excel function is a static method of its category class (`MathTrig`, `Statistical`, `Text`, `DateTime`,
`Engineering`, `Financial`, `LookupReference`, `Logical`, `Information`, `Database`) in `io.github.formulajs`, named as
in Excel with `.` replaced by `_` (`NORM_S_DIST`, `CEILING_MATH`). Arguments and results are JavaScript values in Java
form: `Double` for every number, `String`, `Boolean`, `null`, `Js.UNDEFINED`, `List<Object>` for arrays, `JsDate` for
dates and the error values of `io.github.formulajs.utils.Errors` (`#N/A`, `#VALUE!`, ...).

```java
import io.github.formulajs.Formulajs;
import io.github.formulajs.MathTrig;
import io.github.formulajs.js.Js;

MathTrig.SUM(Js.list(1.0, 2.0, 3.0));                    // 6.0
Formulajs.operation("NORM.S.DIST").apply(1.0, true);     // 0.8413447460685429
```

`Formulajs.operations()` lists every function by the name the JavaScript library exports it under, including the
compatibility names (`BETADIST`, `CEILINGMATH`, ...) and dotted names (`CHISQ.DIST.RT`, `utils.symbols.ADD`).

### Command Line Interface (CLI)

`bin/formulajs` (or `java -jar target/formulajs.jar`) reads one JSON request per line on standard input and writes one
JSON response per line:

```sh
$ printf '{"op":"SUM","args":[1,2,3]}\n{"op":"DATE","args":[2020,2,30],"serial":true}\n' | bin/formulajs
{"result":6}
{"result":43891}
```

A request is `{"op":NAME,"args":[VALUE,...],"serial":BOOLEAN}`; the response is `{"result":VALUE}`, or
`{"error":{"name":NAME,"message":MESSAGE}}` when the function throws or the request is malformed. VALUEs are JSON, with
`{"number":"NaN"}` (also `"Infinity"`, `"-Infinity"`, `"-0"`), `{"undefined":true}`, `{"error":"#N/A"}` and
`{"date":"2020-01-15"}` for the values JSON cannot write. `"serial":true` makes the date functions answer Excel serial
numbers. The exit status is 0 when every request succeeded, 1 otherwise, and 2 (with a usage text) for any argument.
`NOW`, `TODAY`, `RAND` and `RANDBETWEEN` are not available through the command.

## Differences between Excel functions and Formula.js

### Date

The functions `DATE, DATEVALUE, EDATE, EOMONTH` return a date instead of the serial Excel number, unless the serial
switch (`DateUtils.useSerial()`, or `"serial":true` in a request) is on. Dates are computed in UTC.

Copying composite formula directly from Excel will not work out of the box:

```
= DATE(2020,5,9) - DATE(2020,5,8) // Formula.js: 86400000 / Excel: 1
```

It is not recommended to use `DATEVALUE` to parse strings representing a date: Formula.js parses them as JavaScript's
`new Date('YOUR STRING')` does (V8's date parser), and so does this implementation.

## Historic

Original Formula.js project was developed and maintained by Ismael Chang Ghalimi, with support from Sutoiku and help from
the following contributors: Ilmari Karonen, Sébastien Loisel, Trevor Norris, Roönaän, Hannes Stiebitzhofer.

It was then forked and extended by the [handsontable/formula.js](https://github.com/handsontable/formula.js) mainly
contributed by [@budnix](https://github.com/budnix).

As of September 2023, the repo is officially detached from its Handsontable and Sutoiku origins.

[IMPLEMENTATION_STATS.md](IMPLEMENTATION_STATS.md) lists the Excel functions Formula.js implements.

## License

MIT, see [LICENSE](LICENSE) (it also covers the ported parts of jStat and of SheetJS's bessel).
