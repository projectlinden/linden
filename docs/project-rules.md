# Project rules

The CI job **"Project rules"** (`.github/workflows/rules.yml`) checks the repository's rules on every pull request
and on every push to `main`. It is a **required check** in the ruleset `protect-main`: a pull request with a red
"Project rules" cannot be merged. Each rule is its own named step, so a red run shows which rule broke.

| Step                           | Rule                                                                                                                                            | Tool                                                                                                                             |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| **No secrets in Git**          | No password, key or token in any commit of any branch                                                                                           | [gitleaks](https://github.com/gitleaks/gitleaks) (MIT), official image `ghcr.io/gitleaks/gitleaks`, pinned by tag **and** digest |
| **No content in the database** | Every database column is on the allow list as `metadata` or `encrypted`, with a reason – no readable content                                    | `SchemaClassificationIT` (Testcontainers Postgres)                                                                               |
| **Module boundaries**          | `linden-core` knows nothing about HTTP; `linden-api` reaches the database only through core services – no repositories, JPA or `@Transactional` | `ArchitectureTest` ([ArchUnit](https://www.archunit.org), Apache-2.0)                                                            |

More rules join this job as they become testable (Dockerfile lint, image pins, licences, known vulnerabilities).

## No content in the database

The server must never store readable content (end-to-end encryption). `SchemaClassificationIT` runs all Flyway
migrations against a throwaway Postgres, reads every column and compares them with the allow list
`linden-core/src/test/resources/schema-classification.csv`:

- a column that is not on the list → red (*"columns without a classification"*)
- a line for a column that no longer exists → red (*"columns that no longer exist"*)
- a line with an unknown level, no reason, or listed twice → red

Each line is `table,column,classification,reason`. There are two levels: `metadata` (the server can read it – the
reason says why it needs it) and `encrypted` (an opaque blob). There is no level for readable content.

The test checks names and declarations, not what a column really holds – whether a `metadata` column reveals too
much stays a question for the pull request review.

### Run it locally

```bash
./mvnw --batch-mode --projects linden-core verify \
  -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SchemaClassificationIT
```

### Proven to fail

A commit that added an unclassified column turned "No content in the database" red and blocked the merge (PR #40).
Re-run this break check after changing the job.

## Module boundaries

The modules have different jobs: `linden-core` holds every rule, `linden-api` only speaks HTTP, `linden-app` wires
them together. The Maven modules already stop core from using api classes. `ArchitectureTest` (in `linden-app`, the
only module that sees all three) checks what Maven can't:

- **Core knows nothing about HTTP** – no `org.springframework.web`, `org.springframework.http`,
  `org.springframework.security.web` or `jakarta.servlet`. One added dependency in `linden-core/pom.xml` would
  otherwise let HTTP into the rules unnoticed.
- **The API reaches the database only through services** – no repositories, no `jakarta.persistence`, Spring Data or
  Hibernate, and no `@Transactional`. A controller that reads the database itself would skip the rules in the services
  (membership, ownership, limits).

Still allowed in the API: Spring's database-neutral exceptions (`org.springframework.dao`,
`CannotCreateTransactionException`) – `ApiExceptionHandler` turns them into `503`.

A failure lists every violation with class, member and line, and the rule's reason.

### Run it locally

```bash
./mvnw --batch-mode --projects linden-app --also-make test \
  -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Proven to fail

A `@Transactional` on a controller method turned "Module boundaries" red and blocked the merge (PR #48).
Re-run this break check after changing the rules.

## No secrets in Git

gitleaks scans the **whole history** (`fetch-depth: 0`) – a secret that was added and later removed is still in Git,
and this repository is public. Output is redacted (`--redact`), so a finding never prints the secret in the CI log.

### Run it locally

The same command as in CI, from the repo root:

```bash
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/repo:ro" \
  ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --redact --verbose --no-banner
```

It scans every branch your clone knows, including local ones – so it can report more commits than CI does.
Exit code `0` = clean, `1` = leaks found.

### When it finds something

1. **Is it a real secret?** Look at the rule, file and commit in the log (never paste the value anywhere).
2. **Real secret → rotate it first.** Create a new password or key and disable the old one – before anything else.
   Deleting the line or rewriting history does not help: the old value is already public.
3. Then remove it from the code (move it to `.env`, which is ignored) and decide whether to rewrite history.
4. **Fake value** (test data, examples): add a narrow allow list in `.gitleaks.toml` – for that one file or rule,
   with a comment saying why. Never switch a rule off for the whole repository.

### Updating gitleaks

The image is pinned by digest, so Dependabot does not update it. To update:

1. `docker pull ghcr.io/gitleaks/gitleaks:<new tag>` and run the local scan with it.
2. `docker image inspect ghcr.io/gitleaks/gitleaks:<new tag> --format '{{index .RepoDigests 0}}'`
3. Put the new tag and the `sha256:…` digest into `rules.yml` and this page, in one pull request.

### Proven to fail

A throw-away branch with a fake private key turned "Project rules" red and blocked the merge
(local and CI: `leaks found: 1`, exit code 1). Re-run this break check after changing the job.
