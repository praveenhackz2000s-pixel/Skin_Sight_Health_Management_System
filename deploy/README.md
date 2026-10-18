# Deploying the website

> **Status:** the author of this project template has **not** deployed it anywhere. Nothing below is "live".
> Follow the steps, then use the verification list at the end to prove that *your* deployment works.

## Why GitHub alone is not enough

GitHub stores the source code. **GitHub Pages can only serve static HTML** - it cannot run a Java server and it
must never hold a database password. A live site needs three separate pieces:

```
Browser  -->  Docker web-service host (runs this Java app)  -->  hosted MySQL database
                      ^
                 GitHub repository (source code; the host builds from it)
```

## What you need

1. The code in a GitHub repository (see the main README).
2. A host that can run a **Docker web service** (examples: Render, Railway, Fly.io, a VPS). Check each provider's
   current free tier and terms yourself - they change.
3. A **hosted MySQL 8** database (examples: Railway MySQL, Aiven for MySQL, a managed MySQL from your host).
   Your own PC's MySQL is not reachable from the internet and should not be.

## Step 1 - create the database

Create an empty MySQL database and note: **host, port, database name, user, password**.
Most providers also tell you whether SSL is required (then add `sslMode=REQUIRED` to the URL).

You do **not** have to run the SQL files by hand: on its first start the application creates the tables from
`schema.sql` and loads the fictional demo data from `seed-demo.sql` into the database named in `DB_URL`.
(Set `LOAD_DEMO_DATA=false` if you do not want the demo data.)

If you prefer to do it by hand, run the two files with any MySQL client, **but** the first two statements of
`schema.sql` (`CREATE DATABASE ...; USE smart_hospital;`) and the `USE` line of `seed-demo.sql` assume a database
called `smart_hospital`. On a provider that gives you a fixed database name (for example `defaultdb`), delete those
statements first.

## Step 2 - create the web service

For a Docker-based host the settings are always the same idea:

| Setting | Value |
|---|---|
| Source | your GitHub repository (branch `main`) |
| Runtime / build | **Docker** (uses the `Dockerfile` in the repository root) |
| Health check path | `/health` |
| Port | nothing to configure - the app reads the `PORT` variable the host provides (falls back to 8080) |

## Step 3 - environment variables

Set these in the host's dashboard (never in the repository):

| Variable | Example | Notes |
|---|---|---|
| `DB_URL` | `jdbc:mysql://db-host.example.com:3306/smart_hospital?sslMode=REQUIRED&characterEncoding=UTF-8` | host, port and database name from step 1 |
| `DB_USER` | `hospital_app` | |
| `DB_PASSWORD` | *(your database password)* | secret |
| `APP_USER` | `staff` | login name for the website |
| `APP_PASSWORD` | *(a long random password)* | **required for a public site** - turns on the login box |
| `AUTO_INIT_DB` | `true` (default) | create tables on first start |
| `LOAD_DEMO_DATA` | `true` (default) | load the fictional demo data when there are no doctors |

If your provider's database does **not** use SSL, remove `sslMode=REQUIRED` and use
`useSSL=false&allowPublicKeyRetrieval=true` instead.

## Step 4 - deploy and verify

Open the host's logs. A healthy first start prints lines like:

```
[init] Creating tables from schema.sql ...
[init] Loading fictional demo data from seed-demo.sql ...
[init] Database ready: jdbc:mysql://db-host.example.com:3306/smart_hospital
Login    : HTTP Basic login is ON
Open     : http://localhost:8080/
```

Then check, in this order:

- [ ] `https://YOUR-SITE/health` shows `{"status":"UP","database":"UP"}` (this is the check for "the deployed app
      can reach the external database"; `"database":"DOWN"` with HTTP 503 means the DB settings are wrong).
- [ ] `https://YOUR-SITE/` asks for the login and, after logging in, shows the dashboard with demo numbers.
- [ ] Register a patient, create a visit, assign a doctor. Reload - the data is still there.
- [ ] Open the site on a phone-sized window; the layout stays readable.

## Privacy rules for a public site

- Use **only fictional data**. This is a college prototype, not certified medical software.
- Always set `APP_PASSWORD`. Without it anyone who finds the URL can read and change every record.
- The login is a single shared password over HTTPS (all hosts above provide HTTPS). It is basic protection for a demo,
  not a replacement for per-user accounts.
- Never commit `db.properties`, `.env` files or screenshots that show passwords.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `/health` is 503, `"database":"DOWN"` | wrong `DB_URL` / user / password, firewall, or missing `sslMode` |
| Log says `Access denied` | wrong `DB_USER` / `DB_PASSWORD` |
| Log says `Unknown database` and cannot create it | the user has no `CREATE` right - create the database yourself first |
| Build fails while downloading the driver | the host blocked `repo1.maven.org`; copy a `.jar` into `lib/` and change the Dockerfile to `COPY lib ./lib` |
| Site is slow on the first request | free hosts put idle services to sleep; the first request wakes it |
