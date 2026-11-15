# Log inventory

What each Kreds service writes to its log today, and where personal data can slip in.
This is the input for the log collector's filter rules and for the "no personal data in logs" test.

**The rule (ENGINEERING.md § 2):** logs never contain content, keys, tokens, passwords or email addresses.
When in doubt, a field is dropped.

Recorded on 2026-11-15, local development setup (`docker compose`, Spring Boot started from the IDE).

## Container log settings

All containers use the `json-file` log driver with rotation (`x-logging` in `docker-compose.yml`):
10 MB per file, 3 files per container. Log collectors read these files later.

## Overview

| Service                   | Format                                     | Personal data found                                                                  | Risk     | What the collector must do                                                                                  |
|---------------------------|--------------------------------------------|--------------------------------------------------------------------------------------|----------|-------------------------------------------------------------------------------------------------------------|
| Keycloak                  | Plain text, events as `key="value"` pairs  | `username`, `ipAddress`, `code_id` in login events                                   | **High** | Parse the event fields, drop `username`, `ipAddress`, `code_id`; keep `type`, `error`, `userId`, `clientId` |
| Postgres                  | Plain text                                 | Full SQL statement on error, including literal values (an email address in the test) | **High** | Drop `STATEMENT:` and `DETAIL:` lines                                                                       |
| OpenSearch                | Plain text                                 | None found                                                                           | Low      | Pass through                                                                                                |
| OpenSearch Dashboards     | –                                          | None found                                                                           | Low      | Pass through                                                                                                |
| Garage                    | Plain text                                 | None found (object paths may appear on errors)                                       | Medium   | Watch for bucket and object paths, drop them if they appear                                                 |
| Spring Boot (`kreds-app`) | Plain text console, no request logging yet | None yet                                                                             | –        | Comes later: JSON logs with a correlation ID; checked by a test in the app                                  |

## Findings

### Keycloak – login events

A failed login writes a `WARN` line from `org.keycloak.events`:

```
WARN [org.keycloak.events] type="LOGIN_ERROR", realmName="kreds", clientId="account-console",
userId="<uuid>", ipAddress="172.19.0.1", error="invalid_user_credentials", code_id="<id>", username="<username>"
```

| Field                                                   | Keep?              | Why                                                                                                       |
|---------------------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------|
| `type`, `error`, `realmName`, `clientId`, `auth_method` | Keep               | Needed to see failed logins and their cause                                                               |
| `userId`                                                | Keep               | The Keycloak `sub` – the same ID Postgres uses; server-visible protected data. `"null"` for unknown users |
| `username`                                              | **Drop**           | Users may type an email address as their username – and for an unknown user it is whatever was typed      |
| `ipAddress`                                             | **Drop** (for now) | Personal data under GDPR. Whether the security audit (login history) keeps it is decided there            |
| `code_id`                                               | **Drop**           | Identifies a login session; not needed for monitoring                                                     |
| `redirect_uri`                                          | Keep               | Public application URLs only                                                                              |

A login with an unknown user writes the same event with `error="user_not_found"`, `userId="null"` and
`username` set to **exactly what was typed** – in the test an email address (`nobody@example.com`). Because the realm
allows login with email (`loginWithEmailAllowed: true`), the `username` field will often hold an email address,
even for known users. Dropping `username` is therefore required, not optional.

Failed logins for unknown users stay visible through `type`, `error` and the count of events – enough to notice
someone guessing accounts, without storing what they typed.

### Postgres – statements on error

By default Postgres logs the failing statement (`log_min_error_statement = error`):

```
ERROR:  relation "app_user" does not exist at character 15
STATEMENT:  SELECT * FROM app_user WHERE email = 'anna@example.com'
```

The app uses JDBC with bind parameters, so its statements show `$1` instead of values, and Postgres does not log
parameter values on error by default (`log_parameter_max_length_on_error = 0`). But two leaks remain:
statements typed by hand (psql, maintenance scripts) contain literal values, and a failed unique constraint writes
the value into the `DETAIL:` line, even for the app – e.g. `DETAIL: Key (email)=(anna@example.com) already exists.`
Therefore: `STATEMENT:` and `DETAIL:` lines are dropped.

### OpenSearch, Dashboards, Garage

No email addresses or names found in normal operation.

## Open points

- Keycloak can write JSON logs itself (`KC_LOG_CONSOLE_OUTPUT=json`) – easier and safer to filter than parsing text.
- Postgres: decide whether `log_min_error_statement` is raised on the server so statements are never written at all.
- IP addresses in the security audit log: needed for "login from a new place"? Decide with the audit design.
- Spring Boot: JSON logs, correlation ID, and a test that no log line contains an email address.
