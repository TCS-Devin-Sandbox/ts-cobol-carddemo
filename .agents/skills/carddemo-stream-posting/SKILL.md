---
name: carddemo-stream-posting
description: DRAFT - NOT PROVEN. Source inventory and shared-standard applicability for daily transaction posting and reporting.
---

# DRAFT - NOT PROVEN

## Posting stream annex

Use this source audit with the [shared standards](../carddemo-conversion-standards/SKILL.md). The estate has legacy posting fixtures. This revision has no posting Java module, candidate adapter, ticket design/map, rule register or assigned rule-ID prefix. Existing source literals such as reject reason 100 are outcomes, not modernization IDs.

Evidence keys · **P** [CBTRN02C.cbl](../../../app/cbl/CBTRN02C.cbl), **R** [CBTRN03C.cbl](../../../app/cbl/CBTRN03C.cbl), **JP** [POSTTRAN.jcl](../../../app/jcl/POSTTRAN.jcl), **JR** [TRANREPT.jcl](../../../app/jcl/TRANREPT.jcl), **C** [copybooks](../../../app/cpy/), **M** [chain manifest](../../../tools/runjcl/chains.json#L2-L40). Source ranges below are inclusive.

## Programs, jobs and datasets

The lab runs POSTTRAN before TRANREPT (M 25–28). Production scheduling, conditional execution and restart policy require host evidence. TRANREPT has two source EXEC statements both labelled STEP05R. The fixture RC map records PRC001 for the procedure and STEP05R for SORT. STEP10R is the reporting program (JR 23, 37, 59; e.g. [p02 recorded RC map](../../../fixtures/posting/p02-invalid-card/expected/rc.json), where report RC 0 accompanies posting RC 4).

| Job/step | Source and role |
|---|---|
| POSTTRAN / STEP15 | CBTRN02C reads daily transactions sequentially, produces posted master and rejects, and rewrites account/category records (JP 23–42; P 29–61, 193–234, 424–451). |
| TRANREPT / STEP05R (REPROC/PRC001) | Unloads the posted KSDS to backup GDG (+1), 350 FB (JR 23–33). |
| TRANREPT / STEP05R (SORT) | Filters the backup by inclusive processing date bounds and sorts ascending by card to daily GDG (+1), inheriting backup DCB (JR 37–55). |
| TRANREPT / STEP10R | CBTRN03C reads daily output and reference/date inputs, writes formatted report GDG (+1), 133 FB (JR 59–80; R 159–217). |
| Lab setup | DEFGDGB, DALYREJS, ACCTFILE, XREFFILE, TCATBALF, DISCGRP, TRANTYPE, TRANCATG, TRANFILE (M 14–24). |

All DSNs below carry the prefix `AWS.M2.CARDDEMO.`. Load library `LOADLIB` and SYSOUT/SYSPRINT are separate DDs (JP 24–27; JR 60–63). The harness freezes the clock at `2022-07-05 10:00:00.00` (M 4).

| DD / dataset | Layout and role |
|---|---|
| JP DALYTRAN / `DALYTRAN.PS` | 350, CVTRA06Y, input (JP 30–31; P 66–69, 102). |
| JP TRANFILE, JR PRC001.FILEIN / `TRANSACT.VSAM.KSDS` | 350, CVTRA05Y, produced by posting and unloaded by reporting (JP 28–29; JR 26–27; P 71–74, 107, 254–257). |
| JP XREFFILE, JR CARDXREF / `CARDXREF.VSAM.KSDS` | 50, CVACT03Y, keyed by card (JP 32–33; JR 67–68; P 40–44, 112; R 98, 484–491). |
| JP ACCTFILE / `ACCTDATA.VSAM.KSDS` | 300, CVACT01Y, lookup and rewrite (JP 39–40; P 51–55, 86–89, 121, 545–560). |
| JP TCATBALF / `TCATBALF.VSAM.KSDS` | 50, CVTRA01Y, account/type/category key, read and write/rewrite (JP 41–42; P 91–97, 126, 467–542). |
| JP DALYREJS / `DALYREJS(+1)` | 430/F, NEW/CATLG/DELETE. P 176–182 and 446–451 concatenate the 350-byte input and 80-byte trailer (four reason digits + 76 description bytes). Comparator layout [REJTRAN](../../../tools/parity/layouts/REJTRAN.cpy), not a program COPY (JP 34–38; P 81–84). |
| JR PRC001.FILEOUT, SORTIN / `TRANSACT.BKUP(+1)` | 350/FB CVTRA05Y records, unload output and sort input (JR 29–39). |
| JR SORTOUT, TRANFILE / `TRANSACT.DALY(+1)` | 350/FB CVTRA05Y records, SORT to report (JR 51–55, 65–66; R 93). |
| JR TRANTYPE / `TRANTYPE.VSAM.KSDS` | CVTRA03Y, 60, type description (JR 69–70; R 103, 494–501; C CVTRA03Y 4–7). |
| JR TRANCATG / `TRANCATG.VSAM.KSDS` | CVTRA04Y, 60, type/category description (JR 71–72; R 108, 504–511; C CVTRA04Y 4–10). |
| JR DATEPARM / `DATEPARM` | 80-byte fixture input; start/end 10-byte text fields with one separator in working storage (M 14; JR 73–74; R 122–125, 219–246). |
| JR TRANREPT / `TRANREPT(+1)` | 133/FB, NEW/CATLG/DELETE. CVTRA07Y report headers/details/totals; comparator uses text (JR 76–80; R 113, 274–374; M 36). |

Fixture input files (M 5–14) are `ACCTDATA.PS` (300), `CARDXREF.PS` (50), `TCATBALF.PS` (50), `DISCGRP.PS` (50, setup only), `TRANTYPE.PS` (60), `TRANCATG.PS` (60), `DALYTRAN.PS.INIT` (350), `DALYTRAN.PS` (350) and `DATEPARM` (80). Expected artifacts are the posted master, account and category KSDS exports, reject and report generations, both COBOL steps' SYSOUT and `rc.json`. Backup and sorted daily generations are JCL intermediates (M 30–39; JR 23–66).

## Recorded cases

| Case | Recorded focus | POSTTRAN STEP15 / TRANREPT STEP10R RC |
|---|---|---|
| [p01-baseline-mixed](../../../fixtures/posting/p01-baseline-mixed) | Four accepted purchases/payment/refund across two accounts; report ordered by card | 0 / 0 |
| [p02-invalid-card](../../../fixtures/posting/p02-invalid-card) | Missing card, reject reason 0100; valid item posts | 4 / 0 |
| [p03-account-missing](../../../fixtures/posting/p03-account-missing) | Card resolves to a missing account, reject reason 0101 | 4 / 0 |
| [p04-overlimit-reject](../../../fixtures/posting/p04-overlimit-reject) | Equality accepted, over-limit reject 0102 | 4 / 0 |
| [p05-expired-account](../../../fixtures/posting/p05-expired-account) | Expired reject 0103, expiry equal to origin date accepted | 4 / 0 |
| [p06-credits-new-category](../../../fixtures/posting/p06-credits-new-category) | Signed negative cycle debits and a newly created category record | 0 / 0 |
| [p07-empty-day](../../../fixtures/posting/p07-empty-day) | No transactions or rejects; recorded report dataset is zero bytes | 0 / 0 |

The p07 README says headers are produced. R 274–279 writes headers on the first detail path, and the [recorded report dataset](../../../fixtures/posting/p07-empty-day/expected/datasets/AWS.M2.CARDDEMO.TRANREPT.G0001V00.txt) is empty. S02 governs parity. The wording gap belongs with the posting rule/fixture owner.

## Standard-by-standard review

**Unchanged** means the shared rule applies as written. **Cite** means the rule applies with a posting-specific contract, mapping or evidence obligation. **Open** marks missing evidence or a target decision. Source ranges establish the legacy facts. Implementation, delivery and testing requirements come from the shared standard and have no COBOL equivalent.

| Standard | Result | Source and posting implication |
|---|---|---|
| S01 | Cite | JP 19–23 describes posting broadly. P 380–420 is the executable validation sequence. Bind future tickets/rules/map to this code and JR 23–80 before implementation. |
| S02 | Unchanged | R 274–279 and the empty p07 golden resolve the README mismatch. Use recorded fields, report, SYSOUT and RC; leave the wording question with the owner. |
| S03 | Unchanged | P 204–216 branches to post or reject, P 380–420 distinguishes reasons, and R 484–511 has fatal lookup outcomes. Use operations and typed outcomes. |
| S04 | Cite | P 66–126 and R 51–113 define record shapes and bindings. The 430-byte reject and 133-byte edited report need separate mappings. |
| S05 | Open | P 440–442 orders three writes per accepted item and R 170–206 has report/card state. Select target transaction, checkpoint and publication boundaries in the posting design. |
| S06 | Unchanged | P 180–190 holds counters/validation and R 127–137 holds report state. Scope those values per run or documented card boundary and inject collaborators. |
| S07 | Cite | P 187, 403–407 and 545–554 use signed receiver arithmetic. R 134–136, 287–288 and 294–298 accumulate at fixed widths. Bind every receiver; interest's division/widths do not describe these programs. |
| S08 | Cite | P 66–97, 714–726, R 633–646 and CVTRA07Y 15–31 provide file/status/report boundaries. Evidence the posting profile and edited decimal output. |
| S09 | Cite | P 425, 436–438, 693–703 copies ID/origin and generates only processing time. JR 43–48, 73–74 and R 122–125, 171–177 supply business date bounds via SORT cards and DATEPARM. Neither EXEC at JP 23 nor JR 59 passes a cycle PARM. |
| S10 | Unchanged | P 407–420 makes 103 overwrite 102 when both validations fail. Give that precedence one named implementation point and test when a rule is assigned. |
| S11 | Open | R 274–279 and p07 disagree with the README wording. JR 29–33, 51–55, 76–80 and P 440–442 give disposition/writes, while surviving partial writes and effective retries need Operations evidence. |
| S12 | Cite | P 227–234, 345–366, 380–399 and 467–495 distinguish EOF, business RC 4 and category creation. R 219–269 and 484–511 distinguish EOF from fatal lookups. Build a posting matrix and preserve its abends and exact log evidence. |
| S13 | Open | P 440–442, 508–564 perform ordered effects with no checkpoint or common commit in the cited source. R 294–314 advances totals and output. Recovery and downstream publication need a posting contract with host evidence. |
| S14 | Unchanged | R 180 displays a full transaction and R 487, 497, 507 display failed keys. P 476–478 exposes a category key. Preserve restricted SYSOUT and keep identifiers and payloads out of ordinary logs. |
| S15 | Unchanged | P 403–420 plus p04/p05 provide separate thresholds. Both-fail precedence and R 281–285 pagination need independent boundary probes beyond the recorded cases. |
| S16 | Open | JP 23–42 and JR 23–80 give real slots, and p01/p02 RC maps and report datasets give oracles. A posting adapter, SQL assertions and PostgreSQL 15 tests are required for target proof. |
| S17 | Unchanged | JP 23 / JR 59 identify the original inputs for the program replay and p07 has a recorded empty report. Read the legacy source and recorded fixtures as frozen oracles. |
| S18 | Open | JR 31 and 78, P 83–89, and R 344–345 expose framing interfaces. Document the future posting architecture, ownership boundaries and dependency grants before taking a ticket. |
| S19 | Unchanged | P 382 and R 180 show card and transaction data sensitivity. Use the supplied synthetic posting fixture material and synthetic probes only. |

## COBOL-behaviour review

| Row | Result | Source and posting implication |
|---|---|---|
| a · Decimal truncation | No fractional truncation shown | P 187, 403–405 uses cents inputs (CVACT01Y and CVTRA06Y) in an add/subtract expression. R 134–136, 287–288 sums cents. No evidenced interest-style fractional receiver; retain evidenced scales. |
| b · Silent loss of high digits | Cite | P 184–187, 403–405, 545–552 and R 129–136, 294–299 have finite receivers without `ON SIZE ERROR` on these operations. Probe boundary vectors with deployed options. P 425 copies the ID and has no calculated wrapping suffix in this path. |
| c · Intermediate COMPUTE precision | Cite | P 403–405 computes credit minus debit plus amount into one receiver. Preserve that evaluation boundary and seek host `ARITH` evidence where width can affect it. No multiply/divide in the expression. |
| d · Unsigned fields / zoned overpunch | Cite | CVTRA05Y 7, 10–11, CVTRA06Y 7, 10–11, P 432, 545–552 distinguish signed amounts and unsigned fields. p06 gives a signed probe. JR 41 uses a 16-byte ZD sort key. |
| e · Packed / binary / compile options | Cite | R 129–132 has internal COMP-3 page/line fields. P 134–148 and R 142–156 have binary result/diagnostic/LE fields. No packed data fields found in the called copybooks. Evidence internal counter semantics and the host status overlay separately. |
| f · Nonnumeric data | No business validation evidenced | P 715 and R 634 test IO-STATUS. P 380–420 and R 363–371 contain no validation of business numeric bytes. Keep the shared input policy and seek host evidence for malformed data. |
| g · Fixed width / MOVE / STRING / slices | Cite | P 414 slices origin date and P 446–451 moves the reject pieces. R 173–174 slices the processing date and R 363–371 moves data to narrower and numeric-edited report pictures. Exact MOVE width and padding apply. |
| h · EBCDIC / ASCII / collation | Cite | P 34–61 uses indexed random keys. JR 41–48 sorts numeric ZD card data and filters CH processing date. R 172–187 reads the result sequentially. Preserve key bytes at indexed boundaries; preserve SORT semantics for report order. |
| i · REDEFINES | Unchanged | P 134–137, 159–174 and R 142–145 use status/format overlays. Reuse the shared adapter guidance with posting offset and formatting tests. |
| j · FILLER / LRECL | Cite | P 66–97 and JR 31, 78 establish framing. CVTRA07Y 15–31, 48–66 has literal filler and edited pictures. P 425–438 assigns transaction fields individually; confirm initialization of the remaining bytes. |
| k · OCCURS / ODO | Absence observed | No declarations in either program or its listed COPY members. P 91–97 and R 122–137 show fixed key/date/working records. |
| l · State across records | Cite | P 206–215 resets validation per row, P 472–499 resets the category-create flag, and R 127–137, 170–206, 294–314 carry card/page/grand state and use the current record area after an EOF read. Probe lab/host state at EOF. |
| m · Control breaks / EOF | Cite | R 179–205 writes prior-card totals on a card change and page/grand totals on the EOF path, with no final account subtotal there. p01 records that shape. In p07 the after-read date predicate at R 173–178 and empty golden also matter. |
| n · Control transfer | Cite | No executable GO TO or PERFORM THRU found, but R 173–178 uses `NEXT SENTENCE`. Trace its target at the period ending the loop at R 206 and test out-of-range/stale-on-EOF paths. P 234 and R 217 return with GOBACK. |
| o · Level-88 | Unchanged | P 142–144, 345–366 and R 150–152, 219–269 use internal 0/16. Actual posting RC 4 comes from RETURN-CODE at P 229–231, not APPL-EOF. |
| p · Indexed access / AIX / duplicates / START / rewrite | Cite | P 34–61, 380–395, 467–542, 545–564 uses primary keys, category creation and keyed rewrites with error handling. R 484–511 uses keyed reference reads. No START or AIX read found in these paths. |
| q · Status and abends | Cite | P 345–366, 467–495, 707–727 and R 219–269, 484–511, 626–646 give different EOF/reject/create/abend outcomes. Test each operation; U999 comes from the CEE3ABD call. |
| r · JCL PARM / GDG / DISP / RC | Cite | JP 23, 34–42 and JR 23–80 specify generations/dispositions and file-fed date input, with no EXEC PARM for either COBOL job. p02 shows RC 4 plus downstream report output. Verify host scheduling and surviving writes separately. |
| s · DISPLAY and parity SYSOUT | Unchanged | P 194, 227–232, 476–478 and R 160, 180, 198–199, 215, 487–507 contain ordered banners, counts and payload/key lines. Capture exact restricted SYSOUT. |
| t · Business date vs wall clock | Cite | P 414, 436–438, 693 and JR 43–48, 73–74 establish expiry vs incoming origin, wall-clock process timestamp and two independent report date gates. |
| u · CURRENT-DATE | Cite | P 150–174, 436–438, 693–703 takes one sample per accepted row, writes only process timestamp, discards offset and appends four zeroes after hundredths. Origin timestamp comes from input. |
| v · Two-digit year / Julian | Absence observed | No Julian/pivot path found in either program or its listed COPY members. P 151, 414, 694 and R 122–125, 173–174 use a four-digit year or fixed ten-character dates. |
| w · Interest fee / filter / category paths | Interest only | This original row lives in the interest annex. P 407–420 applies limit and expiry checks, P 467–542 updates/creates a category, and JR 45–48 / R 173–178 filter by processing date. |

## Proposals for the target architect and source owners

These behaviours lack a concrete shared prescription or need a posting decision. Each proposal is OPEN.

| Proposal | Evidence and decision needed |
|---|---|
| Business rejection and step semantics | P 208–215, 227–232, 380–420, 446–451. Specify recoverable row rejection with persisted original bytes/reason, 0103 precedence when both limits fail, continued processing and a nonzero normal job return. Include downstream report after RC 4 as observed in p02–p05 and confirm the live condition. S12 covers the need for mapping, while it leaves this business contract to the owner. |
| Multiple writes for one accepted transaction | P 254–257 opens the master for output. P 425–442, 467–542 and 545–566 define category create/rewrite, signed account changes and master write order. Decide how to model persistence if a later write fails and what recovery is safe (S05/S13). |
| Sort/merge preparation and two date gates | JR 23–55 vs JR 73–74 and R 168–178. Evidence exact numeric ZD sort, equal-card order, day bounds at both gates and the observed `NEXT SENTENCE` target. Determine whether mismatched dates, invalid representations or stale-on-EOF records need directed goldens. |
| Report state and numeric editing | R 127–137, 179–205, 274–374 and CVTRA07Y 15–66. Specify line-counter page breaks, header/literal padding, signs, subtotal ordering, stale-EOF accumulation and omission of final-card subtotal. p01 detail adds to −10.50 and the recorded grand total is −26.25, a difference equal to the final detail (−15.75). Probe this path, p07 empty output and a multi-page boundary. Open the p07 README wording gap with the fixture owner. |
| Packed counters and INITIALIZE/record bytes | R 129–132, 281–288 and P 503–510. Decide which packed counter edge behaviours and `INITIALIZE` effects need independent probes, including filler retention on rewrite and category creation. Row e guides storage evidence, with no posting-specific boundary values supplied yet. |

## Command availability

The shared legacy estate commands and comparator support posting's recorded chain. The 24-case estate regression includes seven legacy posting cases. There is no posting candidate proof. All interest-only calls or arguments used by the shared workflow and retained from the four original skills are listed here. Generic tools remain reusable with the future stream bindings.

| Source | Bound to interest today |
|---|---|
| Build 1/6 and Pitfall 1 | Printing the pilot/interest branch and checking `java/interest/interest-batch/pom.xml`; the scope diff against `origin/demo/04a-standards` (pilot) or `origin/demo/05b-integration` (combined proof) restricted to `java/interest`; the Maven enforcer validation against that POM. |
| Build 3/4 and Pitfall 5 | `make parity CHAIN=interest` selects only interest. Maven clean verification against the interest POM and its historical offline enforcer attempt exercise the interest candidate. The corresponding logs under `java/interest/evidence/<ticket>/` belong to interest. Java installation, image setup and whole-estate `make parity` are shared. |
| Build 5 | The Python unittest discovery in `java/interest/interest-batch/src/test/resources/pilot/parity`. Posting has no candidate driver unittest path. |
| Verify 1/2 | The historical `01-pilot` evidence initialization/cleanup and the `INTEREST_PILOT_*` Compose invocation of `pilot/parity/run_case.py` on i01–i07. Both the pilot Start-Class and `--java` joined wrapper `scripts/parity/intcalc_java.py` (PropertiesLauncher with the interest main class) are interest-specific. |
| Verify 3/4 | `compare.py interest <i-case> --actual work/<ticket>/<i-case>`, its interest reports/heading count, and the scratch RC-only mismatch reproduction based on the INTCALC manifest. The comparator executable is shared; choose `posting` for posting artifacts. |
| Verify 5 | Pilot run-ID extraction from the interest e2e log and Compose `psql` with `i01-sql.sql`. The interest/`interest_batch` PG15 integration tests and their assertion artifacts also require the interest module. |
| Verify 6/7 | Pilot report paths, replay commit scope and historical raw output references under `java/interest/`. Replace those bindings in the posting ticket workflow after a module exists. |
| Pitfalls 2/3/7/8 | The historical Testcontainers version override was reproduced on the interest POM, the singleton citation is in `PilotPostgresTestSupport.java`, the RC shape comes from the INTCALC driver, and the custom-exit fix is evidenced in `batch/InterestJobConfig.java`. The fixes are described in the shared pitfalls. The generic decoder example runs independently of a stream. |

Posting still needs a rule-ID namespace and register, target dates/arithmetic/file contracts, implementation scope and build settings, a posting candidate adapter for the two COBOL slots and intermediate JCL artifacts, native SQL assertions if required, and a complete seven-case candidate proof.
