# Project rules

The CI job **"Project rules"** (`.github/workflows/rules.yml`) checks the repository's rules on every pull request
and on every push to `main`. It is a **required check** in the ruleset `protect-main`: a pull request with a red
"Project rules" cannot be merged. Each rule is its own named step, so a red run shows which rule broke.

| Step                  | Rule                                                  | Tool                                                                                                                             |
|-----------------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| **No secrets in Git** | No password, key or token in any commit of any branch | [gitleaks](https://github.com/gitleaks/gitleaks) (MIT), official image `ghcr.io/gitleaks/gitleaks`, pinned by tag **and** digest |

More rules join this job as they become testable (Dockerfile lint, image pins, licences, known vulnerabilities).

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
