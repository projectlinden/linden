## What and why

## Checklist

- [ ] Branch from an up-to-date `main`
- [ ] Test first (or documentation for non-code steps)
- [ ] `./mvnw verify` is green – locally and in CI
- [ ] Break check done
- [ ] Docs updated (README / docs)
- [ ] Security-relevant? → `docs/security.md` updated (new rule, changed rule, or an open point closed)
- [ ] Commit messages follow Conventional Commits

## Human review (ENGINEERING.md § 11 – no machine can check these)

- [ ] Does a new server-visible field reveal relationships? (every new column has a reason in
  `schema-classification.csv`)
- [ ] Threat model: what could go wrong, and who could abuse it?
- [ ] Is it calm? (no streaks, no dark patterns, few notifications)
- [ ] No runes or Viking symbols
- [ ] Does a new service or dependency break the Nordic rule?
