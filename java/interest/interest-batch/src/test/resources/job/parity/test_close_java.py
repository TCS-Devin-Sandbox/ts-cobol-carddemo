import unittest
from close_java import JAVA, MAIN, command


class LauncherTest(unittest.TestCase):
    def test_INT_R10_translation_and_exit_passthrough_exec_contract(self):
        self.assertEqual(command(["-jar", "/a jar.jar", "--interest.pilot.run-on-startup=true",
                                  "--interest.pilot.input-dir=/input path",
                                  "--interest.pilot.output-dir=/output",
                                  "--interest.pilot.clock=2022-07-05T10:00:00.00"]),
                         [JAVA, "-cp", "/a jar.jar", f"-Dloader.main={MAIN}",
                          "org.springframework.boot.loader.launch.PropertiesLauncher",
                          "--interest.close.run-on-startup=true",
                          "--interest.close.input-dir=/input path",
                          "--interest.close.output-dir=/output",
                          "--interest.close.clock=2022-07-05T10:00:00.00"])

    def test_empty_or_invalid_launch_is_rejected(self):
        for args in ([], ["-jar"], ["-cp", "jar"]):
            with self.assertRaises(ValueError):
                command(args)


if __name__ == "__main__":
    unittest.main()
