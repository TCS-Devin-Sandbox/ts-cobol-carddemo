package com.carddemo.vsammq;

import com.carddemo.trantype.web.ErrorResponse;
import com.carddemo.vsammq.AccountInquiryReply.Request;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** CDRA account inquiry over REST instead of the CARD.DEMO request/reply queues. */
@RestController
public class AccountInquiryController {
    /** Single-byte like the EBCDIC (IBM037) and ISO-8859-1 record codecs, so 1000 characters stay 1000 bytes. */
    static final MediaType TEXT_LATIN1 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.ISO_8859_1);
    static final String OUTCOME_HEADER = "X-Inquiry-Outcome";
    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{11}");
    private final AccountInquiryService inquiries;

    AccountInquiryController(AccountInquiryService inquiries) {
        this.inquiries = inquiries;
    }

    /** The MQ request message as-is: function X(4), account 9(11), up to 1000 characters. */
    @PostMapping(path = "/api/accounts/inquiry", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> message(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(AccountInquiryReply.MESSAGE_LENGTH + 1);
        boolean printableAscii = true;
        for (byte b : bytes) {
            printableAscii &= b >= 0x20 && b <= 0x7E;
        }
        if (bytes.length > AccountInquiryReply.MESSAGE_LENGTH || !printableAscii) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE",
                    "Request must be at most 1000 printable ASCII characters");
        }
        return reply(Request.parse(new String(bytes, StandardCharsets.US_ASCII)));
    }

    @GetMapping("/api/accounts/{accountId}/inquiry")
    public ResponseEntity<?> inquiry(@PathVariable String accountId) {
        if (!ACCOUNT_ID.matcher(accountId).matches()) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_ID", "Account ID must be 11 digits");
        }
        return reply(new Request(AccountInquiryReply.INQUIRY_FUNCTION, accountId));
    }

    private ResponseEntity<String> reply(Request request) {
        var reply = inquiries.process(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(TEXT_LATIN1)
                .header(OUTCOME_HEADER, reply.outcome().name()).body(reply.message());
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_INQUIRY_UNAVAILABLE", "Unable to read account");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body(new ErrorResponse(code, message));
    }
}
