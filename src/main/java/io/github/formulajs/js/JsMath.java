/*
 * Copyright (C) 1993-2004 by Sun Microsystems, Inc.; ported in formulajs/formulajs. All rights reserved.
 * Developed at SunPro, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this software is freely granted, provided that this notice is
 * preserved.
 *
 * Ported from V8's src/base/ieee754.cc (V8 12.4, the engine of Node.js 22), which derives from FreeBSD's msun.
 */
package io.github.formulajs.js;

/**
 * Math.pow, Math.exp, Math.atan and Math.atan2 exactly as V8 computes them (V8's pow divides by the whole
 * denominator in its final step, unlike fdlibm, so java.lang.StrictMath answers differently in a few percent of cases;
 * V8's exp returns Math.E for 1). The other Math functions the library uses (log, sin, cos, tan, asin, acos, ...) are
 * fdlibm's in V8 too and are taken from {@link StrictMath}.
 */
public final class JsMath {
  private JsMath() {}

  private static int hi(double d) {
    return (int) (Double.doubleToRawLongBits(d) >> 32);
  }

  private static int lo(double d) {
    return (int) Double.doubleToRawLongBits(d);
  }

  private static double withHi(double d, int high) {
    return Double.longBitsToDouble(((long) high << 32) | (Double.doubleToRawLongBits(d) & 0xFFFFFFFFL));
  }

  private static double withLowZero(double d) {
    return Double.longBitsToDouble(Double.doubleToRawLongBits(d) & 0xFFFFFFFF00000000L);
  }

  private static final double[] BP = {1.0, 1.5};
  private static final double[] DP_H = {0.0, 5.84962487220764160156e-01};
  private static final double[] DP_L = {0.0, 1.35003920212974897128e-08};
  private static final double TWO53 = 9007199254740992.0;
  private static final double HUGE = 1.0e300;
  private static final double TINY = 1.0e-300;
  private static final double L1 = 5.99999999999994648725e-01;
  private static final double L2 = 4.28571428578550184252e-01;
  private static final double L3 = 3.33333329818377432918e-01;
  private static final double L4 = 2.72728123808534006489e-01;
  private static final double L5 = 2.30660745775561754067e-01;
  private static final double L6 = 2.06975017800338417784e-01;
  private static final double P1 = 1.66666666666666019037e-01;
  private static final double P2 = -2.77777777770155933842e-03;
  private static final double P3 = 6.61375632143793436117e-05;
  private static final double P4 = -1.65339022054652515390e-06;
  private static final double P5 = 4.13813679705723846039e-08;
  private static final double LG2 = 6.93147180559945286227e-01;
  private static final double LG2_H = 6.93147182464599609375e-01;
  private static final double LG2_L = -1.90465429995776804525e-09;
  private static final double OVT = 8.0085662595372944372e-0017;
  private static final double CP = 9.61796693925975554329e-01;
  private static final double CP_H = 9.61796700954437255859e-01;
  private static final double CP_L = -7.02846165095275826516e-09;
  private static final double IVLN2 = 1.44269504088896338700e+00;
  private static final double IVLN2_H = 1.44269502162933349609e+00;
  private static final double IVLN2_L = 1.92596299112661746887e-08;

  /** V8's base::ieee754::pow (ECMAScript's NaN for (+-1) ** (+-Infinity) included). */
  public static double pow(double x, double y) {
    int hx = hi(x);
    int lx = lo(x);
    int hy = hi(y);
    int ly = lo(y);
    int ix = hx & 0x7fffffff;
    int iy = hy & 0x7fffffff;
    if ((iy | ly) == 0) {
      return 1.0;
    }
    if (ix > 0x7ff00000 || (ix == 0x7ff00000 && lx != 0) || iy > 0x7ff00000 || (iy == 0x7ff00000 && ly != 0)) {
      return x + y;
    }
    int yisint = 0;
    if (hx < 0) {
      if (iy >= 0x43400000) {
        yisint = 2;
      } else if (iy >= 0x3ff00000) {
        int k = (iy >> 20) - 0x3ff;
        if (k > 20) {
          int j = ly >>> (52 - k);
          if ((j << (52 - k)) == ly) {
            yisint = 2 - (j & 1);
          }
        } else if (ly == 0) {
          int j = iy >> (20 - k);
          if ((j << (20 - k)) == iy) {
            yisint = 2 - (j & 1);
          }
        }
      }
    }
    if (ly == 0) {
      if (iy == 0x7ff00000) {
        if (((ix - 0x3ff00000) | lx) == 0) {
          return y - y;
        } else if (ix >= 0x3ff00000) {
          return hy >= 0 ? y : 0.0;
        } else {
          return hy < 0 ? -y : 0.0;
        }
      }
      if (iy == 0x3ff00000) {
        return hy < 0 ? 1.0 / x : x;
      }
      if (hy == 0x40000000) {
        return x * x;
      }
      if (hy == 0x3fe00000 && hx >= 0) {
        return Math.sqrt(x);
      }
    }
    double ax = Math.abs(x);
    if (lx == 0 && (ix == 0x7ff00000 || ix == 0 || ix == 0x3ff00000)) {
      double z = ax;
      if (hy < 0) {
        z = 1.0 / z;
      }
      if (hx < 0) {
        if (((ix - 0x3ff00000) | yisint) == 0) {
          z = Double.NaN;
        } else if (yisint == 1) {
          z = -z;
        }
      }
      return z;
    }
    int n = (hx >> 31) + 1;
    if ((n | yisint) == 0) {
      return Double.NaN;
    }
    double s = 1.0;
    if ((n | (yisint - 1)) == 0) {
      s = -1.0;
    }
    double t1;
    double t2;
    if (iy > 0x41e00000) {
      if (iy > 0x43f00000) {
        if (ix <= 0x3fefffff) {
          return hy < 0 ? HUGE * HUGE : TINY * TINY;
        }
        if (ix >= 0x3ff00000) {
          return hy > 0 ? HUGE * HUGE : TINY * TINY;
        }
      }
      if (ix < 0x3fefffff) {
        return hy < 0 ? s * HUGE * HUGE : s * TINY * TINY;
      }
      if (ix > 0x3ff00000) {
        return hy > 0 ? s * HUGE * HUGE : s * TINY * TINY;
      }
      double t = ax - 1.0;
      double w = (t * t) * (0.5 - t * (0.3333333333333333333333 - t * 0.25));
      double u = IVLN2_H * t;
      double v = t * IVLN2_L - w * IVLN2;
      t1 = withLowZero(u + v);
      t2 = v - (t1 - u);
    } else {
      n = 0;
      if (ix < 0x00100000) {
        ax *= TWO53;
        n -= 53;
        ix = hi(ax);
      }
      n += (ix >> 20) - 0x3ff;
      int j = ix & 0x000fffff;
      ix = j | 0x3ff00000;
      int k;
      if (j <= 0x3988E) {
        k = 0;
      } else if (j < 0xBB67A) {
        k = 1;
      } else {
        k = 0;
        n += 1;
        ix -= 0x00100000;
      }
      ax = withHi(ax, ix);
      double u = ax - BP[k];
      double v = 1.0 / (ax + BP[k]);
      double ss = u * v;
      double sH = withLowZero(ss);
      double tH = withHi(0.0, ((ix >> 1) | 0x20000000) + 0x00080000 + (k << 18));
      double tL = ax - (tH - BP[k]);
      double sL = v * ((u - sH * tH) - sH * tL);
      double s2 = ss * ss;
      double r = s2 * s2 * (L1 + s2 * (L2 + s2 * (L3 + s2 * (L4 + s2 * (L5 + s2 * L6)))));
      r += sL * (sH + ss);
      s2 = sH * sH;
      tH = withLowZero(3.0 + s2 + r);
      tL = r - ((tH - 3.0) - s2);
      u = sH * tH;
      v = sL * tH + tL * ss;
      double pH = withLowZero(u + v);
      double pL = v - (pH - u);
      double zH = CP_H * pH;
      double zL = CP_L * pH + pL * CP + DP_L[k];
      double t = n;
      t1 = withLowZero(((zH + zL) + DP_H[k]) + t);
      t2 = zL - (((t1 - t) - DP_H[k]) - zH);
    }
    double y1 = withLowZero(y);
    double pL = (y - y1) * t1 + y * t2;
    double pH = y1 * t1;
    double z = pL + pH;
    int j = hi(z);
    int i = lo(z);
    if (j >= 0x40900000) {
      if (((j - 0x40900000) | i) != 0) {
        return s * HUGE * HUGE;
      } else if (pL + OVT > z - pH) {
        return s * HUGE * HUGE;
      }
    } else if ((j & 0x7fffffff) >= 0x4090cc00) {
      if (((j - 0xc090cc00) | i) != 0) {
        return s * TINY * TINY;
      } else if (pL <= z - pH) {
        return s * TINY * TINY;
      }
    }
    i = j & 0x7fffffff;
    int k = (i >> 20) - 0x3ff;
    n = 0;
    if (i > 0x3fe00000) {
      n = j + (0x00100000 >> (k + 1));
      k = ((n & 0x7fffffff) >> 20) - 0x3ff;
      double t = withHi(0.0, n & ~(0x000fffff >> k));
      n = ((n & 0x000fffff) | 0x00100000) >> (20 - k);
      if (j < 0) {
        n = -n;
      }
      pH -= t;
    }
    double t = withLowZero(pL + pH);
    double u = t * LG2_H;
    double v = (pL - (t - pH)) * LG2 + t * LG2_L;
    z = u + v;
    double w = v - (z - u);
    t = z * z;
    t1 = z - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
    double r = (z * t1) / ((t1 - 2.0) - (w + z * w));
    z = 1.0 - (r - z);
    j = hi(z) + (n << 20);
    if ((j >> 20) <= 0) {
      z = Math.scalb(z, n);
    } else {
      z = withHi(z, hi(z) + (n << 20));
    }
    return s * z;
  }

  private static final double[] ATAN_HI = {
    4.63647609000806093515e-01, 7.85398163397448278999e-01, 9.82793723247329054082e-01, 1.57079632679489655800e+00
  };
  private static final double[] ATAN_LO = {
    2.26987774529616870924e-17, 3.06161699786838301793e-17, 1.39033110312309984516e-17, 6.12323399573676603587e-17
  };
  private static final double[] AT = {
    3.33333333333329318027e-01, -1.99999999998764832476e-01, 1.42857142725034663711e-01,
    -1.11111104054623557880e-01, 9.09088713343650656196e-02, -7.69187620504482999495e-02,
    6.66107313738753120669e-02, -5.83357013379057348645e-02, 4.97687799461593236017e-02,
    -3.65315727442169155270e-02, 1.62858201153657823623e-02
  };

  /** V8's base::ieee754::atan. */
  public static double atan(double x) {
    int hx = hi(x);
    int ix = hx & 0x7FFFFFFF;
    int id;
    if (ix >= 0x44100000) {
      if (ix > 0x7FF00000 || (ix == 0x7FF00000 && lo(x) != 0)) {
        return x + x;
      }
      return hx > 0 ? ATAN_HI[3] + ATAN_LO[3] : -ATAN_HI[3] - ATAN_LO[3];
    }
    if (ix < 0x3FDC0000) {
      if (ix < 0x3E400000 && HUGE + x > 1.0) {
        return x;
      }
      id = -1;
    } else {
      x = Math.abs(x);
      if (ix < 0x3FF30000) {
        if (ix < 0x3FE60000) {
          id = 0;
          x = (2.0 * x - 1.0) / (2.0 + x);
        } else {
          id = 1;
          x = (x - 1.0) / (x + 1.0);
        }
      } else if (ix < 0x40038000) {
        id = 2;
        x = (x - 1.5) / (1.0 + 1.5 * x);
      } else {
        id = 3;
        x = -1.0 / x;
      }
    }
    double z = x * x;
    double w = z * z;
    double s1 = z * (AT[0] + w * (AT[2] + w * (AT[4] + w * (AT[6] + w * (AT[8] + w * AT[10])))));
    double s2 = w * (AT[1] + w * (AT[3] + w * (AT[5] + w * (AT[7] + w * AT[9]))));
    if (id < 0) {
      return x - x * (s1 + s2);
    }
    z = ATAN_HI[id] - ((x * (s1 + s2) - ATAN_LO[id]) - x);
    return hx < 0 ? -z : z;
  }

  /** V8's base::ieee754::exp: fdlibm's, except that exp(1) is exactly Math.E. */
  public static double exp(double x) {
    return x == 1.0 ? Math.E : StrictMath.exp(x);
  }

  private static final double PI_O_4 = 7.8539816339744827900E-01;
  private static final double PI_O_2 = 1.5707963267948965580E+00;
  private static final double PI = 3.1415926535897931160E+00;
  private static final double PI_LO = 1.2246467991473531772E-16;

  /** V8's base::ieee754::atan2. */
  public static double atan2(double y, double x) {
    int hx = hi(x);
    int lx = lo(x);
    int ix = hx & 0x7FFFFFFF;
    int hy = hi(y);
    int ly = lo(y);
    int iy = hy & 0x7FFFFFFF;
    if ((ix | ((lx | -lx) >>> 31)) > 0x7FF00000 || (iy | ((ly | -ly) >>> 31)) > 0x7FF00000) {
      return x + y;
    }
    if (((hx - 0x3FF00000) | lx) == 0) {
      return atan(y);
    }
    int m = ((hy >> 31) & 1) | ((hx >> 30) & 2);
    if ((iy | ly) == 0) {
      return switch (m) {
        case 0, 1 -> y;
        case 2 -> PI + TINY;
        default -> -PI - TINY;
      };
    }
    if ((ix | lx) == 0) {
      return hy < 0 ? -PI_O_2 - TINY : PI_O_2 + TINY;
    }
    if (ix == 0x7FF00000) {
      if (iy == 0x7FF00000) {
        return switch (m) {
          case 0 -> PI_O_4 + TINY;
          case 1 -> -PI_O_4 - TINY;
          case 2 -> 3.0 * PI_O_4 + TINY;
          default -> -3.0 * PI_O_4 - TINY;
        };
      }
      return switch (m) {
        case 0 -> 0.0;
        case 1 -> -0.0;
        case 2 -> PI + TINY;
        default -> -PI - TINY;
      };
    }
    if (iy == 0x7FF00000) {
      return hy < 0 ? -PI_O_2 - TINY : PI_O_2 + TINY;
    }
    int k = (iy - ix) >> 20;
    double z;
    if (k > 60) {
      z = PI_O_2 + 0.5 * PI_LO;
      m &= 1;
    } else if (hx < 0 && k < -60) {
      z = 0.0;
    } else {
      z = atan(Math.abs(y / x));
    }
    return switch (m) {
      case 0 -> z;
      case 1 -> -z;
      case 2 -> PI - (z - PI_LO);
      default -> (z - PI_LO) - PI;
    };
  }
}
