---
description: "Run project tests and report pass/fail status. Use after any code change to verify nothing broke."
---

Run the project's test suite and report results:

1. Execute: `.\.maven\apache-maven-3.9.6\bin\mvn.cmd test` from `E:\mimo-worker\javaweb`
2. Parse output for: total tests run, failures, errors
3. Report: "N tests, X failures, Y errors — BUILD SUCCESS/FAILURE"
4. If any test fails, show the failing test name and error message
5. If BUILD SUCCESS, confirm "All tests passing"
