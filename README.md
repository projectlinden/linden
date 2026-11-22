# Linden

![Build](https://github.com/projectlinden/linden/actions/workflows/build.yml/badge.svg)

> A calm, private place for the people you actually know. Made in the Nordics.

Linden is the code name of a private, end-to-end encrypted social app for family and friends –
a Nordic alternative to Instagram, Facebook, Snapchat and WhatsApp. No ads, no strangers, no AI.
The product name is not decided yet; it lives only in user-facing places, never in the code.
Inside the app, a group of people is called a *kreds* (Danish for *circle*).

**Status:** early prototype – Phase 2 (login with Keycloak). Family and friends only.

## Stack

Java 25 · Spring Boot 4 · Postgres · Keycloak · RabbitMQ · S3 (Garage locally) · React PWA

## Getting started

Requires Java 25, Docker and Docker Compose.

```
cp .env.example .env          # then change the passwords in .env
docker compose up -d          # Postgres, Keycloak, Garage.
./mvnw verify                 # build all modules and run all tests
```

| Service          | Address                     |
|------------------|-----------------------------|
| Postgres         | `localhost:5432`            |
| Garage (S3)      | http://localhost:3900       |
| Keycloak (admin) | http://localhost:8180/admin |

First start? See [docs/local-setup.md](docs/local-setup.md)

## Documentation

- [Local setup](docs/local-setup.md) – first start, Keycloak, Garage, tokens with Bruno
- [Project rules](docs/project-rules.md) – what the "Project rules" CI job checks, and what to do when it is red
- [Architecture](docs/architecture/) – login (`auth-flow.md`) and end-to-end encryption (`encryption-flow.md`)
- [Operations](docs/operations/) – monitoring and the log inventory

## Licence

Not yet licensed – all rights reserved. Planned: open source (AGPL-3.0) before going public.
