import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from tools.runjcl import runjcl
from tools import statements_prep
from tools.chaingraph import gen_chain_graph


class StatementSortTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.runner = object.__new__(runjcl.Runner)
        self.runner.catalog = object.__new__(runjcl.Catalog)
        self.runner.catalog.data = {"datasets": {}, "paths": {}, "gdg": {}}
        self.runner.outputs = []
        self.runner.catalog.set("INPUT", type="flat", lrecl=8)
        self.runner.catalog.set("OUTPUT", type="flat", lrecl=10)
        self.infos = {"SORTIN": {"dsn": "INPUT"}, "SORTOUT": {"dsn": "OUTPUT"}}
        self.env = {f"DD_{name}": str(self.directory / name) for name in ("SYSIN", "SORTIN", "SORTOUT")}
        Path(self.env["DD_SYSIN"]).write_text(
            "SORT FIELDS=(5,2,CH,A,1,2,CH,A)\n"
            "OUTREC FIELDS=(1:5,2,3:1,4,7:7,2)\n")
        self.step = runjcl.Step("SORT", "SORT", None, None)

    def run_sort(self, data):
        Path(self.env["DD_SORTIN"]).write_bytes(data)
        self.runner.sort(self.step, self.env, self.infos, self.directory)
        return Path(self.env["DD_SORTOUT"]).read_bytes()

    def test_composite_sort_outrec_and_padding(self):
        self.assertEqual(self.run_sort(b"02xxBBzz03yyAAqq01wwBBrr"),
                         b"AA03yyqq  BB01wwrr  BB02xxzz  ")
        self.assertEqual(self.runner.catalog.lrecl("OUTPUT"), 10)

    def test_empty_records(self):
        self.assertEqual(self.run_sort(b""), b"")
        self.assertIn("0 RECORDS OUT, LRECL 10", (self.directory / "SYSOUT.txt").read_text())

    def test_invalid_outrec_field(self):
        Path(self.env["DD_SYSIN"]).write_text("SORT FIELDS=COPY\nOUTREC FIELDS=(1:7,5)\n")
        with self.assertRaisesRegex(runjcl.JclError, "outside record"):
            self.run_sort(b"01wwBBrr")

    def test_outrec_exceeds_output(self):
        Path(self.env["DD_SYSIN"]).write_text("SORT FIELDS=COPY\nOUTREC FIELDS=(9:1,4)\n")
        with self.assertRaisesRegex(runjcl.JclError, "exceeds SORTOUT"):
            self.run_sort(b"01wwBBrr")

    def test_ksds_input_is_unloaded(self):
        self.runner.catalog.set("INPUT", type="ksds", recsize=8)

        def unload(argv, env, quiet):
            Path(env["DD_FLATFILE"]).write_bytes(b"01wwBBrr")
            return 0

        self.runner._cobcrun = Mock(side_effect=unload)
        with patch.object(runjcl, "ksds_module", return_value="UNLOAD"):
            self.assertEqual(self.run_sort(b"not a flat file"), b"BB01wwrr  ")
        self.runner._cobcrun.assert_called_once()

    def test_ksds_unload_error(self):
        self.runner.catalog.set("INPUT", type="ksds", recsize=8)
        self.runner._cobcrun = Mock(return_value=12)
        with patch.object(runjcl, "ksds_module", return_value="UNLOAD"):
            with self.assertRaisesRegex(runjcl.JclError, "SORT unload of INPUT failed"):
                self.run_sort(b"")


class StatementPreparationTests(unittest.TestCase):
    def test_only_tiot_traversal_is_removed(self):
        original_root = statements_prep.ROOT
        original = (original_root / "app/cbl/CBSTM03A.CBL").read_text()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for member in ("app/cbl/CBSTM03A.CBL", "app/cbl/CBSTM03B.CBL",
                           "app/cpy/COSTM01.CPY", "app/cpy/CUSTREC.cpy"):
                target = root / member
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((original_root / member).read_bytes())
            with patch.object(statements_prep, "ROOT", root):
                statements_prep.prepare()
            prepared = (root / "work/statements/CBSTM03A.cbl").read_text()
            start = original.index("           SET ADDRESS OF PSA-BLOCK")
            end = original.index("           OPEN OUTPUT STMT-FILE", start)
            self.assertEqual(prepared, original[:start] + original[end:])
            for member in ("app/cbl/CBSTM03A.CBL", "app/cbl/CBSTM03B.CBL",
                           "app/cpy/COSTM01.CPY", "app/cpy/CUSTREC.cpy"):
                self.assertEqual((root / member).read_bytes(), (original_root / member).read_bytes())
            self.assertNotIn("\t", (root / "work/cpy/CUSTREC.cpy").read_text())
            self.assertEqual((root / "work/cpy/COSTM01.cpy").read_bytes(),
                             (root / "app/cpy/COSTM01.CPY").read_bytes())

    def test_dd_comments_and_quoted_blanks(self):
        statements = runjcl.read_statements([
            "//REPORT DD DSN='A B',",
            "// SPACE=(CYL,(1,1),RLSE), stray text with )",
            "// DISP=NEW",
        ])
        self.assertEqual(statements[0][2], "DSN='A B',SPACE=(CYL,(1,1),RLSE),DISP=NEW")

    def test_creastmt_statement_dd(self):
        job = runjcl.Job(runjcl.find_jcl("CREASTMT"))
        self.assertEqual(job.steps[-1].dds["STMTFILE"].params["DSN"],
                         "AWS.M2.CARDDEMO.STATEMNT.PS")

    def test_graph_includes_uppercase_jcl_and_statement_output(self):
        steps = gen_chain_graph.parse_jobs()["CREASTMT"]["steps"]
        self.assertEqual(steps[-1]["program"], "CBSTM03A")
        self.assertIn(("STMTFILE", "AWS.M2.CARDDEMO.STATEMNT.PS"), steps[-1]["dd"])


if __name__ == "__main__":
    unittest.main()
