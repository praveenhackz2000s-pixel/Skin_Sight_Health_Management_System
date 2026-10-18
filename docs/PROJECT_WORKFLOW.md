# Project workflow - how the system works end to end

## 1. Request path (three layers)

```
Browser  ->  HttpServer (com.sun.net.httpserver)  ->  Handler  ->  Service  ->  DAO  ->  JDBC  ->  MySQL
   ^                                                     |
   '------------- HTML built by HtmlPages <--------------'
```

* **Handler** (`web/handlers`): reads the URL / form, calls a service, builds the HTML page, redirects after a POST.
* **Service** (`service`): validation, business rules, transactions. Opens the connection with try-with-resources.
* **DAO** (`dao`): only SQL (`PreparedStatement`). Receives the connection from the service.
* **Model** (`model`): plain Java objects with private fields, constructors, getters, setters.
* **AuthFilter**: runs before every handler (security headers, same-origin check, optional login).

Every successful POST ends with a **303 redirect** to a normal GET page (Post/Redirect/Get), so refreshing the browser
never repeats a form submission.

## 2. Reception workflow

1. **Search** (`/patients/search`) by ID, name or phone.
2. Not found -> **Register** (`/patients/new`). Validation: name letters only, age 0-120, phone 7-15 digits.
   Same name + same phone as an existing patient -> no duplicate is created; the existing record is opened.
3. **New visit** (`/visits/new?patientId=..`): choose Eye or Skin, tick symptoms (the check boxes are the active routing
   keywords of that department) and optionally type other symptoms. Reception can flag the visit as urgent.
4. **Save** (`POST /visits`), one database transaction:
   normalize symptoms -> match routing rules -> insert visit -> insert suggestions -> commit.
5. The **visit page** shows the suggestion with its reason, similar earlier visits, and an "Assign doctor" button.
6. **Assign** (`/appointments/new?visitId=..`): the page lists *eligible* doctors with their workload. Reception can
   pick one or use auto-assign. If nobody is eligible the visit simply stays `PENDING_ASSIGNMENT` and remains on the
   dashboard.

## 3. Doctor workflow

1. `/doctors/view?id=..` shows the doctor's waiting queue (in consultation first, urgent next, then queue number).
2. **Start** -> **Complete** (or **Cancel**) buttons move the appointment; the visit status follows automatically.
3. On the visit page a doctor of the department can **accept / reject** a suggestion.
4. The **assigned** doctor enters clinical findings, test results and - if they decide to - the *confirmed diagnosis*.
5. The confirmed diagnosis is shown in green as "Doctor-confirmed diagnosis". Suggestions are always labelled
   "not a diagnosis".

## 4. State machines

Appointment:

```
WAITING --start--> IN_PROGRESS --complete--> COMPLETED
   |                    |
   '--cancel--> CANCELLED <--cancel--'
```

Visit status follows the appointment: WAITING -> `ASSIGNED`, IN_PROGRESS -> `IN_PROGRESS`, COMPLETED -> `COMPLETED`,
CANCELLED -> back to `PENDING_ASSIGNMENT` (that is how **rescheduling** works: cancel, then assign again).
COMPLETED and CANCELLED appointments cannot be changed.

## 5. Algorithms (all simple and explainable)

### 5.1 Symptom normalization (`SymptomUtils`)
trim -> lower case -> split on `, ; | newline` -> drop unusual characters -> collapse spaces -> remove duplicates.
`"  Blurred Vision ,EYE pain; "` becomes `blurred vision, eye pain`.

### 5.2 Historical case comparison (`HistoricalCaseService`)
Compare the current symptom set with every earlier visit of the **same department** (newest 2000):

```
similarity = shared symptoms / unique symptoms in either set        (Jaccard)
```

Worked example:

| | symptoms |
|---|---|
| current visit | floaters, flashes of light |
| earlier visit | floaters, flashes of light, blurred vision |

shared = 2, union = 3 -> similarity = 2/3 = **67 %**.

The list is sorted by this score (ties: newest first) and cut to the top 5 (visit page) or 10 (history page).
**The percentage is only a ranking of earlier cases. It is never the probability of a disease.**
A diagnosis is displayed for an earlier visit only if a doctor confirmed it; otherwise the page says
"No confirmed diagnosis recorded".

### 5.3 Rule-based specialist suggestion (`SpecialistMatcher`)
For every **active** rule of the department: if the rule's keyword is one of the symptom phrases, or appears in a
phrase as whole words, it matches. Matches are grouped by specialization; each group becomes one stored suggestion
with a sentence explaining which keywords matched. A matched *red-flag* rule also marks the visit URGENT.
**No match -> a general department review is suggested** ("No specific routing rule matched ...") - the system never
invents a specialist.

### 5.4 Eligible doctors and assignment (`DoctorAssignmentService`)
A doctor is eligible when **all** are true:

1. same department as the visit,
2. `active = TRUE` and `available = TRUE`,
3. specialization starts with "General" **or** equals a suggested specialization that no doctor has rejected.

Routine assignment picks the eligible doctor with the fewest active patients (waiting + in progress; ties -> lowest
doctor ID). Urgent visits are always ordered ahead of routine ones in the queue, so balancing workload can never push
an urgent patient behind a routine one. The service then, in **one transaction**:

1. locks the visit row (two receptionists cannot book the same visit twice),
2. re-checks that no active appointment exists,
3. locks the doctor row and re-checks `active/available`,
4. computes the next queue number for that doctor today,
5. inserts the appointment and sets the visit to ASSIGNED.

## 6. Pages and routes

| Route | Purpose |
|---|---|
| `GET /` | dashboard, pending visits, recent patients |
| `GET /patients/new`, `POST /patients` | register |
| `GET /patients/search?q=` | search |
| `GET /patients/view?id=` | details + visit history |
| `GET /visits/new?patientId=[&department=]` | department choice, visit form |
| `POST /visits` | save visit + suggestions |
| `GET /visits/view?id=` | visit page (suggestions, similar cases, doctor, clinical record) |
| `POST /visits/review` | doctor accepts / rejects a suggestion |
| `POST /visits/clinical` | assigned doctor saves findings, tests, diagnosis |
| `GET /history?visitId=` | similar earlier visits (top 10) |
| `GET /doctors[?department=]` | doctor directory with active patient counts |
| `POST /doctors/availability` | mark available / not available |
| `GET /doctors/view?id=` | one doctor: queue and completed |
| `GET /appointments/new?visitId=` | assignment page |
| `POST /appointments` | create appointment (`doctorId=auto` or an ID) |
| `GET /appointments[?show=all&department=&doctorId=]` | waiting queue / history |
| `POST /appointments/status` | start, complete, cancel |
| `GET /workload[?department=]` | workload of every doctor |
| `GET /health` | health check (JSON, no secrets) |

## 7. Safety principles built into the design

* The system is **decision support**. It never diagnoses, never prescribes, and never writes `confirmed_diagnosis`.
* Every suggestion carries its reason and the words "demonstration rule - requires clinical validation".
* Only the **assigned doctor** can save clinical notes; only a doctor of the **same department** can review a suggestion.
* All SQL uses `PreparedStatement`; all dynamic text is HTML-escaped; forms are validated on the server.
* Error pages never show SQL, stack traces, URLs with credentials or passwords.
