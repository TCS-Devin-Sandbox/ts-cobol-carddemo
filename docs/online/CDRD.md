# CDRD — System date and time (G10)

CDRD started CODATE01 from an MQ trigger: it read 1000-byte requests, ignored
their content, and put a 1000-byte reply on `CARD.DEMO.REPLY.DATE`. The owner
approved REST for CDRD ([batch G scope](../batch-g/scope.md), option 3B), so the
online API serves the same reply at `GET /api/system-time`. No MQ broker is
involved.

## Contract

`GET /api/system-time` requires the usual sign-on JWT (any user type) and takes
no input; query parameters are ignored, as CODATE01 ignored the request fields.

| `Accept` | Body |
|---|---|
| `application/json` (default) | `{"tranId":"CDRD","program":"CODATE01","date":"MM-DD-YYYY","time":"HH:MM:SS","reply":"<1000 chars>"}` |
| `text/plain` | The 1000-byte reply buffer as sent to MQ |

The reply is `SYSTEM DATE : ` + `MM-DD-YYYY` + `SYSTEM TIME : ` + `HH:MM:SS`
(46 characters, no separator between the date and the second label) followed by
954 spaces. Example with the estate's frozen clock (2022-07-05 10:00:00):

```text
SYSTEM DATE : 07-05-2022SYSTEM TIME : 10:00:00<954 spaces>
```

Time is 24-hour local time of the API's default time zone (CICS used the region's
local clock); fractions of a second are truncated.

## Source-to-test trace

Source: [CODATE01](../../app/app-vsam-mq/cbl/CODATE01.cbl) (cited line map in
[batch-g/inventory.md](../batch-g/inventory.md#codate01--mq-system-clock-responder)).

| COBOL paragraph / behavior | Replacement | Automated proof (`SystemTimeTest`) |
|---|---|---|
| 1000/2300/2400/2100 trigger RETRIEVE, MQOPEN request/reply/error queues | HTTP request; Spring Security JWT in place of queue security | `CDRD_requires_sign_on` |
| 3000-GET-REQUEST: MsgId, CorrelId, ReplyToQ saved; request copied but unused | No request body; query input ignored | `CDRD_json_reply_ignores_request_input` |
| 4000-PROCESS-REQUEST-REPLY: blank reply, ASKTIME, FORMATTIME MMDDYYYY DATESEP('-') TIME TIMESEP, STRING | `SystemTimeService.now()` on an injected `Clock` | `CODATE01_4000_PROCESS_REQUEST_REPLY_frozen_clock_exact_1000_byte_reply`, `CODATE01_FORMATTIME_..._24_hour_local_time` |
| 4100-PUT-REPLY: 1000-byte MQFMT-STRING buffer | `text/plain` body of exactly 1000 bytes; JSON `reply` | `CODATE01_4100_PUT_REPLY_plain_text_is_the_1000_byte_buffer` |
| 9000-ERROR to `CARD.DEMO.ERROR`, 8000-TERMINATION MQCLOSE | Not ported: no queues to open or close; framework errors use the API's normal error responses | none |

**Weaker proof:** CODATE01 needs CICS and MQ, so it cannot run off-host and there
is no legacy output to compare. The expected reply is derived from the source
(PIC widths and STRING literals) and asserted byte for byte under a frozen clock.

**Contract change:** a real MQ client of CDRD would need to call this endpoint
instead; no such client exists in the repository (scope section 3).
