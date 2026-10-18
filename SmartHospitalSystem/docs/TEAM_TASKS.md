# Team tasks - four members, about three days

The project is already complete and runs. These tasks tell each member **which part they own, understand and can
explain in the viva**, and what they should extend or test. Everybody uses the **same schema, packages and method
signatures** listed at the bottom. Nobody creates a second database script or a second connection class.

## Member 1 - Patient registration

| Area | Files |
|---|---|
| Model | `model/Patient.java` |
| Data access | `dao/PatientDAO.java` |
| Business rules | `service/PatientService.java` (validation, duplicate check, search) |
| Web | `web/handlers/PatientHandler.java` (register, search, patient details and visit history) |

You should be able to explain: validation rules (name, age 0-120, phone 7-15 digits), why `PreparedStatement` is used,
how duplicates are avoided (same name + same phone), how `LIKE` wildcards are escaped in the search.

Test checklist items: 3, 4, 5, 6 (see README).
Ideas to extend: edit patient details, show a patient's last visit on the search results.

## Member 2 - Visits and historical cases

| Area | Files |
|---|---|
| Model | `model/Visit.java`, `model/SimilarCase.java` |
| Data access | `dao/VisitDAO.java`, `dao/HistoryDAO.java` |
| Business rules | `service/VisitService.java` (create visit, clinical notes), `service/HistoricalCaseService.java`, `service/SymptomUtils.java` |
| Web | `web/handlers/VisitHandler.java` (visit form, visit page, history page) |

You should be able to explain: symptom normalization, **Jaccard similarity with a worked example**, why the score is a
ranking and not a disease probability, why a diagnosis is shown only when a doctor confirmed it, and how the visit and its
suggestions are saved in one transaction.

Test checklist items: 7, 8, 9, 12.
Ideas to extend: filter similar cases by age group, show the doctor's findings of similar cases.

## Member 3 - Doctors, suggestions, appointments

| Area | Files |
|---|---|
| Model | `model/Doctor.java`, `model/Appointment.java`, `model/Recommendation.java`, `model/RoutingRule.java`, `model/DoctorWorkload.java` |
| Data access | `dao/DoctorDAO.java`, `dao/AppointmentDAO.java`, `dao/RecommendationDAO.java`, `dao/RoutingRuleDAO.java` |
| Business rules | `service/SpecialistMatcher.java`, `service/DoctorAssignmentService.java` |
| Web | `web/handlers/DoctorHandler.java` (directory, doctor page, workload), `web/handlers/AppointmentHandler.java` (assign, queue, status) |

You should be able to explain: how rules produce a suggestion and a safe fallback, the eligibility rule
(department + active + available + general or suggested specialization), lowest-workload choice, how urgent visits are
queued first, queue numbers, row locking inside the assignment transaction, and the status transitions.

Test checklist items: 10, 11, 13, 14, 15, 16.
Ideas to extend: a page to edit routing rules, a "reassign" button that cancels and books in one step.

## Member 4 - Database, integration, testing, deployment

| Area | Files |
|---|---|
| Database | `schema.sql`, `seed-demo.sql`, `database/DBConnection.java`, `database/DatabaseInitializer.java` |
| Start-up and server | `Main.java`, `Config.java`, `web/WebServer.java`, `web/AuthFilter.java` |
| Shared web code | `web/HtmlPages.java`, `web/FormUtils.java`, `web/handlers/BaseHandler.java`, `DashboardHandler.java`, `StaticHandler.java`, `HealthHandler.java`, `resources/static/style.css` |
| Shared service code | `service/ValidationException.java` |
| Repository | `README.md`, `.gitignore`, `Dockerfile`, `run.bat`, `run.sh`, `docs/`, `deploy/` |

You should be able to explain: the three-layer architecture, how a request travels from the browser to MySQL, why the
connection is opened per operation, environment variables vs `db.properties`, the generated-column unique key that blocks
duplicate active appointments, the Docker build, and the login/CSRF/header protections.

Test checklist items: 1, 2, 17, 18, 19, 20. You also keep the `main` branch compiling after every merge.
Ideas to extend: per-user logins, a database backup script.

## Shared contract (do not change without telling the team)

```java
// service layer - what the handlers call
PatientService.register(String name, String age, String phone)               -> RegisterResult
PatientService.search(String term)                                           -> List<Patient>
VisitService.createVisit(int patientId, String dept, List<String> keywords,
                         String other, boolean urgentFlag)                   -> Visit
VisitService.saveClinicalNotes(int visitId, int doctorId, String findings,
                               String tests, String diagnosis)               -> void
VisitService.reviewRecommendation(int visitId, int recommendationId,
                                  int doctorId, String decision)             -> void
HistoricalCaseService.findSimilarCases(int visitId, int limit)               -> List<SimilarCase>
SpecialistMatcher.findMatches(Connection con, String dept, String symptoms)  -> MatchResult
DoctorAssignmentService.assign(int visitId, int doctorIdOr0ForAuto)          -> AssignmentResult
DoctorAssignmentService.updateStatus(int appointmentId, String newStatus)    -> void
DoctorAssignmentService.getWorkloads(String deptOrNull)                      -> List<DoctorWorkload>

// every DAO method takes an open java.sql.Connection as its first parameter;
// only services open and close connections (DBConnection.getConnection()).
```

Package rule: the package declaration always equals the folder path (`com.hospital.service` is in
`src/main/java/com/hospital/service`). Status and department values are the String constants in `Visit`,
`Appointment` and `Recommendation` - never type the literals twice.

## Suggested three-day plan

| Day | Everyone | Goal at the end of the day |
|---|---|---|
| 1 | Install JDK 17 + MySQL, clone, run `run.bat`, click through the whole demo scenario | every member can start the app and knows the folder structure |
| 2 | Read your own files line by line, write your own test cases from the README checklist, make one small improvement on your own branch | each member can explain their part without looking at the code |
| 3 | Merge branches, run the full checklist, rehearse the demo, optional deployment | the `main` branch passes the checklist; slides / viva ready |

## Working agreements

* One branch per member (`feature/patients`, `feature/visits`, `feature/doctors`, `feature/integration`).
* Pull `main` before you start work and before you push.
* Small commits with clear messages (`Add age validation to PatientService`).
* Never commit `db.properties`, passwords, or real patient data.
* Run `javac` (or `run.bat`) before every push - a push that does not compile blocks everyone.
