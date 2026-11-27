Feature: READACCT / CBACT01C - read the account master and write the account extract files
  Traceability: testRepo/analysis/BRE/CBACT01C.json, testRepo/analysis/TargetSpec/READACCT_target_spec.json

  # CBACT01C-BR-001, CBACT01C-BR-007
  Scenario: Every account is printed in ACCT-ID order between the banners
    Given the account file "accts_unsorted.txt" in "text" format
    When READACCT runs
    Then STEP05 ends with return code 0
    And SYSOUT holds 3 account blocks in ascending ACCT-ID order
    And every account block ends with its 300-byte account record
    And SYSOUT starts with "START OF EXECUTION OF PROGRAM CBACT01C"
    And SYSOUT contains the end banner

  # CBACT01C-BR-001 (EBCDIC / Cobrix path)
  Scenario: The EBCDIC account file gives the same output as its text form
    Given the account file "accts_unsorted.ebcdic" in "ebcdic" format
    When READACCT runs
    Then STEP05 ends with return code 0
    And the outputs equal those of "accts_unsorted.txt" in "text" format

  # CBACT01C-BR-002
  Scenario: Each account produces an extract record with the reissue date as YYYYMMDD
    Given the account file "accts_unsorted.txt" in "text" format
    When READACCT runs
    Then PSCOMP holds 3 records of 107 bytes
    And PSCOMP record 1 has reissue date "20250131" and group id "GOLD"
    And PSCOMP record 3 has current balance -42.50

  # CBACT01C-BR-003
  Scenario: Zero cycle debit becomes 2525.00 and a non-zero debit keeps the previous record's value
    Given the account file "accts_unsorted.txt" in "text" format
    When READACCT runs
    Then the PSCOMP cycle debits are "2525.00, 2525.00, 2525.00"

  # CBACT01C-BR-003 (legacy-recorded synthetic case)
  Scenario: Cycle-debit carry matches the legacy GnuCOBOL run
    Given the synthetic legacy case "readacct_quirks"
    When READACCT runs
    Then the PSCOMP cycle debits are "binary zeros, 2525.00, 2525.00, 2525.00"
    And datasets and SYSOUT equal the legacy-recorded case

  # CBACT01C-BR-004
  Scenario: Each account produces a 5-entry balance array record
    Given the account file "accts_unsorted.txt" in "text" format
    When READACCT runs
    Then ARRYPS holds 3 records of 110 bytes
    And ARRYPS record 1 entries are "1234.56/1005.00, 1234.56/1525.00, -1025.00/-2500.00, 0/0, 0/0"

  # CBACT01C-BR-005
  Scenario: Each account produces a 12-byte and a 39-byte variable record
    Given the account file "accts_unsorted.txt" in "text" format
    When READACCT runs
    Then VBPS holds 6 records alternating 12 and 39 bytes
    And VBPS record 2 is "1000000000100000012345F00000050000{2025"

  # CBACT01C-BR-006
  Scenario: An over-length account record stops the run with abend 999
    Given the account file "accts_bad_record.txt" in "text" format
    When READACCT runs
    Then STEP05 ends with return code "U0999"
    And SYSOUT holds 1 account blocks before "ERROR READING ACCOUNT FILE" then "ABENDING PROGRAM"
    And SYSOUT does not contain the end banner

  # CBACT01C-BR-006 (EBCDIC unload ends inside a record)
  Scenario: Complete accounts are processed before a truncated EBCDIC record abends the run
    Given the account file "accts_truncated.ebcdic" in "ebcdic" format
    When READACCT runs
    Then STEP05 ends with return code "U0999"
    And SYSOUT holds 3 account blocks before "ERROR READING ACCOUNT FILE" then "ABENDING PROGRAM"

  # CBACT01C-BR-006 (open failure)
  Scenario: A missing account file stops the run with abend 999
    Given the account file "does_not_exist.txt" in "text" format
    When READACCT runs
    Then STEP05 ends with return code "U0999"
    And SYSOUT ends with "ERROR OPENING ACCTFILE" then "ABENDING PROGRAM"

  # Parity with the legacy golden files
  Scenario Outline: Output matches the read-masters golden files
    Given the read-masters account fixture "<case>"
    When READACCT runs
    Then tools/parity/compare.py passes for READACCT.PREDEL and READACCT.STEP05

    Examples:
      | case          |
      | r01-populated |
      | r02-empty     |
