import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.parity import compare, shadow


class ExpectedOverrideTest(unittest.TestCase):
    def test_override_and_default_and_missing_output(self):
        source = compare.FIXTURES / "interest/i01-baseline/expected"
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "report.md"
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, compare.run_comparison("interest", "i01-baseline", source, report, "test"))
                self.assertEqual(0, compare.run_comparison("external", "nightly", source, report, "test", expected=source))
                self.assertEqual(1, compare.run_comparison("external", "nightly", Path(tmp), report, "test", expected=source))

    def test_empty_and_missing_expected(self):
        with tempfile.TemporaryDirectory() as tmp:
            expected = Path(tmp)
            (expected / "manifest.json").write_text("{}")
            (expected / "rc.json").write_text("{}")
            self.assertEqual([], compare.compare_case("external", "empty", expected, expected=expected)[0])
            with self.assertRaises(SystemExit):
                compare.compare_case("external", "missing", expected, expected=expected / "missing")


class ShadowReportTest(unittest.TestCase):
    def run_shadow(self, root, drop):
        with patch.object(shadow, "ROOT", root), patch.object(shadow.zipfile, "ZipFile"), \
                patch("sys.argv", ["shadow", "--drop", str(drop), "--run-id", "shadow_test"]), \
                contextlib.redirect_stdout(io.StringIO()):
            result = shadow.main()
        return result, json.loads((root / "work/shadow/shadow_test/drift.json").read_text())

    def test_empty_drop_is_drift(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "tools/runjcl").mkdir(parents=True)
            (root / "tools/runjcl/chains.json").write_text("{}")
            result, report = self.run_shadow(root, root / "missing")
            self.assertEqual(1, result)
            self.assertEqual("DRIFT", report["status"])
            self.assertEqual([], report["cases"])

    def test_invalid_case_still_writes_error_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "tools/runjcl").mkdir(parents=True)
            (root / "tools/runjcl/chains.json").write_text("{}")
            (root / "drop/posting/bad").mkdir(parents=True)
            result, report = self.run_shadow(root, root / "drop")
            self.assertEqual(1, result)
            self.assertEqual("ERROR", report["cases"][0]["verdict"])
            self.assertIsNone(report["cases"][0]["chain_exit"])


if __name__ == "__main__":
    unittest.main()
