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
package io.github.formulajs.cli;

import io.github.formulajs.Fn;
import io.github.formulajs.Formulajs;
import io.github.formulajs.js.JsException;
import io.github.formulajs.js.Json;
import io.github.formulajs.utils.DateUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * The {@code formulajs} command: one JSON request per line on standard input, one JSON response per line on standard
 * output. A request is {@code {"op":NAME,"args":[VALUE,...],"serial":BOOLEAN}}; the response is
 * {@code {"result":VALUE}} or {@code {"error":{"name":NAME,"message":MESSAGE}}}. The exit status is 0 when every
 * request succeeded, 1 when one failed, and 2 (with the usage text on standard error) for any argument.
 */
public final class Main {
  static final String USAGE = "usage: formulajs < requests.jsonl\n"
      + "\n"
      + "Reads one JSON request per line on standard input and writes one JSON response per line.\n"
      + "Request: {\"op\":NAME,\"args\":[VALUE,...],\"serial\":BOOLEAN}\n"
      + "Response: {\"result\":VALUE} or {\"error\":{\"name\":NAME,\"message\":MESSAGE}}\n";

  /**
   * Stack of the thread that runs the command. V8's {@code JSON.parse} does not recurse, so the reference reads request
   * lines nested far deeper than a default Java stack holds; the recursive parser here gets the room to do the same.
   */
  private static final long COMMAND_STACK_BYTES = 1L << 30;

  private Main() {}

  public static void main(String[] argv) throws IOException, InterruptedException {
    PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(System.err, false, StandardCharsets.UTF_8);
    int status = onCommandStack(() -> run(argv, System.in, out, err));
    out.flush();
    err.flush();
    System.exit(status);
  }

  /** Runs {@code command} on a thread with the command's stack and answers its exit status. */
  static int onCommandStack(Callable<Integer> command) throws IOException, InterruptedException {
    FutureTask<Integer> task = new FutureTask<>(command);
    Thread thread = new Thread(null, task, "formulajs", COMMAND_STACK_BYTES);
    thread.start();
    try {
      return task.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException io) {
        throw io;
      }
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    }
  }

  /** Runs the command on the given streams and answers its exit status. */
  public static int run(String[] argv, InputStream in, PrintStream out, PrintStream err) throws IOException {
    if (argv.length > 0) {
      err.print(USAGE);
      return 2;
    }
    String input = decodeUtf8(in.readAllBytes());
    StringBuilder output = new StringBuilder();
    boolean failed = false;
    for (String line : input.split("\n", -1)) {
      if (line.isEmpty()) {
        continue;
      }
      String response;
      try {
        response = Json.stringify(handle(Json.parse(line)));
      } catch (JsException e) {
        failed = true;
        response = Json.stringify(errorResponse(e.name(), e.getMessage()));
      } catch (RuntimeException e) {
        failed = true;
        response = Json.stringify(errorResponse("InternalError", String.valueOf(e)));
      } catch (StackOverflowError e) {
        // The reference answers a line whose parsing, decoding or serialisation overflows V8's stack with V8's
        // RangeError, as it answers every other error, and goes on with the next line.
        failed = true;
        response = Json.stringify(errorResponse("RangeError", "Maximum call stack size exceeded"));
      }
      output.append(response).append('\n');
    }
    out.print(output);
    return failed ? 1 : 0;
  }

  /**
   * UTF-8 decoding as Node.js does it (the WHATWG decoder): every maximal ill-formed subsequence, an encoded surrogate
   * included, becomes one U+FFFD.
   */
  static String decodeUtf8(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length);
    int needed = 0;
    int seen = 0;
    int codePoint = 0;
    int lower = 0x80;
    int upper = 0xBF;
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i] & 0xFF;
      if (needed == 0) {
        if (b < 0x80) {
          out.append((char) b);
        } else if (b >= 0xC2 && b <= 0xDF) {
          needed = 1;
          codePoint = b & 0x1F;
        } else if (b >= 0xE0 && b <= 0xEF) {
          lower = b == 0xE0 ? 0xA0 : 0x80;
          upper = b == 0xED ? 0x9F : 0xBF;
          needed = 2;
          codePoint = b & 0xF;
        } else if (b >= 0xF0 && b <= 0xF4) {
          lower = b == 0xF0 ? 0x90 : 0x80;
          upper = b == 0xF4 ? 0x8F : 0xBF;
          needed = 3;
          codePoint = b & 0x7;
        } else {
          out.append('\uFFFD');
        }
        continue;
      }
      if (b < lower || b > upper) {
        needed = 0;
        seen = 0;
        lower = 0x80;
        upper = 0xBF;
        out.append('\uFFFD');
        i--;
        continue;
      }
      lower = 0x80;
      upper = 0xBF;
      codePoint = (codePoint << 6) | (b & 0x3F);
      if (++seen == needed) {
        out.appendCodePoint(codePoint);
        needed = 0;
        seen = 0;
      }
    }
    if (needed != 0) {
      out.append('\uFFFD');
    }
    return out.toString();
  }

  static JsException requestError(String message) {
    return new JsException("RequestError", message);
  }

  /** Answers one request (a parsed JSON line). */
  public static Map<String, Object> handle(Object request) {
    if (!(request instanceof Map<?, ?> map)) {
      throw requestError("a request must be a JSON object");
    }
    if (!(map.get("op") instanceof String op)) {
      throw requestError("op must be a string");
    }
    Fn operation = Formulajs.operation(op);
    if (operation == null) {
      throw requestError("unknown op: " + Json.stringify(op));
    }
    Object args = map.get("args");
    if (map.containsKey("args") && !(args instanceof List)) {
      throw requestError("args must be an array");
    }
    Object serial = map.get("serial");
    if (map.containsKey("serial") && !(serial instanceof Boolean)) {
      throw requestError("serial must be a boolean");
    }
    List<Object> decoded = new ArrayList<>();
    if (args instanceof List<?> list) {
      for (Object item : list) {
        decoded.add(Codec.decode(item));
      }
    }
    if (Boolean.TRUE.equals(serial)) {
      DateUtils.useSerial();
    }
    try {
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("result", Codec.encode(operation.apply(decoded.toArray())));
      return response;
    } catch (StackOverflowError e) {
      throw new JsException("RangeError", "Maximum call stack size exceeded");
    } finally {
      DateUtils.useDate();
    }
  }

  private static Map<String, Object> errorResponse(String name, String message) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("name", name);
    error.put("message", message);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("error", error);
    return response;
  }
}
