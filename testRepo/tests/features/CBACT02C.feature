Feature: READCARD / CBACT02C - read and print card data file
  Traceability: testRepo/analysis/BRE/CBACT02C.json, testRepo/analysis/TargetSpec/READCARD_target_spec.json

  # CBACT02C-BR-001
  Scenario: Every card record is printed in CARD-NUM order
    Given the card data file "cards_unsorted.txt" in "text" format
    When READCARD runs
    Then STEP05 ends with return code 0
    And SYSOUT holds 3 card lines in ascending CARD-NUM order
    And every card line is 150 characters long

  # CBACT02C-BR-001 (EBCDIC / Cobrix path)
  Scenario: EBCDIC card records are printed exactly as their decoded 150 bytes
    Given the card data file "cards_unsorted.ebcdic" in "ebcdic" format
    When READCARD runs
    Then STEP05 ends with return code 0
    And SYSOUT holds 3 card lines in ascending CARD-NUM order
    And every card line equals the cp037 decode of its input record

  # CBACT02C-BR-002
  Scenario: An unreadable card record stops the run with abend 999
    Given the card data file "cards_bad_record.txt" in "text" format
    When READCARD runs
    Then STEP05 ends with return code "U0999"
    And SYSOUT ends with "ERROR READING CARDFILE" then "ABENDING PROGRAM"
    And SYSOUT does not contain the end banner

  # CBACT02C-BR-002 (open failure)
  Scenario: A missing card data file stops the run with abend 999
    Given the card data file "does_not_exist.txt" in "text" format
    When READCARD runs
    Then STEP05 ends with return code "U0999"
    And SYSOUT ends with "ERROR OPENING CARDFILE" then "ABENDING PROGRAM"

  # CBACT02C-BR-003
  Scenario: The listing is bracketed by the start and end banners
    Given the card data file "cards_unsorted.txt" in "text" format
    When READCARD runs
    Then SYSOUT starts with "START OF EXECUTION OF PROGRAM CBACT02C"
    And SYSOUT ends with "END OF EXECUTION OF PROGRAM CBACT02C"

  # Reconciliation against golden fixtures
  Scenario Outline: SYSOUT and return codes match the legacy golden files
    Given the read-masters fixture "<case>"
    When READCARD runs
    Then SYSOUT equals the fixture's expected READCARD.STEP05 sysout
    And the return codes equal the fixture's expected READCARD rc

    Examples:
      | case          |
      | r01-populated |
      | r02-empty     |
