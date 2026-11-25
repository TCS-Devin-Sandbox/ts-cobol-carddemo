# Generated from workbench/technical_summary/business_rules.md (approved Stage 2)
# and workbench/target_design/testing_requirements.md (approved Stage 3).
Feature: READCARD / STEP05 / CBACT02C card master listing in PySpark

  Background:
    Given the approved READCARD PySpark configuration

  # ---------------------------------------------------------------- BR-001
  @BR-001 @TS-01 @positive @parity
  Scenario: Every card in the populated golden master is listed
    Given the golden fixture case "r01-populated"
    When the PySpark application processes the card master
    Then the return code is 0
    And the listing is identical to the legacy golden SYSOUT
    And the listing has one detail line per input record

  @BR-001 @boundary
  Scenario: A single-card master lists exactly one detail line
    Given the test data set "single_record"
    When the PySpark application processes the card master
    Then the return code is 0
    And the listing has 1 detail lines

  @BR-001 @volume
  Scenario: A large card master lists every record
    Given a generated card master of 20000 unordered records
    When the PySpark application processes the card master
    Then the return code is 0
    And the listing has 20000 detail lines
    And the detail lines are in ascending EBCDIC card-number order

  # ---------------------------------------------------------------- BR-002
  @BR-002 @TS-03 @positive
  Scenario: Unordered input is listed in ascending card-number order
    Given the test data set "unordered"
    When the PySpark application processes the card master
    Then the detail lines are in ascending EBCDIC card-number order
    And the first detail card number is "4000000000000001"

  @BR-002 @VR-12 @boundary
  Scenario: Card numbers containing letters follow KSDS EBCDIC collation
    Given the test data set "ebcdic_mixed_keys"
    When the PySpark application processes the card master
    Then the detail card numbers are in this order:
      | card_num         |
      | A000000000000001 |
      | Z000000000000001 |
      | 0000000000000001 |
      | 4000000000000001 |
      | 9000000000000001 |

  # ---------------------------------------------------------------- BR-003
  @BR-003 @TS-04 @positive
  Scenario: Trimmed input records are emitted as 150-character images
    Given the test data set "trimmed_records"
    When the PySpark application processes the card master
    Then every detail line is 150 characters long
    And every detail line equals its input record padded to 150 characters

  @BR-003 @boundary
  Scenario: A full 150-character record is emitted unchanged
    Given the test data set "exact_150"
    When the PySpark application processes the card master
    Then the return code is 0
    And every detail line equals its input record padded to 150 characters

  @BR-003 @TS-08 @positive
  Scenario: Card number and CVV are emitted in clear text exactly as legacy
    Given the golden fixture case "r01-populated"
    When the PySpark application processes the card master
    Then every CVACT02Y field of every detail line equals the legacy SYSOUT field

  # ---------------------------------------------------------------- BR-004
  @BR-004 @positive
  Scenario: The listing is framed by the CBACT02C banners
    Given the golden fixture case "r01-populated"
    When the PySpark application processes the card master
    Then the first listing line is "START OF EXECUTION OF PROGRAM CBACT02C"
    And the last listing line is "END OF EXECUTION OF PROGRAM CBACT02C"

  # ---------------------------------------------------------------- BR-005
  @BR-005 @TS-02 @boundary @parity
  Scenario: An empty card master completes successfully
    Given the golden fixture case "r02-empty"
    When the PySpark application processes the card master
    Then the return code is 0
    And the listing is identical to the legacy golden SYSOUT
    And the listing has 0 detail lines

  # ---------------------------------------------------------------- BR-006 / TR-002 / TR-004
  @BR-006 @TR-002 @TR-004 @TS-05 @negative
  Scenario: A missing card master abends with the open error
    Given a card master directory with no card dataset
    When the PySpark application processes the card master
    Then the return code is 12
    And the listing is exactly:
      | line                                   |
      | START OF EXECUTION OF PROGRAM CBACT02C |
      | ERROR OPENING CARDFILE                 |
      | FILE STATUS IS: NNNN0035               |
      | ABENDING PROGRAM                       |
      | CEE3ABD: USER ABEND U+000000999        |
    And the run metrics record abend code 999 and status "ABEND"

  @BR-006 @V-01 @TS-06 @negative
  Scenario: An over-length record abends with the read error before any detail line
    Given the test data set "over_length"
    When the PySpark application processes the card master
    Then the return code is 12
    And the listing is exactly:
      | line                                   |
      | START OF EXECUTION OF PROGRAM CBACT02C |
      | ERROR READING CARDFILE                 |
      | FILE STATUS IS: NNNN0034               |
      | ABENDING PROGRAM                       |
      | CEE3ABD: USER ABEND U+000000999        |

  @BR-006 @V-02 @TS-07 @negative
  Scenario: Duplicate card numbers abend with the read error before any detail line
    Given the test data set "duplicate_keys"
    When the PySpark application processes the card master
    Then the return code is 12
    And the listing is exactly:
      | line                                   |
      | START OF EXECUTION OF PROGRAM CBACT02C |
      | ERROR READING CARDFILE                 |
      | FILE STATUS IS: NNNN0022               |
      | ABENDING PROGRAM                       |
      | CEE3ABD: USER ABEND U+000000999        |

  # Close status '90' (source_to_target_mapping.md) is shown via the 9910 binary branch:
  # '9' + ord('0') = 9048 off-host (ASCII); z/OS EBCDIC would show 9240.
  @BR-006 @TR-002 @TR-003 @negative
  Scenario: A failure while closing the listing abends with the close error
    Given the golden fixture case "r01-populated"
    And closing the listing fails
    When the PySpark application processes the card master
    Then the return code is 12
    And the listing has 2 detail lines
    And the last listing lines are:
      | line                            |
      | ERROR CLOSING CARDFILE          |
      | FILE STATUS IS: NNNN9048        |
      | ABENDING PROGRAM                |
      | CEE3ABD: USER ABEND U+000000999 |

  # ---------------------------------------------------------------- TR-001
  @TR-001 @positive
  Scenario: End of file ends the read loop normally
    Given the golden fixture case "r01-populated"
    When the PySpark application processes the card master
    Then the return code is 0
    And the run metrics record status "SUCCESS" with no abend code

  # ---------------------------------------------------------------- TR-003
  @TR-003 @VR-09 @boundary
  Scenario Outline: File status values are displayed in the CBACT02C format
    When the file status "<status>" is formatted
    Then the displayed status is "<display>"

    Examples:
      | status    | display |
      | 35        | 0035    |
      | 34        | 0034    |
      | 22        | 0022    |
      | 9<x04>    | 9004    |
      | 9<x22>    | 9034    |
      | 90        | 9048    |

  # ---------------------------------------------------------------- TR-005
  @TR-005 @VR-10 @positive
  Scenario: The card master is opened read-only
    Given the test data set "unordered"
    When the PySpark application processes the card master
    Then the card master file is unchanged

  # ---------------------------------------------------------------- TR-006
  @TR-006 @VR-11 @positive
  Scenario: The listing carries no count or trailer lines
    Given the test data set "unordered"
    When the PySpark application processes the card master
    Then the listing line count is the input record count plus 2

  # ---------------------------------------------------------------- reconciliation / OQ-04
  @reconciliation @positive
  Scenario: Run metrics reconcile with the legacy SYSOUT
    Given the golden fixture case "r01-populated"
    When the PySpark application processes the card master
    Then input_records equals output_detail_lines equals the legacy detail line count
    And records_sha256 equals the SHA-256 of the legacy detail lines
    And min_card_num and max_card_num equal the first and last legacy card numbers

  @OQ-04 @positive
  Scenario: A Parquet card master produces the same listing as the fixed-width master
    Given the golden fixture case "r01-populated" converted to Parquet
    When the PySpark application processes the card master
    Then the return code is 0
    And the listing is identical to the legacy golden SYSOUT
