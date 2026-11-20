import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import byte_check


class ByteCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.left, self.right = self.root / "left", self.root / "right"

    def test_padding_and_empty_files(self):
        self.left.write_bytes(b"abc\n")
        self.right.write_bytes(b"abc  \n")
        self.assertEqual(1, byte_check.check_pair(self.left, self.right, 5, "sample"))
        self.left.write_bytes(b"")
        self.right.write_bytes(b"")
        self.assertEqual(0, byte_check.check_pair(self.left, self.right, 5, "sample"))

    def test_missing_overlength_and_record_count(self):
        self.left.write_bytes(b"abcdef\n")
        with self.assertRaisesRegex(ValueError, "record=0 byte_offset=5"):
            byte_check.records(self.left, 5)
        self.left.write_bytes(b"abc\nabc\n")
        self.right.write_bytes(b"abc\n")
        with self.assertRaisesRegex(ValueError, "record=1 byte_offset=0"):
            byte_check.check_pair(self.left, self.right, 5, "sample")
        self.right.unlink()
        with self.assertRaises(FileNotFoundError):
            byte_check.check_pair(self.left, self.right, 5, "sample")

    def test_cli_mismatch_is_nonzero_with_case_file_record_and_offset(self):
        case = "i01-baseline"
        expected = self.root / "fixtures/interest" / case / "expected"
        actual = self.root / "actual" / case
        (expected / "datasets").mkdir(parents=True)
        (actual / "datasets").mkdir(parents=True)
        (expected / "manifest.json").write_text(json.dumps({Path(byte_check.ACCOUNT).name[:-4]: {"records": 1}}))
        (expected / byte_check.ACCOUNT).write_bytes(b"abc\n")
        (actual / byte_check.ACCOUNT).write_bytes(b"axc\n")
        result = subprocess.run([sys.executable, byte_check.__file__, "--root", str(self.root),
                                 "--actual-root", str(self.root / "actual"), "--case", case],
                                capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        for diagnostic in ("case=i01-baseline", "file=datasets/", "record=0", "byte_offset=1"):
            self.assertIn(diagnostic, result.stderr)

    def test_signed_zoned_golden_amounts(self):
        self.assertEqual("12.35", str(byte_check.zoned(b"0000000123E")))
        self.assertEqual("-3.00", str(byte_check.zoned(b"0000000030}")))
