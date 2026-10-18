# Smart Eye & Skin Specialist Hospital Management System

A small hospital web application for a hospital that treats **only eye and skin patients**.
Reception registers patients and records visits; the system then

1. finds **similar earlier visits** by comparing symptom keywords, and
2. applies **clinician-approved routing rules** to suggest a specialist category,

and helps reception **assign an eligible, available doctor with the lowest workload**. Doctors see their queue and
record findings, test results and their own confirmed diagnosis.

> **Important:** this is a *college project and a clinical decision-support prototype*. It does **not** diagnose
> disease, does not prescribe, and must not be used with real patient data. All people, doctors and records in the demo
> data are **fictional**. The routing rules are **demonstration rules** that have not been validated by clinicians.

Technology (as required by the lecturer): **Java 17, `com.sun.net.httpserver.HttpServer`, JDBC, MySQL 8, plain
HTML + CSS, no JavaScript.** Compiled with `javac`, run with `java`. **No Maven, no Gradle, no Spring, no ORM, no Node,
no Python, no AI/ML service.**

```
Browser  ->  Java HTTP server  ->  JDBC  ->  MySQL
```

---

## 1. Quick start (about 5 minutes)

### You need

| Software | Version | Check with |
|---|---|---|
| JDK (not only a JRE) | 17 or newer, e.g. Eclipse Temurin 17 | `java -version` and `javac -version` |
| MySQL Server | 8.x, running | `mysql --version` |
| Internet, once | to download the MySQL JDBC driver | - |

### Windows (PowerShell, VS Code terminal or double-click)

```powershell
# 1. in the project folder: tell the app your MySQL password (skip if root has no password)
Copy-Item db.properties.example db.properties
notepad db.properties            # set db.user and db.password, save

# 2. build and start (downloads the JDBC driver into lib\ the first time)
.\run.bat
```

### Linux / macOS

```bash
cp db.properties.example db.properties && nano db.properties   # set db.user / db.password
bash run.sh
```

Open **<http://localhost:8080/>**. That is all: on first start the application

* creates the database `smart_hospital` if it does not exist,
* runs `schema.sql` if the tables are missing,
* loads the **fictional demo data** from `seed-demo.sql` if there are no doctors yet.

The terminal prints `[init] ...` lines so you can see what happened. If MySQL is not reachable the web server still
starts, the pages show a friendly "Database problem" message, and the terminal explains the cause.

### If `run.bat` cannot download the driver

Some college networks block the download. Get the JAR by hand - see [`lib/README.md`](lib/README.md) (a 2-minute job) -
then run `run.bat` again.

---

## 2. Configuration

Every setting can be an **environment variable** (used on a hosting platform) or a line in the private file
**`db.properties`** (used on your own PC; it is listed in `.gitignore` and never committed). Environment variables win.

| Environment variable | `db.properties` key | Default | Meaning |
|---|---|---|---|
| `DB_URL` | `db.url` | `jdbc:mysql://localhost:3306/smart_hospital?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8` | JDBC URL |
| `DB_USER` | `db.user` | `root` | database user |
| `DB_PASSWORD` | `db.password` | *(empty)* | database password |
| `PORT` | `port` | `8080` | web server port (hosts set it for you) |
| `APP_USER` | `app.user` | `staff` | website login name |
| `APP_PASSWORD` | `app.password` | *(empty = login off)* | website password - **set it for any public site** |
| `AUTO_INIT_DB` | `auto.init.db` | `true` | create database/tables on start when missing |
| `LOAD_DEMO_DATA` | `load.demo.data` | `true` | load demo data when there are no doctors |

PowerShell example without a file: `$env:DB_PASSWORD="my-password"; .\run.bat`

Passwords are never printed in logs or shown on web pages (only the URL without its query string is printed).

---

## 3. Project structure

```
SmartHospitalSystem/
├── README.md                 this file
├── schema.sql                tables, keys, constraints, indexes
├── seed-demo.sql             FICTIONAL demo data (safe to run more than once)
├── Dockerfile                container build with plain javac (no Maven/Gradle)
├── run.bat / run.sh          one-command build + start (Windows / Linux, macOS)
├── db.properties.example     copy to db.properties (git-ignored) and edit
├── .gitignore  .gitattributes  .dockerignore
├── lib/README.md             how to get the MySQL Connector/J JAR (the JAR itself is not in Git)
├── deploy/README.md          how to put the site on the internet
├── docs/                     PROJECT_WORKFLOW.md, DATABASE_DESIGN.md, TEAM_TASKS.md
├── .vscode/                  settings.json, launch.json for VS Code
└── src/main/
    ├── resources/static/style.css
    └── java/com/hospital/
        ├── Main.java                         start-up
        ├── Config.java                       env variables / db.properties
        ├── database/   DBConnection, DatabaseInitializer
        ├── model/      Patient, Doctor, Visit, Appointment, Recommendation,
        │               RoutingRule, SimilarCase, DoctorWorkload
        ├── dao/        PatientDAO, DoctorDAO, VisitDAO, AppointmentDAO, HistoryDAO,
        │               RoutingRuleDAO, RecommendationDAO          (SQL only)
        ├── service/    PatientService, VisitService, SpecialistMatcher,
        │               HistoricalCaseService, DoctorAssignmentService,
        │               SymptomUtils, ValidationException          (business rules)
        └── web/        WebServer, HtmlPages, FormUtils, AuthFilter
            └── handlers/  BaseHandler, DashboardHandler, PatientHandler, VisitHandler,
                           DoctorHandler, AppointmentHandler, StaticHandler, HealthHandler
```

Beyond the layout requested in the assignment, a few small classes were added because the features needed them:
`Config`, `DatabaseInitializer`, `RecommendationDAO`, the models `RoutingRule`, `SimilarCase` and `DoctorWorkload`,
`SymptomUtils`, `ValidationException`, `AuthFilter`, `BaseHandler`, `StaticHandler` and `HealthHandler`.
Every class has one clear job (see [`docs/TEAM_TASKS.md`](docs/TEAM_TASKS.md)). Inheritance is used in exactly one place
where it has a real purpose: the handlers extend `BaseHandler` so error pages, redirects and 404/405 handling are written once.

---

## 4. Build and run by hand (what `run.bat` does)

You need the driver JAR in `lib/` first ([`lib/README.md`](lib/README.md)).

**Windows PowerShell**

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out -cp "lib/*" -sourcepath src/main/java src/main/java/com/hospital/Main.java
java -cp "out;lib/*;src/main/resources" com.hospital.Main
```

**Linux / macOS**

```bash
mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" -sourcepath src/main/java src/main/java/com/hospital/Main.java
java -cp "out:lib/*:src/main/resources" com.hospital.Main
```

Notes:

* The classpath separator is **`;` on Windows** and **`:` on Linux/macOS**. `lib/*` means "every JAR in lib".
* `src/main/resources` is on the run classpath so the server can find `static/style.css`.
* `-sourcepath` makes `javac` compile every class that `Main` uses. To compile *all* files explicitly instead:
  Linux/macOS `javac -encoding UTF-8 -d out -cp "lib/*" $(find src/main/java -name "*.java")`,
  PowerShell `javac -encoding UTF-8 -d out -cp "lib/*" (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName`.
* Stop the server with **Ctrl+C** (it shuts down cleanly).

### Optional: create the database by hand

```
mysql -u root -p -e "source schema.sql"
mysql -u root -p smart_hospital -e "source seed-demo.sql"
```

(`source` works in PowerShell too; the `< file` redirect does not.) MySQL Workbench: *File > Run SQL Script*.
Both files can be run again at any time: `schema.sql` uses `IF NOT EXISTS`, and `seed-demo.sql` uses fixed IDs with
`INSERT IGNORE`, so nothing is duplicated. To start over completely: `DROP DATABASE smart_hospital;` and run the app again.

---

## 5. VS Code on Windows

1. Install **JDK 17**, **MySQL 8** and **VS Code** with the **Extension Pack for Java** (Microsoft).
2. *File > Open Folder...* and choose the `SmartHospitalSystem` folder.
3. Get the driver into `lib/` (run `.\run.bat` once in the VS Code terminal, or follow `lib/README.md`).
   The file `.vscode/settings.json` already points the Java extension at `src/main/java`, `lib/**/*.jar` and `out`.
   After the JAR is there you should see it under **Java Projects > Referenced Libraries**.
4. Create `db.properties` from `db.properties.example` and set your MySQL password.
5. Open `src/main/java/com/hospital/Main.java` and click **Run** above `main`, or choose **Run Smart Hospital**
   in the *Run and Debug* panel. (Or simply use the terminal: `.\run.bat`.)
6. Browse to <http://localhost:8080/>.

If VS Code shows red underlines under `import java.sql...` after you added the JAR: press `Ctrl+Shift+P` and run
**Java: Clean Java Language Server Workspace**.

---

## 6. What you can do in the website

| Page | Address |
|---|---|
| Dashboard with summary cards and visits waiting for a doctor | `/` |
| Register patient / search patient / patient details with visit history | `/patients/new`, `/patients/search`, `/patients/view?id=1` |
| New eye or skin visit (symptom check boxes + free text) | `/visits/new?patientId=1` |
| Visit page: suggestion + reason, similar visits, doctor, clinical record | `/visits/view?id=37` |
| Similar earlier visits (ranked) | `/history?visitId=37` |
| Doctor directory (filter Eye/Skin, availability switch) | `/doctors` |
| Doctor page: waiting queue and completed | `/doctors/view?id=3` |
| Assign a doctor | `/appointments/new?visitId=46` |
| Waiting queue / all appointments (start, complete, cancel) | `/appointments` |
| Workload per doctor | `/workload` |
| Health check | `/health` |

The full route table is in [`docs/PROJECT_WORKFLOW.md`](docs/PROJECT_WORKFLOW.md).

---

## 7. How the two "smart" features work (short version)

**Similar earlier visits.** Symptoms are normalized (trim, lower case, split on commas, remove duplicates) and compared
with earlier visits of the same department using **Jaccard similarity**:
`shared symptoms / unique symptoms in either visit`. Example: `{floaters, flashes of light}` vs
`{floaters, flashes of light, blurred vision}` = 2 / 3 = 67 %. The score only **ranks** earlier cases; it is *not* the
probability of a disease. A diagnosis is shown for an earlier visit **only if a doctor confirmed it**.

**Specialist suggestion.** Rows of the `routing_rules` table ("keyword -> specialist category") are matched against the
symptoms; the page explains which keywords matched. Red-flag keywords also mark the visit **URGENT**. When nothing matches
the system suggests a *general department review* instead of inventing a specialist. A doctor can accept or reject a suggestion.

**Doctor choice.** Eligible = same department, active, available, and either a *General* doctor or one of the suggested
(not rejected) specializations. Routine patients go to the eligible doctor with the fewest waiting + in-progress patients;
urgent patients are always placed ahead of routine ones in a queue. If nobody is eligible the visit stays pending.
Workload numbers come from the `appointments` table, never from constants.

---

## 8. Demo data and a five-minute demo scenario

`seed-demo.sql` creates 15 fictional patients, 10 fictional doctors (Dr. Meera Nair is **on leave** and Dr. Neha Gupta is
**inactive**, so they must never be assignable), 35 demonstration routing rules, 36 earlier completed visits (with
doctor-confirmed diagnoses, and a few without), and today's queue: 9 visits with appointments (2 in consultation,
2 flagged urgent) and 3 visits still waiting for a doctor.

Follow this scenario (patient and visit numbers assume a fresh database):

1. **Dashboard** `/` - see the cards and the three visits waiting for a doctor (#46, #47, #48).
2. **Register** *Demo Walkin*, age 40, phone `9000099999` -> patient ID **16**. Register the same details again ->
   "already registered", no duplicate.
3. **Find patient**: search `Demo`, then `9000099999`, then a nonsense text.
4. **New visit** for patient 16 -> **Eye** -> tick *floaters* and *flashes of light* -> save (visit **#49**).
   You see: URGENT banner, suggestion **Retina** with the matched keywords as the reason, and similar earlier visits
   ranked by overlap, with the *doctor-confirmed* diagnosis of those earlier visits.
5. Click **Assign doctor**. Eligible: Dr. Anita Rao, Dr. Vikram Menon (general), Dr. Sunita Iyer (retina). **Not** listed:
   Dr. Meera Nair (on leave), Dr. Rahul Desai (glaucoma), any skin doctor. Use *Auto-assign*.
6. **Appointments**: the urgent patient is ahead of routine patients of the same doctor. Press **Start**, then **Complete**.
   Open **Workload** - the counts changed.
7. On visit #49 the assigned doctor writes findings and a diagnosis. The page now shows a green
   **Doctor-confirmed diagnosis** block; suggestions stay labelled "not a diagnosis".
8. **No doctor available**: on **Doctors > Skin** press *Mark not available* for Dr. Kavita Sharma and Dr. Farhan Ali.
   Open **Assign doctor** for visit **#48** (hair loss -> general dermatology): the page says nobody is eligible, the visit
   stays *Pending assignment*. Mark them available again and assign - it works.
9. **No rule matches**: create a skin visit, type `odd tingling` as other symptom -> *No specific routing rule matched*
   and a *general* review is suggested.

---

## 9. Manual testing checklist

Tick each box after you have really tried it. (`[x]` marks nothing by itself - the boxes start empty on purpose.)

- [ ] **1. Application starts** - `run.bat` prints `Open : http://localhost:8080/` and the dashboard loads.
- [ ] **2. Database connection** - terminal shows `[init] Database ready`; `/health` returns `{"status":"UP","database":"UP"}`.
- [ ] **3. Patient registration saves** - register a patient, open `/patients/view?id=<new id>`; also check in MySQL:
  `SELECT * FROM patients ORDER BY patient_id DESC LIMIT 1;`
- [ ] **4. Patient ID generated** - the new patient has the next free ID (16 on a fresh demo database).
- [ ] **5. Search finds the patient** - by ID, by part of the name, by part of the phone number.
- [ ] **6. Invalid patient input rejected** - empty name, age `200` or `abc`, phone `12`, name `<script>`: every case shows a red
  error list and nothing is saved.
- [ ] **7. Eye visits accept only eye data** - the Eye form lists only eye symptoms. Sending a skin keyword to an eye visit
  is refused: `curl.exe -X POST http://localhost:8080/visits -d "patientId=1&department=EYE&symptom=pimples"` -> HTTP 400.
- [ ] **8. Skin visits accept only skin data** - same with a skin visit and the keyword `floaters`.
- [ ] **9. Historical comparison returns relevant records** - visit with *floaters + flashes of light* lists earlier retina
  cases first at 100 %; every row shows shared symptoms and the explanation that the score is a ranking.
- [ ] **10. Rule match gives an explained suggestion** - the suggestion shows *why* (matched keywords) and "demonstration rule".
- [ ] **11. No match gives a safe fallback** - symptom text `odd tingling` -> "No specific routing rule matched", general category only.
- [ ] **12. Confirmed diagnosis is distinguishable from a suggestion** - suggestions are always labelled "not a diagnosis"; only a doctor-entered
  diagnosis appears in the green *Doctor-confirmed diagnosis* box with the doctor's name. A doctor who is not assigned to
  the visit cannot save one.
- [ ] **13. Only qualified, available doctors can be assigned** - Dr. Meera Nair (on leave), Dr. Neha Gupta (inactive), doctors
  of the other department and non-matching specialists never appear; sending their ID by hand is refused (HTTP 400).
- [ ] **14. Appointment status updates** - Start -> Complete changes the badge, the visit status and the dashboard numbers; a
  completed appointment cannot be changed.
- [ ] **15. Workload changes** - `/workload` active count goes +1 when you assign and -1 when you complete or cancel.
- [ ] **16. No available doctor -> pending** - scenario step 8; the visit stays *Pending assignment* and no appointment row exists.
- [ ] **17. Invalid IDs and database errors handled** - `/patients/view?id=abc` (400), `/patients/view?id=99999` (404),
  `/visits/view` (400). Stop MySQL and reload: friendly "Database problem" page, no stack trace, `/health` returns 503.
- [ ] **18. Desktop and mobile** - open the site, press `F12`, toggle the device toolbar (phone size): navigation wraps, tables
  scroll sideways inside their box, forms stay usable, no horizontal page scroll.
- [ ] **19. Rebuild from scratch** - delete the `out` folder, run the documented `javac` / `java` commands (section 4) - the
  site starts again.
- [ ] **20. Deployed app reaches the external database** - after deploying (see `deploy/README.md`) open
  `https://YOUR-SITE/health`: `"database":"UP"`.

---

## 10. Git and GitHub for a team of four

### Upload this project (one person does it, once)

1. **Unzip** the downloaded `SmartHospitalSystem.zip`. (GitHub cannot use a zip as a project - upload the *contents*.)
2. On <https://github.com> click **New repository**, name it `SmartHospitalSystem`, leave "Add a README / .gitignore"
   **unticked**, create it.
3. In a terminal inside the unzipped folder:

```bash
git init
git add .
git status                     # check: db.properties and *.jar must NOT be listed
git commit -m "Initial project: Smart Eye & Skin Specialist Hospital"
git branch -M main
git remote add origin https://github.com/<owner>/SmartHospitalSystem.git
git push -u origin main
```

(Without Git installed you can also drag the unzipped files and folders into the repository page with *Add file > Upload
files*.) Then add the three teammates under **Settings > Collaborators**.

### Everyone else: clone and work on your own branch

```bash
git clone https://github.com/<owner>/SmartHospitalSystem.git
cd SmartHospitalSystem
git checkout -b feature/visits          # one branch per member (see docs/TEAM_TASKS.md)

# ... edit files, run  run.bat  to check it compiles ...

git status
git add src/main/java/com/hospital/service/VisitService.java
git commit -m "Limit similar cases to the same department"
git push -u origin feature/visits       # first push of the branch
```

Open the branch on GitHub, click **Compare & pull request**, ask a teammate to review, then **Merge**.

### Stay in sync and resolve conflicts

```bash
git checkout main
git pull                                # get everybody's merged work
git checkout feature/visits
git merge main                          # bring main into your branch

# if Git reports a conflict: open the file, search for <<<<<<<, keep the right lines,
# delete the <<<<<<< ======= >>>>>>> markers, then:
git add <the-file>
git commit
```

Rules: pull before you push, small commits, never commit `db.properties` or passwords, and make sure the project still
compiles before every push. Nobody changes the schema, package names or the shared signatures without telling the team.

---

## 11. Deployment

GitHub is only the source-code store - **GitHub Pages cannot run a Java server or talk to MySQL.** The `Dockerfile`
builds the app (JDK 17 image, driver downloaded with `curl`, `javac`, no Maven/Gradle) and runs it with `java`; the
app reads `PORT`, binds to `0.0.0.0`, takes the database from `DB_URL` / `DB_USER` / `DB_PASSWORD`, exposes `/health`,
and shuts down cleanly. You also need a separately hosted MySQL database.
Step-by-step instructions, the environment-variable list and a verification checklist are in
[`deploy/README.md`](deploy/README.md). **Set `APP_PASSWORD` for every public deployment**, and use fictional data only.

Local Docker test (if Docker is installed):

```bash
docker build -t smart-hospital .
docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/smart_hospital?useSSL=false&allowPublicKeyRetrieval=true" \
  -e DB_USER=root -e DB_PASSWORD=your-password smart-hospital
```

---

## 12. Security and privacy

* Synthetic data only; no credentials or real records in the repository (`.gitignore` excludes `db.properties`, `.env`,
  dumps, compiled output and the driver JAR).
* All SQL uses `PreparedStatement`; table and column names are never built from user input.
* Every dynamic value is HTML-escaped; there is **no JavaScript** and the `Content-Security-Policy` header forbids scripts
  and inline styles.
* All forms are validated on the server (length, range, allowed values, department-specific symptom lists).
* Form posts from other websites are rejected (`Origin` must match `Host`); pages cannot be framed.
* Optional HTTP Basic login (`APP_PASSWORD`) protects every page except `/health` and the stylesheet.
* Multi-step writes (visit + suggestions, assignment + queue number + visit status, status changes) run in **transactions**;
  the database additionally blocks two active appointments for one visit.
* Errors show a friendly message; details go only to the server log, and passwords are never logged.

---

## 13. Known limitations

* **Not medical software.** Rules are demonstrations; keyword matching is literal (no synonyms or spelling correction;
  similarity compares whole symptom phrases). Nothing here has been clinically validated.
* **One shared login.** There are no per-user accounts or roles. The server checks that clinical notes come from the
  *assigned* doctor's form, but it cannot prove *who* is sitting at the computer. A real system needs individual logins,
  audit logging, encryption at rest, backups and compliance with health-data law.
* Appointments are **queue numbers per doctor per day**, not time slots. The date comes from the database server's clock.
* No editing/deleting of patients, no editing of routing rules in the UI (change `routing_rules` with SQL), no pagination
  (lists show at most 200 rows), a new database connection per request (no pooling), fixed 8 worker threads - fine for a
  demo, not for a busy hospital.
* The visit status `CANCELLED` exists in the schema but the UI does not use it (a cancelled *appointment* returns the visit
  to *Pending assignment*).

---

## 14. Verification status (what was and was not actually tested)

Tested by the author of this package in a Linux sandbox (Ubuntu 24.04, OpenJDK 17, **MySQL Server 8.0.46**):

* all 43 classes compile with plain `javac`;
* `schema.sql` and `seed-demo.sql` run by hand and a second time without errors; the application also created the database,
  tables and demo data by itself from an empty server;
* about 155 automated HTTP checks against the running application passed (registration, validation, search, duplicate
  handling, visits, suggestions, fallback, similar cases, assignment, ineligible/unavailable doctors, queue numbers, status
  changes, rescheduling, workload, pending state, clinical record rules, login, cross-site POST block, 404/405/413 handling,
  database-down behaviour); the HTML of the main pages passed an HTML validator (`tidy`).

**Not tested**, so please test them yourselves:

* The **MySQL Connector/J** JAR itself. Its download server was not reachable from the test environment, so the tests used
  the MariaDB JDBC driver against the real MySQL server. The code uses only standard JDBC calls, but treat Connector/J as
  untested.
* `run.bat` on **Windows**, VS Code, the `Dockerfile` build, and any **hosted deployment**. Nothing has been deployed.
* The pages were validated but not looked at in a real browser on a phone or desktop.
# Skin_Sight_Health_Management_System
