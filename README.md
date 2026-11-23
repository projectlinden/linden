# Linden

![Build](https://github.com/projectlinden/linden/actions/workflows/build.yml/badge.svg)

> A calm, private place for the people you actually know. Made in the Nordics.

Linden is the code name of a private, end-to-end encrypted social app for family and friends –
a Nordic alternative to Instagram, Facebook, Snapchat and WhatsApp. No ads, no strangers, no AI.
The product name is not decided yet; it lives only in user-facing places, never in the code.
Inside the app, a group of people is called a *kreds* (Danish for *circle*).

**Status:** early prototype – Phase 2 (login with Keycloak). Family and friends only.

## Stack

Java 25 · Spring Boot 4 · Postgres · Keycloak · S3 (Garage locally) · Prometheus, Grafana, Loki · React PWA (planned)

## Getting started

Requires Java 25, Docker and Docker Compose.

```bash
cp .env.example .env          # then replace every change-me in .env

# Everything: Postgres, Keycloak, Garage + monitoring (Prometheus, Grafana, Loki, Fluent Bit)
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d

./mvnw verify                 # build all modules and run all tests
```

Without monitoring (faster, less memory): `docker compose up -d` starts only Postgres, Keycloak and Garage.
Compose then warns about "orphan containers" if monitoring ran before – that is harmless; never add
`--remove-orphans`, it would delete the monitoring containers. Stop with the same `-f` options you started with.

| Service          | Address                     |
|------------------|-----------------------------|
| Postgres         | `localhost:5432`            |
| Garage (S3)      | http://localhost:3900       |
| Keycloak (admin) | http://localhost:8180/admin |
| Grafana          | http://localhost:3000       |
| Prometheus       | http://localhost:9090       |

First start? See [docs/local-setup.md](docs/local-setup.md) (Garage setup, Keycloak test user) and
[docs/operations/monitoring.md](docs/operations/monitoring.md) (Linux: the app's metrics port and the firewall rule).

## Documentation

- [Local setup](docs/local-setup.md) – first start, Keycloak, Garage, tokens with Bruno
- [Project rules](docs/project-rules.md) – what the "Project rules" CI job checks, and what to do when it is red
- [Architecture](docs/architecture/) – login (`auth-flow.md`) and end-to-end encryption (`encryption-flow.md`)
- [Operations](docs/operations/) – monitoring and the log inventory

## Licence

Not yet licensed – all rights reserved. Planned: open source (AGPL-3.0) before going public.
