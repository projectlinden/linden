#!/usr/bin/env bash
# Finishes the rename Kreds → Stamly (Phase 0, Step 8). Run once, from the repo root, on chore/rename-to-stamly.
# The UI word *kreds* / *kredse* (a group) is kept on purpose; history (spike/, the old symbol draft) is not touched.
set -euo pipefail

# 1. Files whose names contain "kreds"
git mv monitoring/grafana/dashboards/kreds-overview.json monitoring/grafana/dashboards/stamly-overview.json
git mv monitoring/grafana/dashboards/kreds-logs.json     monitoring/grafana/dashboards/stamly-logs.json
git mv monitoring/grafana/provisioning/dashboards/kreds.yml monitoring/grafana/provisioning/dashboards/stamly.yml

# 2. The files that still contain technical "kreds" names
files=(
  README.md .env.example .gitignore docker-compose.yml docker-compose.monitoring.yml
  garage/garage.toml postgres/init/10-create-databases.sh
  stamly-app/src/main/resources/application.properties
  monitoring/prometheus/prometheus.yml monitoring/fluent-bit/fluent-bit.yaml monitoring/loki/loki.yml
  monitoring/grafana/dashboards/stamly-overview.json monitoring/grafana/dashboards/stamly-logs.json
  monitoring/grafana/provisioning/dashboards/stamly.yml
  bruno/stamly/environments/Local.yml
  docs/local-setup.md docs/architecture/auth-flow.md docs/architecture/encryption-flow.md
  docs/operations/monitoring.md docs/operations/log-inventory.md
)

# Order matters: specific names first, the plain app name "Kreds" last.
sed -i \
  -e 's#github\.com/tysker/kreds#github.com/tysker/stamly#g' \
  -e 's/LOGGING_LEVEL_DK_NORDICS_KREDS/LOGGING_LEVEL_NU_STAMLY/g' \
  -e 's/KREDS_/STAMLY_/g' \
  -e 's/kreds\.service/stamly.service/g' \
  -e 's/kreds_\(default\|postgres-data\)/stamly_\1/g' \
  -e 's/kreds-\(web\|api\|core\|app\|crypto\|media\|admin\|overview\|logs\|realm\)/stamly-\1/g' \
  -e 's/kreds-\(garage\|grafana\|prometheus\|postgres\|keycloak\|loki\|fluent-bit\)/stamly-\1/g' \
  -e 's#realms/kreds#realms/stamly#g' \
  -e 's/realmName="kreds"/realmName="stamly"/g' \
  -e 's/^name: kreds$/name: stamly/' \
  -e 's/`name: kreds`/`name: stamly`/g' \
  -e 's#dashboards/kreds\.yml#dashboards/stamly.yml#g' \
  -e 's/name: kreds$/name: stamly/' \
  -e 's/datname=~\\"kreds|keycloak\\"/datname=~\\"stamly|keycloak\\"/' \
  -e 's/"kreds"$/"stamly"/' \
  -e 's/`kreds`/`stamly`/g' \
  -e 's/Realm: kreds/Realm: stamly/g; s/database: kreds/database: stamly/g; s/Postgres (kreds)/Postgres (stamly)/g' \
  -e 's/realm \*\*kreds\*\*/realm **stamly**/g; s/kredsdb/stamlydb/g' \
  -e 's/the kreds and keycloak databases/the stamly and keycloak databases/g' \
  -e 's/folder \*Kreds\*/folder *Stamly*/g; s/folder: Kreds/folder: Stamly/' \
  -e 's/\bKreds\b/Stamly/g' \
  "${files[@]}"

# 3. README intro: the app is Stamly; "kreds" is what a group is called inside it
sed -i 's/^Stamly (Danish for \*circle\*) is a private/Stamly is a private/' README.md
sed -i '/^a Nordic alternative to Instagram/a\
Inside the app, a group of people is called a *kreds* (Danish for *circle*).' README.md
