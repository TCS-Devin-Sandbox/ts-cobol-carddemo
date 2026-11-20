import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from run_case import write_manifest


class ManifestTest(unittest.TestCase):
    def test_normal_empty_and_overlength(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            (out / "datasets").mkdir()
            account = out / "datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt"
            transaction = out / "datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt"
            account.write_bytes(b"account  \n")
            transaction.write_bytes(b"")
            runner = Mock()
            runner.resolve_dsn.return_value = (transaction.stem, None)
            entries = {account.stem: {"type": "ksds", "recsize": 12},
                       transaction.stem: {"type": "flat", "lrecl": 17}}
            runner.catalog.entry.side_effect = entries.get
            write_manifest(runner, out)
            manifest = json.loads((out / "manifest.json").read_text())
            self.assertEqual(account.read_bytes(), b"account\n")
            self.assertEqual(manifest[account.stem], {"layout": "CVACT01Y", "lrecl": 12, "records": 1})
            self.assertEqual(manifest[transaction.stem]["records"], 0)
            self.assertEqual(manifest[transaction.stem]["lrecl"], 17)
            entries[transaction.stem] = {}
            write_manifest(runner, out)
            manifest = json.loads((out / "manifest.json").read_text())
            self.assertEqual(manifest[transaction.stem]["lrecl"], 80)
            account.write_bytes(b"x" * 13 + b"\n")
            with self.assertRaises(ValueError):
                write_manifest(runner, out)


if __name__ == "__main__":
    unittest.main()
