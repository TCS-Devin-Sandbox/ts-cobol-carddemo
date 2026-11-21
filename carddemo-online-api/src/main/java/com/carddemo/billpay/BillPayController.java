package com.carddemo.billpay;

import com.carddemo.billpay.BillPayService.Payment;
import com.carddemo.billpay.BillPayService.Screen;
import com.carddemo.trantype.web.ErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** CB00 / COBIL00C: Enter on the COBIL0A map. PF3, PF4 and first entry need no data access and stay in the UI. */
@RestController
public class BillPayController {
    private final BillPayService billPay;

    BillPayController(BillPayService billPay) {
        this.billPay = billPay;
    }

    @PostMapping("/api/bill-payments")
    public ResponseEntity<Screen> enter(@RequestBody Payment payment) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(billPay.process(payment));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("BILL_PAY_UNAVAILABLE", "Unable to lookup Account..."));
    }
}
