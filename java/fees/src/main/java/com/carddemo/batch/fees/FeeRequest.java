package com.carddemo.batch.fees;

import java.math.BigDecimal;

public record FeeRequest(String FEE_ACCT_ID, String FEE_GROUP_ID, BigDecimal FEE_CYCLE_BAL,
                         int FEE_LATE_DAYS, int FEE_CYCLE_DAYS, BigDecimal FEE_AMT,
                         BigDecimal FEE_AVG_DAILY, int FEE_RC) {
}
