# CPVS - pending authorization summary (COPAUS0C)

Batch G row G7. COPAUS0C's COPAU0A map becomes `POST /api/authorizations/summary`
and the React route `/authorizations` (main menu option 11). The CAVW
account/xref/customer reads (`AccountViewService.lookup`) and the G2 tables
`pending_auth_summary` (PAUTSUM0) and `pending_auth_detail` (PAUTDTL1) back it.
The detail screen (CPVD, COPAUS1C) is row G8.

## Contract

One POST per AID key: `{action: ENTER|PF7|PF8, accountId, selections[5], state}`.
`state` is CDEMO-ACCT-ID plus CDEMO-CPVS-INFO (COCOM01Y): `pageNumber`,
`previousPageKeys` (CDEMO-CPVS-PAUKEY-PREV-PG, at most 20), `lastKey`,
`nextPage` and `rowKeys` (CDEMO-CPVS-AUTH-KEYS). A key is PA-AUTHORIZATION-KEY
as 5 + 9 digits (inverted date, inverted time). The client sends back the state
it received, as the terminal sent back the COMMAREA; the API rejects a state
the screen cannot produce (400).

The response carries `account` and `summary` (ENTER only), `rows` (null means
the list stays on the screen, as SEND without ERASE), `message`,
`accountIdInvalid`, `transfer` (XCTL to COPAUS1C/CPVD with the selected key)
and the new `state`. Read failures return 503 `AUTHORIZATION_SUMMARY_UNAVAILABLE`.

## Preserved behaviour

* Rows come in PAUT9CTS order (ascending inverted keys, so newest first), five
  per page with one lookahead GNP deciding whether PF8 has a next page.
* PF7 on page 1 and PF8 without a next page keep the list and show the legacy
  top/bottom messages.
* Legacy quirk: PROCESS-PAGE-FORWARD bumps the page number on the first row
  even when paging back, so PF7 always shows the previous page of the page
  PF8 last reached; PF7 never goes back more than one page.
* `S`/`s` on the first non-blank selection transfers to CPVD; any other value
  shows `Invalid selection. Valid value is S` and the screen is redrawn.
* GET-AUTH-SUMMARY reads by the typed account, so a missing XREF still shows
  the authorization counts; a missing PAUTSUM0 shows zero counts and no list.
* ACCSTAT is never moved by COPAUS0C and stays blank. The name, address lines
  and phone use the STRING delimiters of GATHER-ACCOUNT-DETAILS (first word of
  first/last name, address text up to a double space, ZIP(1:5)).
* NOTFND messages keep the legacy text with RESP 13 / RESP2 80; when both the
  account and the customer are missing the customer message wins (last STRING).

## Deviations

* PF8 at page 20 with a next page returns `Only 20 pages can be browsed. Press
  Enter to start again...` instead of overrunning the 20-entry key table
  (scope Q9).
  The bound is on the slot counter, which the PF7 quirk leaves one ahead of
  the page shown, so after a PF7 the last reachable page is 19.
* `nextPage` is recomputed on every list fill; COPAUS0C keeps a stale flag
  when a fill ends early, which leads PF8 to an empty page or a GNP without
  a scheduled PSB.
* A missing XREF does not read ACCTDAT/CUSTDAT with the undefined key COPAUS0C
  uses; the account section stays blank.
* Amounts keep full precision instead of the 3270 edit pictures (as CAVW).
* First entry from the menu is blank: the web app carries no COMMAREA account.
  Navigating with `state.accountId` (the CPVD return path) gathers at once.
* Until G8 installs CPVD, the UI reports the transfer as not installed.
* Other keys show CCDA-MSG-INVALID-KEY without a round trip or redraw.

## Paragraph trace

| COPAUS0C paragraph | Tests |
|---|---|
| MAIN-PARA first entry / re-entry | AuthorizationSummaryPage.test: MAIN-PARA cases; SignonMenuTest.COMEN01C_PROCESS_ENTER_KEY_COPAUS0C |
| PROCESS-ENTER-KEY edits | AuthorizationSummaryFlowTest.PROCESS_ENTER_KEY_blankAndNonNumericAccountIds; UI invalid-account case |
| PROCESS-ENTER-KEY selection / XCTL | AuthorizationSummaryFlowTest.PROCESS_ENTER_KEY_selectionTransfersToCpvdOrRejectsOtherValues; UI S selection case |
| GATHER-DETAILS / GATHER-ACCOUNT-DETAILS | AuthorizationSummaryIntegrationTest.GATHER_DETAILS_composesAccountSectionSummaryAndFirstPageInKeyOrder; AuthorizationSummaryFlowTest.GATHER_* |
| GETCARDXREF-BYACCT NOTFND | AuthorizationSummaryIntegrationTest.GETCARDXREF_BYACCT_notFoundStillShowsTheAuthorizationSummary |
| GET-AUTH-SUMMARY | AuthorizationSummaryFlowTest.GET_AUTH_SUMMARY_missingRootShowsZeroCountsAndNoList |
| PROCESS-PAGE-FORWARD / GET-AUTHORIZATIONS | AuthorizationSummaryFlowTest.GATHER_DETAILS_firstPageNewestFirstWithLookahead, PROCESS_PAGE_FORWARD_exactlyFiveRowsHasNoNextPage |
| POPULATE-AUTH-LIST | AuthorizationSummaryIntegrationTest.GATHER_DETAILS_*; UI summary/rows case |
| PROCESS-PF8-KEY / REPOSITION-AUTHORIZATIONS | AuthorizationSummaryFlowTest.PROCESS_PF8_KEY_pagesForwardThenStopsAtBottom; AuthorizationSummaryIntegrationTest.PROCESS_PF8_KEY_and_PF7_overHttp; UI paging case |
| PROCESS-PF7-KEY | AuthorizationSummaryFlowTest.PROCESS_PF7_KEY_topOfFirstPage, PROCESS_PF7_KEY_legacyPageCounterOnlyEverGoesBackOnePage |
| 20-page bound (Q9) | AuthorizationSummaryFlowTest.PROCESS_PF8_KEY_boundedAtTwentyPages; UI 20-page case |
| Read failure | AuthorizationSummaryIntegrationTest.readFailureReturnsSafeUnavailableMessage, forgedStateAndUnknownKeysAreRejected |
