\set ON_ERROR_STOP on
SET search_path TO interest_pilot, public;
SELECT current_database() AS database, current_setting('server_version') AS postgres_version, :'run_id' AS run_id;
SELECT emit_ordinal, txn_id, amount, card_number, orig_ts, proc_ts
FROM interest_tx WHERE run_id = :'run_id' ORDER BY emit_ordinal;
SELECT acct_id, old_current_balance, new_current_balance, new_cycle_credit, new_cycle_debit
FROM account_delta WHERE run_id = :'run_id' ORDER BY acct_id;
SELECT count(*) AS tx_rows, sum(amount) AS total FROM interest_tx WHERE run_id = :'run_id';
SELECT a.acct_id, a.current_balance, count(d.acct_id) AS deltas
FROM account_input a LEFT JOIN account_delta d USING (run_id, acct_id)
WHERE a.run_id = :'run_id' GROUP BY a.acct_id, a.current_balance ORDER BY a.acct_id;
SELECT count(*) = 2 AND sum(amount) = 47.00
AND bool_and((emit_ordinal = 1 AND txn_id = '2022071800000001' AND amount = 10.00 AND card_number = '4000000000002001'
 OR emit_ordinal = 2 AND txn_id = '2022071800000002' AND amount = 37.00 AND card_number = '4000000000002002')
 AND orig_ts = '2022-07-05-10.00.00.000000' AND proc_ts = '2022-07-05-10.00.00.000000') AS transactions_pass
FROM interest_tx WHERE run_id = :'run_id';
SELECT count(*) = 1 AND bool_and(acct_id = '00000002001' AND old_current_balance = 1000.00
AND new_current_balance = 1010.00 AND new_cycle_credit = 0.00 AND new_cycle_debit = 0.00) AS delta_pass
FROM account_delta WHERE run_id = :'run_id';
SELECT EXISTS (SELECT 1 FROM account_input WHERE run_id = :'run_id' AND acct_id = '00000002002' AND current_balance = 2400.00)
AND NOT EXISTS (SELECT 1 FROM account_delta WHERE run_id = :'run_id' AND acct_id = '00000002002') AS final_account_unchanged_pass;
