\set ON_ERROR_STOP on
\echo SQL case=:case run=:run_id
CREATE TEMP TABLE gate_assertions AS
SELECT
 (SELECT count(*) = 1 FROM interest.run WHERE run_id=:'run_id'::uuid AND state='COMPUTED' AND mode='shadow') AS run_pass,
 (SELECT count(*) = :tx_count FROM interest.interest_tx WHERE run_id=:'run_id'::uuid) AS tx_count_pass,
 (SELECT count(*) = :delta_count FROM interest.account_delta WHERE run_id=:'run_id'::uuid) AS delta_count_pass,
 (SELECT coalesce(sum(amount),0) = :tx_sum FROM interest.interest_tx WHERE run_id=:'run_id'::uuid) AS tx_sum_pass,
 (SELECT coalesce(sum(new_current_balance-old_current_balance),0) = :delta_sum FROM interest.account_delta
   WHERE run_id=:'run_id'::uuid) AS delta_sum_pass,
 NOT EXISTS(SELECT 1 FROM interest.account_delta WHERE run_id=:'run_id'::uuid AND acct_id=:'last_account') AS last_account_pass,
 (SELECT count(*) = 1 AND bool_and(state='PRIVATE' AND destination LIKE 'shadow:%') FROM interest.publication
   WHERE run_id=:'run_id'::uuid) AS publication_pass,
 NOT EXISTS(SELECT 1 FROM interest.run r LEFT JOIN interest.publication p USING(run_id)
   WHERE r.run_id=:'run_id'::uuid AND (r.state='RELEASED' OR p.state='RECEIVED')) AS no_release_pass;
TABLE gate_assertions;
SELECT run_pass AND tx_count_pass AND delta_count_pass AND tx_sum_pass AND delta_sum_pass AND last_account_pass
  AND publication_pass AND no_release_pass AS all_pass FROM gate_assertions \gset
\if :all_pass
\echo SQL PASS case=:case tx=:tx_count deltas=:delta_count tx_sum=:tx_sum delta_sum=:delta_sum
\else
\echo SQL FAIL case=:case run=:run_id
DO $$ BEGIN RAISE EXCEPTION 'Gate SQL assertion failed'; END $$;
\endif
