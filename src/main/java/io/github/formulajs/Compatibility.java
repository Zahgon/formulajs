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

import java.util.Map;

/** Compatibility functions. */
public final class Compatibility {
  private Compatibility() {}

  static void register(Map<String, Fn> ops) {
    ops.put("BETADIST", Statistical::BETA_DIST);
    ops.put("BETAINV", Statistical::BETA_INV);
    ops.put("BINOMDIST", Statistical::BINOM_DIST);
    ops.put("BINOMDIST.RANGE", Statistical::BINOM_DIST_RANGE);
    ops.put("CEILINGMATH", MathTrig::CEILING_MATH);
    ops.put("CEILINGPRECISE", MathTrig::CEILING_PRECISE);
    ops.put("CHIDIST", Statistical::CHISQ_DIST);
    ops.put("CHIDIST.RT", Statistical::CHISQ_DIST_RT);
    ops.put("CHIDISTRT", Statistical::CHISQ_DIST_RT);
    ops.put("CHIINV", Statistical::CHISQ_INV);
    ops.put("CHIINV.RT", Statistical::CHISQ_INV_RT);
    ops.put("CHIINVRT", Statistical::CHISQ_INV_RT);
    ops.put("CHITEST", Statistical::CHISQ_TEST);
    ops.put("COVAR", Statistical::COVARIANCE_P);
    ops.put("COVARIANCEP", Statistical::COVARIANCE_P);
    ops.put("COVARIANCES", Statistical::COVARIANCE_S);
    ops.put("CRITBINOM", Statistical::BINOM_INV);
    ops.put("EXPONDIST", Statistical::EXPON_DIST);
    ops.put("FDIST", Statistical::F_DIST);
    ops.put("FDIST.RT", Statistical::F_DIST_RT);
    ops.put("FDISTRT", Statistical::F_DIST_RT);
    ops.put("FINV", Statistical::F_INV);
    ops.put("FINV.RT", Statistical::F_INV_RT);
    ops.put("FINVRT", Statistical::F_INV_RT);
    ops.put("FLOORMATH", MathTrig::FLOOR_MATH);
    ops.put("FLOORPRECISE", MathTrig::FLOOR_PRECISE);
    ops.put("FTEST", Statistical::F_TEST);
    ops.put("GAMMADIST", Statistical::GAMMA_DIST);
    ops.put("GAMMAINV", Statistical::GAMMA_INV);
    ops.put("GAMMALNPRECISE", Statistical::GAMMALN_PRECISE);
    ops.put("HYPGEOMDIST", Statistical::HYPGEOM_DIST);
    ops.put("LOGINV", Statistical::LOGNORM_INV);
    ops.put("LOGNORMDIST", Statistical::LOGNORM_DIST);
    ops.put("LOGNORMINV", Statistical::LOGNORM_INV);
    ops.put("MODEMULT", Statistical::MODE_MULT);
    ops.put("MODESNGL", Statistical::MODE_SNGL);
    ops.put("NEGBINOMDIST", Statistical::NEGBINOM_DIST);
    ops.put("NETWORKDAYSINTL", DateTime::NETWORKDAYS_INTL);
    ops.put("NORMDIST", Statistical::NORM_DIST);
    ops.put("NORMINV", Statistical::NORM_INV);
    ops.put("NORMSDIST", Statistical::NORM_S_DIST);
    ops.put("NORMSINV", Statistical::NORM_S_INV);
    ops.put("PERCENTILEEXC", Statistical::PERCENTILE_EXC);
    ops.put("PERCENTILEINC", Statistical::PERCENTILE_INC);
    ops.put("PERCENTRANKEXC", Statistical::PERCENTRANK_EXC);
    ops.put("PERCENTRANKINC", Statistical::PERCENTRANK_INC);
    ops.put("POISSONDIST", Statistical::POISSON_DIST);
    ops.put("QUARTILEEXC", Statistical::QUARTILE_EXC);
    ops.put("QUARTILEINC", Statistical::QUARTILE_INC);
    ops.put("RANKAVG", Statistical::RANK_AVG);
    ops.put("RANKEQ", Statistical::RANK_EQ);
    ops.put("SKEWP", Statistical::SKEW_P);
    ops.put("STDEVP", Statistical::STDEV_P);
    ops.put("STDEVS", Statistical::STDEV_S);
    ops.put("TDIST", Statistical::T_DIST);
    ops.put("TDIST.2T", Statistical::T_DIST_2T);
    ops.put("TDIST.RT", Statistical::T_DIST_RT);
    ops.put("TDISTRT", Statistical::T_DIST_RT);
    ops.put("TINV", Statistical::T_INV);
    ops.put("TINV.2T", Statistical::T_INV_2T);
    ops.put("TTEST", Statistical::T_TEST);
    ops.put("VARP", Statistical::VAR_P);
    ops.put("VARS", Statistical::VAR_S);
    ops.put("WEIBULLDIST", Statistical::WEIBULL_DIST);
    ops.put("WORKDAYINTL", DateTime::WORKDAY_INTL);
    ops.put("ZTEST", Statistical::Z_TEST);
  }
}
