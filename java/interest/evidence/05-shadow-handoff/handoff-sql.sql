-- INT-05 SQL assertions, executed by HandoffIT after every test (PostgreSQL 15, interest schema, handoff as
-- SET ROLE interest_publish). Output lines "SQL ..." are in handoff-fault-injection.txt / test-output.txt.
-- 1. No RELEASED run without a RECEIVED publication carrying a verified receipt.
SELECT count(*) AS released_without_verified_receipt FROM interest.run r LEFT JOIN interest.publication p USING (run_id)
WHERE r.state = 'RELEASED' AND (p.state IS DISTINCT FROM 'RECEIVED' OR (p.receipt->>'verified') IS DISTINCT FROM 'true');
-- 2. One live publication claim per destination/cycle.
SELECT count(*) AS live_claims_per_destination_cycle_gt1 FROM (SELECT p.destination, r.cycle FROM interest.publication p
  JOIN interest.run r USING (run_id) WHERE r.mode = 'live' GROUP BY 1, 2 HAVING count(*) > 1) d;
-- 3. At most one publication record per run (also the table's primary key).
SELECT count(*) AS publication_rows_for_run FROM interest.publication WHERE run_id = :'run_id';
