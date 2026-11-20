\set ON_ERROR_STOP on
\echo INT-04 i01-baseline run_id=:run_id database=interest_close
SET search_path TO interest, public;

SELECT current_database() = 'interest_close' AS private_database_pass;

SELECT count(*) = 1 AND bool_and(r.state = 'COMPUTED' AND s.state = 'SEALED'
  AND s.codec_profile = 'LAB_ASCII' AND r.mode = 'shadow' AND r.parm = '2022071800') AS run_snapshot_pass
FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = :'run_id';

SELECT count(*) = 2 AND sum(amount) = 47.00
  AND bool_and((emit_ordinal = 1 AND txn_id = '2022071800000001' AND amount = 10.00
      AND card_number = '4000000000002001'
    OR emit_ordinal = 2 AND txn_id = '2022071800000002' AND amount = 37.00
      AND card_number = '4000000000002002')
    AND orig_ts = '2022-07-05-10.00.00.000000' AND proc_ts = orig_ts) AS transactions_pass
FROM interest_tx WHERE run_id = :'run_id';

SELECT count(*) = 1 AND bool_and(acct_id = '00000002001' AND old_current_balance = 1000.00
  AND new_current_balance = 1010.00 AND new_cycle_credit = 0.00 AND new_cycle_debit = 0.00) AS delta_pass
FROM account_delta WHERE run_id = :'run_id';

SELECT EXISTS (SELECT 1 FROM account_input a JOIN interest.run r USING (snapshot_id)
  WHERE r.run_id = :'run_id' AND a.acct_id = '00000002002' AND a.current_balance = 2400.00)
  AND NOT EXISTS (SELECT 1 FROM account_delta WHERE run_id = :'run_id' AND acct_id = '00000002002')
  AS final_account_unchanged_pass;

SELECT count(*) = 1 AND bool_and(next_legacy_key IS NULL AND last_ordinal = 2
  AND last_acct_id = '00000002002') AS checkpoint_pass
FROM run_checkpoint WHERE run_id = :'run_id';

SELECT count(*) = 1 AND bool_and((SELECT count(*) FROM account_input a WHERE a.snapshot_id = s.snapshot_id)
  = (s.manifest->'ACCTDATA'->>'count')::bigint) AS account_input_manifest_count_pass
FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = :'run_id';

SELECT count(*) = 1 AND bool_and((SELECT count(*) FROM card_xref_input a WHERE a.snapshot_id = s.snapshot_id)
  = (s.manifest->'CARDXREF'->>'count')::bigint) AS xref_input_manifest_count_pass
FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = :'run_id';

SELECT count(*) = 1 AND bool_and((SELECT count(*) FROM category_balance_input a WHERE a.snapshot_id = s.snapshot_id)
  = (s.manifest->'TCATBALF'->>'count')::bigint) AS category_input_manifest_count_pass
FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = :'run_id';

SELECT count(*) = 1 AND bool_and((SELECT count(*) FROM disclosure_rate_input a WHERE a.snapshot_id = s.snapshot_id)
  = (s.manifest->'DISCGRP'->>'count')::bigint) AS disclosure_input_manifest_count_pass
FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = :'run_id';
