# Local setup

## Garage (S3 object storage) – one-time setup

Garage is a cluster by design. Even a single local node must be told its role once.
The setup is stored in the Docker volume, so it is only needed after the first start
(or after `docker compose down -v`).

```
# 1. Find the node id
docker exec linden-garage /garage status

# 2. Give the node a role: zone "dc1", 1 GB capacity – then apply
# <node-id> = the ID column from "garage status"
docker exec linden-garage /garage layout assign -z dc1 -c 1G <node-id>
docker exec linden-garage /garage layout apply --version 1

# 3. Create the bucket and the app's access key, and allow the key on the bucket
docker exec linden-garage /garage bucket create linden-media
docker exec linden-garage /garage key create linden-app
docker exec linden-garage /garage bucket allow --read --write --owner linden-media --key linden-app
```

Copy the key id and secret from `key create` into `.env`
(`GARAGE_ACCESS_KEY_ID`, `GARAGE_SECRET_ACCESS_KEY`).

## Postgres – databases and roles

On the first start (empty volume), `postgres/init/10-create-databases.sh` creates:

| Database   | Owner role | Used by              |
|------------|------------|----------------------|
| `linden`   | `linden`   | Spring Boot (Flyway) |
| `keycloak` | `keycloak` | Keycloak             |

Neither role is a superuser, and neither can connect to the other's database.
The `postgres` superuser is for maintenance only.

The script runs **only when the volume is empty**. After changing it, recreate the volume
(this deletes all local Postgres data):

    docker compose down
    docker volume rm linden_postgres-data
    docker compose up -d postgres

## Run the app (IntelliJ)

The app needs Postgres and Keycloak running (`docker compose up -d`). On start, **Flyway** creates or updates the
tables in the `linden` database (`linden-core/src/main/resources/db/migration`). Without a reachable database the
app does not start – it fails fast with a clear error.

Run configuration for `LindenApplication` → **Modify options → Environment variables**:

| Variable                 | Value                                            | Needed?                  |
|--------------------------|--------------------------------------------------|--------------------------|
| `LINDEN_DB_PASSWORD`     | the same value as `LINDEN_DB_PASSWORD` in `.env` | yes                      |
| `LINDEN_DB_URL`          | `jdbc:postgresql://localhost:5432/linden`        | no – that is the default |
| `LINDEN_DB_USER`         | `linden`                                         | no – that is the default |
| `LINDEN_AUTH_ISSUER_URI` | `http://localhost:8180/realms/linden`            | no – that is the default |

- Put the password **only** in *Environment variables* – never in *Active profiles*, never in a file in the repo.
- Keep the run configuration local: never tick **Store as project file** (it would be committed).
- The database timeouts are in `application.properties`: at most 3 s to get a connection, so a stopped or paused
  database gives a quick `503 service_unavailable`, never a hanging request.

### When it does not start

| Symptom                                                                         | Cause                                                                                                                                              |
|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| *The server requested SCRAM-based authentication, but no password was provided* | `LINDEN_DB_PASSWORD` is not set in the run configuration                                                                                           |
| *Invalid profile property value … 'LINDEN_DB_PASSWORD=…'*                       | The password was put into *Active profiles* – move it to *Environment variables*, and **change the password** if that log line was shared anywhere |
| *Connection to localhost:5432 refused*                                          | Postgres is not running – `docker compose up -d postgres`                                                                                          |
| `GET /api/v1/me` → `503 service_unavailable`                                    | The database stopped or paused after the app started – start it again; the app reconnects by itself                                                |

## Keycloak

Runs in development mode (`start-dev`) on http://localhost:8180, with its data in the `keycloak` database.

- Admin console: http://localhost:8180/admin – log in with `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD`.
- This bootstrap admin is created on the **first start only** and is meant to be temporary. Changing the
  values in `.env` later has no effect.
- The `master` realm is only for administering Keycloak. Linden uses its own realm `linden`.
- Health: port 9000 inside the container (used by the Docker health check, not published).

### Realm `linden` (as code)

The realm lives in `keycloak/realm/linden-realm.json` and is imported on startup (`--import-realm`).

- **Imported only if the realm does not exist yet.** After changing the file, delete the realm in the
  admin console (switch to realm `linden` → *Realm settings* → *Action* → *Delete*) and restart Keycloak:
  `docker compose restart keycloak`.
- Two realms: `master` (only for administering Keycloak, where `KEYCLOAK_ADMIN_USER` lives) and `linden`
  (Linden users). An admin account cannot log in to `linden`, and that is intended.
- The `linden` realm has **no users in Git**. Create local test users by hand: realm `linden` → *Users* → *Add user*,
  then set a password on the *Credentials* tab (turn *Temporary* off).
- Client `linden-web`: a public client (no secret – it runs in the browser) using Authorization Code with
  PKCE (Proof Key for Code Exchange, `S256`). Allowed redirect: `http://localhost:5173/*` (Vite dev server).
- Self-registration is off (invite-only), and so is "forgot password" until email is set up.

Check that the realm is up:

    curl -s http://localhost:8180/realms/linden/.well-known/openid-configuration

## Try the API with a real token

The API only accepts a **Bearer token** from Keycloak (realm `linden`). Until `linden-web` exists, you get one with
**Authorization Code + PKCE** – the same login flow the PWA will use. Bruno does the whole flow for you.

> **Always use `localhost`, never `127.0.0.1`, in the Keycloak URLs.** The token contains the address it was issued
> by (`iss`), and Spring Boot only accepts `http://localhost:8180/realms/linden` (`LINDEN_AUTH_ISSUER_URI`). A token
> from
> `127.0.0.1:8180` is rejected with `401`.

### 1. Create a test user (once)

1. Open the admin console: `http://localhost:8180/admin` – log in with `KEYCLOAK_ADMIN_USER` /
   `KEYCLOAK_ADMIN_PASSWORD` from `.env`.
2. Switch to the realm **linden** (drop-down top left – not `master`).
3. **Users → Create new user:** username e.g. `anna`, email `anna@example.com`, *Email verified* on → **Create**.
4. Tab **Credentials → Set password:** a test password, *Temporary* **off** → **Save**.

Use made-up test users only – never a real person's email.

### 2. Get a token in Bruno (recommended)

Set the auth **once on the collection**, so every request inherits it:

1. Collection settings → **Auth** → mode **OAuth 2.0**.
2. Fill in:

   | Field              | Value                                                                 |
            |--------------------|-----------------------------------------------------------------------|
   | Grant type         | Authorization Code                                                    |
   | Callback URL       | `http://localhost:5173/`                                              |
   | Authorization URL  | `http://localhost:8180/realms/linden/protocol/openid-connect/auth`    |
   | Access Token URL   | `http://localhost:8180/realms/linden/protocol/openid-connect/token`   |
   | Client ID          | `linden-web`                                                          |
   | Client Secret      | *(empty – `linden-web` is a public client)*                           |
   | Scope              | `openid`                                                              |
   | Use PKCE           | **on**                                                                |

3. Click **Get Access Token**. Bruno opens a login window – log in as your test user. Bruno catches the redirect to
   `localhost:5173` itself (nothing needs to run there) and stores the token.
4. In each request: **Auth → Inherit**. Bruno sends `Authorization: Bearer <token>`.

The access token is valid for a few minutes; Bruno renews it with the refresh token, or you click
**Get Access Token** again.

**Check:** `GET http://localhost:8080/api/v1/me` → `200` with `{"userId":"…"}` (the user's ID in Keycloak).

### 3. Without Bruno (curl)

1. Create a PKCE pair and open the printed URL in the browser:

   ```bash
   CODE_VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
   CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '=\n')
   echo "http://localhost:8180/realms/linden/protocol/openid-connect/auth?client_id=linden-web&response_type=code&scope=openid&redirect_uri=http://localhost:5173/&code_challenge=$CODE_CHALLENGE&code_challenge_method=S256"
   ```

2. Log in. The browser then fails to open `localhost:5173` – expected. Copy the value of `code` from the address bar
   (between `code=` and the next `&`). It is valid for about one minute.
3. Exchange the code for a token and call the API:

   ```bash
   CODE='paste-the-code-here'
   TOKEN=$(curl -s -X POST http://localhost:8180/realms/linden/protocol/openid-connect/token \
     -d grant_type=authorization_code -d client_id=linden-web -d redirect_uri=http://localhost:5173/ \
     -d code="$CODE" -d code_verifier="$CODE_VERIFIER" | jq -r .access_token)
   curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me
   ```

### When it does not work

| Symptom                                                 | Cause                                                                                   |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `401` although a token was sent                         | Token from `127.0.0.1` instead of `localhost` (wrong `iss`), or expired – get a new one |
| `401` and no `Authorization` header in Bruno's Timeline | The request's Auth is not set to *Inherit*                                              |
| Keycloak: *Invalid parameter: redirect_uri*             | Callback URL is not exactly `http://localhost:5173/`                                    |
| Keycloak: *Client not found*                            | Realm `master` instead of `linden` in the URLs                                          |
| `invalid_grant` (curl)                                  | The code expired or was used twice – log in again                                       |

Never paste tokens into online JWT decoders – a token is a key to the account.
