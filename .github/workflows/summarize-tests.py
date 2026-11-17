#!/usr/bin/env python3
"""Summarise JUnit XML test results for the Verify Tests workflow.

Reads app/build/test-results/testDebugUnitTest/*.xml and prints:
  - Tests run: <total>, failed: <failures>
  - the failing test names, or "- No failing tests."

Kept as a file (not a YAML heredoc): a heredoc terminator indented inside a
`run: |` block is never at column 0 after YAML dedent, so bash never finds
it and the summary step fails with a here-document syntax error even when
every test passes. Calling this file avoids that entire failure class.
"""

import glob
import xml.etree.ElementTree as ET

total = failures = 0
failed = []

for f in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    try:
        root = ET.parse(f).getroot()
    except Exception:
        continue
    for ts in root.iter("testsuite"):
        total += int(ts.get("tests", 0))
        failures += int(ts.get("failures", 0)) + int(ts.get("errors", 0))
        for tc in ts.iter("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.append(f"{tc.get('classname', '')}.{tc.get('name', '')}")

print(f"- Tests run: {total}, failed: {failures}")
if failed:
    print("- Failing tests:")
    for name in sorted(set(failed)):
        print(f"  - {name}")
else:
    print("- No failing tests.")
