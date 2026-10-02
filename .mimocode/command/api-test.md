---
description: "Test a backend API endpoint directly. Usage: /api-test /api/products?category=手机"
---

Test a backend API endpoint by making a direct HTTP request:

1. Take the URL path from `$ARGUMENTS` (e.g., `/api/products?category=手机`)
2. Execute: `Invoke-WebRequest -Uri "http://localhost:8080$ARGUMENTS" -UseBasicParsing`
3. For POST/PUT/DELETE, use `-Method POST` (or PUT/DELETE) and `-Body` from `$ARGUMENTS`
4. Report the HTTP status code and response body (truncated to 500 chars if large)
5. If the response is JSON, parse and summarize key fields (success, total, data)
6. If the request fails (connection refused, 500, etc.), report the error clearly
