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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/** Array.prototype.sort with a comparator, as V8 implements it. */
final class ArraySort {
  private ArraySort() {}

  /**
   * Array.prototype.sort with a comparator, in place, as V8 does it: undefined elements go last uncompared, the
   * comparator's answer goes through ToNumber (NaN is 0), and the rest is sorted by V8's TimSort, galloping merges
   * included, so that inconsistent comparators (NaN elements) give V8's order.
   */
  static List<Object> sort(List<Object> list, BiFunction<Object, Object, Object> comparator) {
    List<Object> work = new ArrayList<>();
    for (Object v : list) {
      if (v != Js.UNDEFINED) {
        work.add(v);
      }
    }
    int undefinedCount = list.size() - work.size();
    TimSort sorter = new TimSort(work.toArray(), comparator);
    sorter.sort();
    list.clear();
    list.addAll(Arrays.asList(sorter.a));
    list.addAll(Collections.nCopies(undefinedCount, Js.UNDEFINED));
    return list;
  }

  /** V8's array-sort.tq (CPython's listsort): runs, binary insertion, merges with galloping. */
  private static final class TimSort {
    private final Object[] a;
    private final BiFunction<Object, Object, Object> comparator;
    private final int[] runBase = new int[85];
    private final int[] runLength = new int[85];
    private int runs;
    private int minGallop = 7;

    TimSort(Object[] a, BiFunction<Object, Object, Object> comparator) {
      this.a = a;
      this.comparator = comparator;
    }

    private double order(Object x, Object y) {
      double d = Js.toNumber(comparator.apply(x, y));
      return Double.isNaN(d) ? 0 : d;
    }

    void sort() {
      int n = a.length;
      int minRun = n;
      int r = 0;
      while (minRun >= 64) {
        r |= minRun & 1;
        minRun >>= 1;
      }
      minRun += r;
      for (int low = 0, remaining = n; n >= 2 && remaining != 0; ) {
        int length = countAndMakeRun(low, low + remaining);
        if (length < minRun) {
          int forced = Math.min(minRun, remaining);
          binaryInsertionSort(low, low + length, low + forced);
          length = forced;
        }
        runBase[runs] = low;
        runLength[runs++] = length;
        mergeCollapse();
        low += length;
        remaining -= length;
      }
      while (runs > 1) {
        int i = runs - 2;
        mergeAt(i > 0 && runLength[i - 1] < runLength[i + 1] ? i - 1 : i);
      }
    }

    private int countAndMakeRun(int lowArg, int high) {
      int low = lowArg + 1;
      if (low == high) {
        return 1;
      }
      int length = 2;
      Object previous = a[low];
      boolean descending = order(a[low], a[low - 1]) < 0;
      for (int i = low + 1; i < high; i++) {
        double c = order(a[i], previous);
        if (descending ? c >= 0 : c < 0) {
          break;
        }
        previous = a[i];
        length++;
      }
      for (int i = lowArg, j = lowArg + length - 1; descending && i < j; i++, j--) {
        Object t = a[i];
        a[i] = a[j];
        a[j] = t;
      }
      return length;
    }

    private void binaryInsertionSort(int low, int startArg, int high) {
      for (int start = low == startArg ? startArg + 1 : startArg; start < high; start++) {
        int left = low;
        int right = start;
        Object pivot = a[start];
        while (left < right) {
          int mid = left + ((right - left) >> 1);
          if (order(pivot, a[mid]) < 0) {
            right = mid;
          } else {
            left = mid + 1;
          }
        }
        System.arraycopy(a, left, a, left + 1, start - left);
        a[left] = pivot;
      }
    }

    private void mergeCollapse() {
      while (runs > 1) {
        int n = runs - 2;
        if ((n > 0 && runLength[n - 1] <= runLength[n] + runLength[n + 1])
            || (n > 1 && runLength[n - 2] <= runLength[n - 1] + runLength[n])) {
          if (runLength[n - 1] < runLength[n + 1]) {
            n--;
          }
        } else if (runLength[n] > runLength[n + 1]) {
          break;
        }
        mergeAt(n);
      }
    }

    private void mergeAt(int i) {
      int baseA = runBase[i];
      int lengthA = runLength[i];
      int baseB = runBase[i + 1];
      int lengthB = runLength[i + 1];
      runLength[i] = lengthA + lengthB;
      if (i == runs - 3) {
        runBase[i + 1] = runBase[i + 2];
        runLength[i + 1] = runLength[i + 2];
      }
      runs--;
      int k = gallopRight(a, a[baseB], baseA, lengthA, 0);
      baseA += k;
      lengthA -= k;
      if (lengthA == 0) {
        return;
      }
      lengthB = gallopLeft(a, a[baseA + lengthA - 1], baseB, lengthB, lengthB - 1);
      if (lengthB == 0) {
        return;
      }
      if (lengthA <= lengthB) {
        mergeLow(baseA, lengthA, baseB, lengthB);
      } else {
        mergeHigh(baseA, lengthA, baseB, lengthB);
      }
    }

    /** The k with arr[base + k - 1] < key <= arr[base + k], searched from the hint outwards. */
    private int gallopLeft(Object[] arr, Object key, int base, int length, int hint) {
      int lastOfs = 0;
      int offset = 1;
      if (order(arr[base + hint], key) < 0) {
        int maxOfs = length - hint;
        while (offset < maxOfs && order(arr[base + hint + offset], key) < 0) {
          lastOfs = offset;
          offset = (offset << 1) + 1;
          offset = offset <= 0 ? maxOfs : offset;
        }
        offset = Math.min(offset, maxOfs);
        lastOfs += hint;
        offset += hint;
      } else {
        int maxOfs = hint + 1;
        while (offset < maxOfs && order(arr[base + hint - offset], key) >= 0) {
          lastOfs = offset;
          offset = (offset << 1) + 1;
          offset = offset <= 0 ? maxOfs : offset;
        }
        offset = Math.min(offset, maxOfs);
        int tmp = lastOfs;
        lastOfs = hint - offset;
        offset = hint - tmp;
      }
      for (lastOfs++; lastOfs < offset; ) {
        int m = lastOfs + ((offset - lastOfs) >> 1);
        if (order(arr[base + m], key) < 0) {
          lastOfs = m + 1;
        } else {
          offset = m;
        }
      }
      return offset;
    }

    /** The k with arr[base + k - 1] <= key < arr[base + k], searched from the hint outwards. */
    private int gallopRight(Object[] arr, Object key, int base, int length, int hint) {
      int lastOfs = 0;
      int offset = 1;
      if (order(key, arr[base + hint]) < 0) {
        int maxOfs = hint + 1;
        while (offset < maxOfs && order(key, arr[base + hint - offset]) < 0) {
          lastOfs = offset;
          offset = (offset << 1) + 1;
          offset = offset <= 0 ? maxOfs : offset;
        }
        offset = Math.min(offset, maxOfs);
        int tmp = lastOfs;
        lastOfs = hint - offset;
        offset = hint - tmp;
      } else {
        int maxOfs = length - hint;
        while (offset < maxOfs && order(key, arr[base + hint + offset]) >= 0) {
          lastOfs = offset;
          offset = (offset << 1) + 1;
          offset = offset <= 0 ? maxOfs : offset;
        }
        offset = Math.min(offset, maxOfs);
        lastOfs += hint;
        offset += hint;
      }
      for (lastOfs++; lastOfs < offset; ) {
        int m = lastOfs + ((offset - lastOfs) >> 1);
        if (order(key, arr[base + m]) < 0) {
          offset = m;
        } else {
          lastOfs = m + 1;
        }
      }
      return offset;
    }

    private void mergeLow(int baseA, int lengthAArg, int baseB, int lengthBArg) {
      int lengthA = lengthAArg;
      int lengthB = lengthBArg;
      Object[] tmp = Arrays.copyOfRange(a, baseA, baseA + lengthA);
      int dest = baseA;
      int cursorTemp = 0;
      int cursorB = baseB;
      a[dest++] = a[cursorB++];
      boolean copyB = false;
      merge: {
        if (--lengthB == 0) {
          break merge;
        }
        if (lengthA == 1) {
          copyB = true;
          break merge;
        }
        while (true) {
          int winsA = 0;
          int winsB = 0;
          while (true) {
            if (order(a[cursorB], tmp[cursorTemp]) < 0) {
              a[dest++] = a[cursorB++];
              winsB++;
              winsA = 0;
              if (--lengthB == 0) {
                break merge;
              }
              if (winsB >= minGallop) {
                break;
              }
            } else {
              a[dest++] = tmp[cursorTemp++];
              winsA++;
              winsB = 0;
              if (--lengthA == 1) {
                copyB = true;
                break merge;
              }
              if (winsA >= minGallop) {
                break;
              }
            }
          }
          minGallop++;
          for (boolean first = true; winsA >= 7 || winsB >= 7 || first; first = false) {
            minGallop = Math.max(1, minGallop - 1);
            winsA = gallopRight(tmp, a[cursorB], cursorTemp, lengthA, 0);
            if (winsA > 0) {
              System.arraycopy(tmp, cursorTemp, a, dest, winsA);
              dest += winsA;
              cursorTemp += winsA;
              lengthA -= winsA;
              if (lengthA == 1) {
                copyB = true;
                break merge;
              }
              if (lengthA == 0) {
                break merge;
              }
            }
            a[dest++] = a[cursorB++];
            if (--lengthB == 0) {
              break merge;
            }
            winsB = gallopLeft(a, tmp[cursorTemp], cursorB, lengthB, 0);
            if (winsB > 0) {
              System.arraycopy(a, cursorB, a, dest, winsB);
              dest += winsB;
              cursorB += winsB;
              lengthB -= winsB;
              if (lengthB == 0) {
                break merge;
              }
            }
            a[dest++] = tmp[cursorTemp++];
            if (--lengthA == 1) {
              copyB = true;
              break merge;
            }
          }
          minGallop++;
        }
      }
      if (copyB) {
        System.arraycopy(a, cursorB, a, dest, lengthB);
        a[dest + lengthB] = tmp[cursorTemp];
      } else if (lengthA > 0) {
        System.arraycopy(tmp, cursorTemp, a, dest, lengthA);
      }
    }

    private void mergeHigh(int baseA, int lengthAArg, int baseB, int lengthBArg) {
      int lengthA = lengthAArg;
      int lengthB = lengthBArg;
      Object[] tmp = Arrays.copyOfRange(a, baseB, baseB + lengthB);
      int dest = baseB + lengthB - 1;
      int cursorTemp = lengthB - 1;
      int cursorA = baseA + lengthA - 1;
      a[dest--] = a[cursorA--];
      boolean copyA = false;
      merge: {
        if (--lengthA == 0) {
          break merge;
        }
        if (lengthB == 1) {
          copyA = true;
          break merge;
        }
        while (true) {
          int winsA = 0;
          int winsB = 0;
          while (true) {
            if (order(tmp[cursorTemp], a[cursorA]) < 0) {
              a[dest--] = a[cursorA--];
              winsA++;
              winsB = 0;
              if (--lengthA == 0) {
                break merge;
              }
              if (winsA >= minGallop) {
                break;
              }
            } else {
              a[dest--] = tmp[cursorTemp--];
              winsB++;
              winsA = 0;
              if (--lengthB == 1) {
                copyA = true;
                break merge;
              }
              if (winsB >= minGallop) {
                break;
              }
            }
          }
          minGallop++;
          for (boolean first = true; winsA >= 7 || winsB >= 7 || first; first = false) {
            minGallop = Math.max(1, minGallop - 1);
            winsA = lengthA - gallopRight(a, tmp[cursorTemp], baseA, lengthA, lengthA - 1);
            if (winsA > 0) {
              dest -= winsA;
              cursorA -= winsA;
              System.arraycopy(a, cursorA + 1, a, dest + 1, winsA);
              lengthA -= winsA;
              if (lengthA == 0) {
                break merge;
              }
            }
            a[dest--] = tmp[cursorTemp--];
            if (--lengthB == 1) {
              copyA = true;
              break merge;
            }
            winsB = lengthB - gallopLeft(tmp, a[cursorA], 0, lengthB, lengthB - 1);
            if (winsB > 0) {
              dest -= winsB;
              cursorTemp -= winsB;
              System.arraycopy(tmp, cursorTemp + 1, a, dest + 1, winsB);
              lengthB -= winsB;
              if (lengthB == 1) {
                copyA = true;
                break merge;
              }
              if (lengthB == 0) {
                break merge;
              }
            }
            a[dest--] = a[cursorA--];
            if (--lengthA == 0) {
              break merge;
            }
          }
          minGallop++;
        }
      }
      if (copyA) {
        dest -= lengthA;
        cursorA -= lengthA;
        System.arraycopy(a, cursorA + 1, a, dest + 1, lengthA);
        a[dest] = tmp[cursorTemp];
      } else if (lengthB > 0) {
        System.arraycopy(tmp, 0, a, dest - (lengthB - 1), lengthB);
      }
    }
  }
}
