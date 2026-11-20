import unittest

from intcalc_java import JAVA, MAIN, command


class CandidateCommandTest(unittest.TestCase):
    def test_translates_only_pilot_property_prefix(self):
        self.assertEqual([
            JAVA, "-cp", "candidate.jar", f"-Dloader.main={MAIN}",
            "org.springframework.boot.loader.launch.PropertiesLauncher",
            "--interest.close.run-on-startup=true", "--interest.close.input-dir=/a b",
            "--interest.close.output-dir=/out", "--interest.close.clock=2022-07-05T10:00:00.00",
            "--other=--interest.pilot.keep", "--interest.close.parm=2022071800",
        ], command(["-jar", "candidate.jar", "--interest.pilot.run-on-startup=true",
                    "--interest.pilot.input-dir=/a b", "--interest.pilot.output-dir=/out",
                    "--interest.pilot.clock=2022-07-05T10:00:00.00",
                    "--other=--interest.pilot.keep", "--interest.close.parm=2022071800"]))

    def test_empty_properties(self):
        self.assertEqual(5, len(command(["-jar", "candidate.jar"])))

    def test_rejects_invalid_invocation(self):
        for arguments in ([], ["-jar"], ["-cp", "candidate.jar"]):
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                command(arguments)
