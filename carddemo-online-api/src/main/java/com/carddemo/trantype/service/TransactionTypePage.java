package com.carddemo.trantype.service;

import java.util.List;

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
