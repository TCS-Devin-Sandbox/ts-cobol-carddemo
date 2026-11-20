#!/usr/bin/env python3
"""Launch the complete shadow candidate in place of INTCALC STEP15 only."""
import os
import sys

JAVA = "/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
MAIN = "com.carddemo.interest.batch.InterestBatchApplication"


def command(arguments):
    if len(arguments) < 2 or arguments[0] != "-jar":
        raise ValueError("expected -jar JAR followed by job properties")
    translated = [arg.replace("--interest.pilot.", "--interest.close.", 1)
                  if arg.startswith("--interest.pilot.") else arg
                  for arg in arguments[2:]]
    return [JAVA, "-cp", arguments[1], f"-Dloader.main={MAIN}",
            "org.springframework.boot.loader.launch.PropertiesLauncher", *translated]


if __name__ == "__main__":
    try:
        argv = command(sys.argv[1:])
    except ValueError as error:
        print(str(error), file=sys.stderr)
        sys.exit(12)
    os.execv(JAVA, argv)
