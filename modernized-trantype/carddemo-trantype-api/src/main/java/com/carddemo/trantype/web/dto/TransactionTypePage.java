package com.carddemo.trantype.web.dto;

import java.util.List;

/**
 * One keyset page. Replaces the CTLI COMMAREA paging fields: firstKey = WS-CA-FIRST-TR-CODE,
 * nextCursor = WS-CA-LAST-TR-CODE (look-ahead key), hasNext = CA-NEXT-PAGE-EXISTS,
 * pageNumber = WS-CA-SCREEN-NUM.
 */
public record TransactionTypePage(
        List<TransactionTypeDto> rows,
        String firstKey,
        String lastKey,
        String nextCursor,
        boolean hasNext,
        boolean hasPrev,
        int pageNumber,
        int size,
        String infoMessage,
        String errorMessage) {
}
