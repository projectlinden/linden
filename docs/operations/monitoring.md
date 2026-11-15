# Monitoring

How Kreds watches itself: metrics, health and (later) logs and alerts – **without ever seeing what users share**.
Decisions: ADR-0003 (Prometheus for metrics, Grafana for dashboards and alerts) and ADR-0004 (Loki for logs).
Logs: see [log-inventory.md](log-inventory.md).

## Overview

```mermaid
flowchart LR
    subgraph host["Your machine"]
        app["kreds-app<br/>:8081 /actuator/prometheus"]
    end
    subgraph docker["Docker network kreds_default"]
        kc["Keycloak<br/>:9000 /metrics"]
        garage["Garage<br/>:3903 /metrics (token)"]
        pgx["postgres-exporter<br/>:9187"] --> pg[("Postgres")]
        osx["opensearch-exporter<br/>:9114"] --> os[("OpenSearch")]
        prom["Prometheus<br/>127.0.0.1:9090"]
        grafana["Grafana<br/>127.0.0.1:3000"]
    end
    grafana --> prom
    prom --> app
    prom --> kc
    prom --> garage
    prom --> pgx
    prom --> osx
```

Prometheus **pulls** ("scrapes") every 30 seconds and keeps metrics for 15 days. Grafana reads from Prometheus and is
the only monitoring UI with a login.

| Job          | Target                                          | Reachable from                                                                        | Access                                                                   |
|--------------|-------------------------------------------------|---------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `prometheus` | `localhost:9090`                                | `127.0.0.1:9090` on the host                                                          | Local only                                                               |
| `keycloak`   | `keycloak:9000/metrics`                         | Docker network only (management port is **never** published)                          | –                                                                        |
| `garage`     | `garage:3903/metrics`                           | Docker network; admin port also on `127.0.0.1:3903`                                   | Bearer token `GARAGE_METRICS_TOKEN` (metrics only – not the admin token) |
| `postgres`   | `postgres-exporter:9187`                        | Docker network only                                                                   | Exporter logs in as role `monitoring`                                    |
| `opensearch` | `opensearch-exporter:9114`                      | Docker network only                                                                   | None yet (OpenSearch security is off locally)                            |
| `kreds-app`  | `host.docker.internal:8081/actuator/prometheus` | Management port of the app, bound to `KREDS_MANAGEMENT_ADDRESS` (default `127.0.0.1`) | None – the port is never public                                          |

## Starting and stopping

Monitoring lives in its own file, `docker-compose.monitoring.yml`, in the **same Compose project** (`name: kreds`),
so it shares the network with the services it watches.

```bash
# Everything
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d

# Kreds only (Compose then warns about "orphan containers" – harmless, never add --remove-orphans)
docker compose up -d

# Check the scrape config before (re)starting Prometheus
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml run --rm --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml

# After changing prometheus.yml
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml restart prometheus
```

> **Never use `docker compose down -v`** unless you want to wipe all data. `-v` deletes the volumes – Postgres,
> OpenSearch, Garage (including its layout, bucket and keys) and Prometheus.

Status page: `http://127.0.0.1:9090/targets` – every job must be **UP**.

## Grafana

`http://127.0.0.1:3000` – log in with `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env`.

- **OSS image** `grafana/grafana` (AGPL-3.0) – never `grafana-enterprise` (different licence). Pin a stable
  `MAJOR.MINOR.PATCH` tag, never a nightly build (a tag with a long build number after the hyphen).
- **Locked down:** no anonymous access, no sign-up, admin password as a Compose secret
  (`GF_SECURITY_ADMIN_PASSWORD__FILE`).
- **Nothing leaves the machine:** usage reporting, update checks, news feed and Gravatar are switched off.
- **The admin password is applied only on the very first start** (when `grafana-data` is empty). To change it later:
  `docker exec kreds-grafana grafana cli admin reset-admin-password <new>`.

Everything is **provisioned as code** from `monitoring/grafana/`:

| File                                      | What                                                                                                                                              |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `provisioning/datasources/prometheus.yml` | Data source `Prometheus` with the fixed uid `prometheus`; not editable in the UI                                                                  |
| `provisioning/dashboards/kreds.yml`       | Loads every JSON file from `dashboards/` into the folder *Kreds*; UI changes cannot be saved                                                      |
| `dashboards/kreds-overview.json`          | **"Kreds – is it OK?"** – services up, Postgres reachable, OpenSearch not red, logins per hour, app requests and errors, JVM heap, database sizes |

**Changing a dashboard:** edit it in the UI to try things out, then *Export → Export as JSON*, replace the file in
`dashboards/`, and discard the UI changes. Grafana reloads the file within 30 seconds. Every panel must use the data
source uid `prometheus` – an exported dashboard from another Grafana may carry a different one.

## Secrets and roles

| Variable (`.env`)        | Used by                                                                           | Rights                                                                                          |
|--------------------------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `GARAGE_METRICS_TOKEN`   | Garage + Prometheus (as Compose secret, file `/run/secrets/garage_metrics_token`) | Only `/metrics` – cannot touch buckets or keys                                                  |
| `GRAFANA_ADMIN_PASSWORD` | Grafana (Compose secret, first start only)                                        | Admin of the monitoring UI                                                                      |
| `MONITORING_DB_PASSWORD` | Postgres role `monitoring` + postgres-exporter (Compose secret)                   | `pg_monitor`: statistics and settings; **no** `CONNECT` on the `kreds` and `keycloak` databases |

Secrets reach containers as **Compose secrets** (files under `/run/secrets/`), not as environment variables, so they
do not appear in `docker inspect`. No committed file contains a secret.

The role `monitoring` is created by `postgres/init/20-create-monitoring-role.sh` – automatically on the first start
of an empty volume. For an existing volume, run it once by hand (it is idempotent):

```bash
docker compose exec postgres bash /docker-entrypoint-initdb.d/20-create-monitoring-role.sh
```

## The Kreds app (Spring Boot Actuator)

- Actuator runs on its **own management port** (`KREDS_MANAGEMENT_PORT`, default `8081`), bound to
  `KREDS_MANAGEMENT_ADDRESS` (default `127.0.0.1`). The public API port `8080` serves no Actuator at all.
- Only `health` (including `health/liveness` and `health/readiness`) and `prometheus` are exposed and open without
  a token. Everything else under `/actuator` is denied (`ManagementSecurityConfig`) – `env` or `heapdump` could
  contain secrets.
- `ManagementEndpointsIT` proves all of this.
- **Linux:** `host.docker.internal` points to the Docker bridge (`docker0`), not to `127.0.0.1`. Two things are needed:
    1. Start the app with `KREDS_MANAGEMENT_ADDRESS=172.17.0.1` (in IntelliJ: run configuration → environment
       variables). `172.17.0.1` is Docker's default `docker0` address – check with `ip -4 addr show docker0` and
       `docker exec kreds-prometheus grep host.docker.internal /etc/hosts`; both must show the same IP.
    2. If the Ubuntu firewall (`ufw`) is on, allow **only** Docker's networks to reach the port – never disable the
       firewall:
       ```bash
       sudo ufw allow from 172.16.0.0/12 to any port 8081 proto tcp comment 'Prometheus in Docker -> kreds-app metrics'
       ```
  Symptoms in the targets check: `connection refused` → the app listens on the wrong address;
  `context deadline exceeded` → the firewall drops the connection.
  On macOS, Docker Desktop forwards `host.docker.internal` to `127.0.0.1`, so the default works.
- Later, when the app runs in Compose, the target becomes `kreds-app:8081`, `KREDS_MANAGEMENT_ADDRESS=0.0.0.0`
  (inside a container that means only the container's own network) and the port is not published at all.

## Privacy rules for metrics

The data classification applies to metrics too ("when in doubt, encrypt"):

1. **Labels never contain personal data** – no user IDs, *kreds* IDs, email addresses, IPs or paths. Keycloak's
   login events are labelled only by realm, client, event and error (`KC_EVENT_METRICS_USER_TAGS=realm,clientId`).
2. **Index names never contain personal or *kreds*-specific data** (never `places-<circleId>`). They become label
   values in Prometheus.
3. **Spring metrics use URI templates** (`/api/v1/circles/{id}`), never real IDs, as tags.
4. Bruno checks enforce rule 1 for Keycloak, Garage, Postgres and OpenSearch (allow-list of label names, no `@` in
   label values).

## Checks (Bruno)

Grafana credentials live in a Bruno environment as **secret variables** (`grafanaUser`, `grafanaPassword`) – never
in a request file. A failing targets check names the job, its URL and Prometheus' `lastError`.

| Request                                                        | What it proves                                                                         |
|----------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `GET :9090/api/v1/targets`                                     | All expected jobs exist and are `up`                                                   |
| `query=keycloak_user_events_total`                             | Login events are counted; label names only from the allow-list                         |
| `GET :3903/metrics` (no token)                                 | Garage refuses metrics without the token (`401`/`403`)                                 |
| `query={job="garage"}`                                         | No `@` in any label value                                                              |
| `query=pg_up`                                                  | The exporter can really log in to Postgres (`"1"`)                                     |
| `query=elasticsearch_cluster_health_number_of_nodes`           | The exporter really reaches OpenSearch                                                 |
| `query=elasticsearch_cluster_health_status`                    | Cluster is not red (picked by the `color` label, never by position)                    |
| `GET :3000/api/health`                                         | Grafana runs and its database is OK                                                    |
| `GET :3000/api/datasources/uid/prometheus/health` (Basic auth) | Grafana itself can query Prometheus                                                    |
| `GET :3000/api/datasources` (no auth)                          | No anonymous access (`401`)                                                            |
| `GET :3000/api/dashboards/uid/kreds-overview` (Basic auth)     | The overview dashboard is provisioned from the repo; every panel uses uid `prometheus` |

## Good to know

- **Keycloak login counters appear only after the first login** since Keycloak started. Until then the query is
  empty – expected, not a bug.
- **A target can be `up` while the service behind it is broken.** Exporters keep running when they cannot reach
  their database. That is why `pg_up` and the OpenSearch metrics are checked separately.
- **OpenSearch is yellow on a single node** when indices have replicas – normal locally. Red means act.
- **`busybox` tools in the Prometheus image** (`wget`, `nslookup`) can fail to resolve container names when the host
  has a search domain (e.g. `.home`). Prometheus itself is not affected – test through Prometheus or Bruno instead.
- **`pg_monitor` can read the text of running queries** (`pg_stat_activity`). Accepted: the exporter does not export
  query text by default, Kreds uses bind parameters (`$1`), and the exporter is reachable only inside Docker.
- `sslmode=disable` between the exporter and Postgres is acceptable only while both run on the same Docker network.

## Before any server

- OpenSearch security on → the opensearch-exporter gets its own read-only monitoring user.
- Postgres on its own server → TLS (`sslmode=verify-full`) for the exporter.
- Prometheus, exporters and (later) Loki stay on the internal network; only Grafana gets a login.
