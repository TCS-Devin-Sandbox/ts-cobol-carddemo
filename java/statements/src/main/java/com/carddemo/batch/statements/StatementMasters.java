package com.carddemo.batch.statements;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** CBSTM03B's master access: XREFFILE read sequentially, CUSTFILE and ACCTFILE read by key. */
interface StatementMasters {
    List<Map<String, Object>> crossReferences() throws Exception;

    Optional<Map<String, Object>> customer(BigDecimal customerId) throws Exception;

    Optional<Map<String, Object>> account(BigDecimal accountId) throws Exception;
}
