package com.carddemo.trantype.service;

import java.util.List;

/** Stateless replacement of the CTLI COMMAREA paging fields (WS-CA-FIRST/LAST-TR-CODE, SCREEN-NUM, NEXT-PAGE-IND). */
public record TransactionTypePage(
        List<TransactionTypeDto> rows,
        String firstKey,
        String lastKey,
        String nextCursor,
        boolean hasNext,
        boolean hasPrev,
        int pageNumber,
        String message) {
}
