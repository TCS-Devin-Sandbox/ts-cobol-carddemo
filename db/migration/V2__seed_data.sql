-- Source: CREADB21.jcl steps LDTTYPE/RUNTEP2 (ctl/DB2LTTYP.ctl) and LDTCCAT (ctl/DB2LTCAT.ctl).
-- Values copied verbatim, including the source spelling 'REVERAL'.

INSERT INTO transaction_type (tr_type, tr_description) VALUES
    ('01', 'PURCHASE'),
    ('02', 'PAYMENT'),
    ('03', 'CREDIT'),
    ('04', 'AUTHORIZATION'),
    ('05', 'REFUND'),
    ('06', 'REVERAL'),
    ('07', 'ADJUSTMENT');

INSERT INTO transaction_type_category (trc_type_code, trc_type_category, trc_cat_data) VALUES
    ('01', '0001', 'REGULAR SALES DRAFT'),
    ('01', '0002', 'REGULAR CASH ADVANCE'),
    ('01', '0003', 'CONVENIENCE CHECK DEBIT'),
    ('01', '0004', 'ATM CASH ADVANCE'),
    ('01', '0005', 'INTEREST AMOUNT'),
    ('02', '0001', 'CASH PAYMENT'),
    ('02', '0002', 'ELECTRONIC PAYMENT'),
    ('02', '0003', 'CHECK PAYMENT'),
    ('03', '0001', 'CREDIT TO ACCOUNT'),
    ('03', '0002', 'CREDIT TO PURCHASE BALANCE'),
    ('03', '0003', 'CREDIT TO CASH BALANCE'),
    ('04', '0001', 'ZERO DOLLAR AUTHORIZATION'),
    ('04', '0002', 'ONLINE PURCHASE AUTHORIZATION'),
    ('04', '0003', 'TRAVEL BOOKING AUTHORIZATION'),
    ('05', '0001', 'REFUND CREDIT'),
    ('06', '0001', 'FRAUD REVERSAL'),
    ('06', '0002', 'NON FRAUD REVERSAL'),
    ('07', '0001', 'SALES DRAFT CREDIT ADJUSTMENT');
