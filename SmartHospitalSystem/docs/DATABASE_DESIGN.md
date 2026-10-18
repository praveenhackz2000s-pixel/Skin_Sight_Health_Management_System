# Database design

Database name: **`smart_hospital`** (MySQL 8, InnoDB, `utf8mb4`).
Files: `schema.sql` (tables) and `seed-demo.sql` (fictional data). Both can be re-run safely.

## Relationships

```
patients 1 ----< visits 1 ----< recommendations
                    |  \
                    |   `----< appointments >---- 1 doctors
                    |                                 |
                    `-- confirmed_by_doctor_id -------'      (a diagnosis always names a doctor)
                         recommendations.reviewed_by_doctor_id ---> doctors

routing_rules  (stand-alone lookup table: keyword -> specialist category)
```

## Tables

### patients
| Column | Type | Notes |
|---|---|---|
| patient_id | INT, PK, auto-increment | shown to staff as the patient ID |
| name | VARCHAR(100) NOT NULL | |
| age | INT NOT NULL | `CHECK (age BETWEEN 0 AND 120)` |
| phone | VARCHAR(20) NOT NULL | stored as digits with an optional leading `+` |
| created_at | DATETIME | default `CURRENT_TIMESTAMP` |

Indexes: `name`, `phone` (search).

### doctors
| Column | Type | Notes |
|---|---|---|
| doctor_id | INT, PK | |
| name | VARCHAR(100) | |
| department | ENUM('EYE','SKIN') | |
| specialization | VARCHAR(80) | e.g. General Ophthalmology, Retina, Glaucoma, General Dermatology, Acne, Inflammatory Skin Conditions |
| available | BOOLEAN | accepting patients right now (toggle on the Doctors page) |
| active | BOOLEAN | still works at the hospital; inactive doctors are never assignable |

A specialization that **starts with "General"** is the *general category*: such a doctor can receive any case of
their department.

### visits  (past and current visits live in this one table)
| Column | Type | Notes |
|---|---|---|
| visit_id | INT, PK | |
| patient_id | FK -> patients | |
| department | ENUM('EYE','SKIN') | |
| symptoms | TEXT | normalized, comma separated: `blurred vision, eye pain` |
| priority | ENUM('ROUTINE','URGENT') | URGENT when a red-flag keyword matched or reception flagged it |
| clinical_findings | TEXT NULL | written by the assigned doctor |
| test_results | TEXT NULL | written by the assigned doctor |
| confirmed_diagnosis | VARCHAR(500) NULL | **only ever typed by a doctor**; the rule engine never writes it |
| confirmed_by_doctor_id | FK -> doctors NULL | who confirmed it |
| confirmed_at | DATETIME NULL | |
| visit_date | DATETIME | default now |
| status | ENUM | see below |

Constraint `chk_dx_has_doctor`: a diagnosis without a confirming doctor is impossible.

Visit status values:

| Status | Meaning |
|---|---|
| PENDING_ASSIGNMENT | saved, no doctor yet (also the state after an appointment is cancelled) |
| ASSIGNED | has a WAITING appointment |
| IN_PROGRESS | the doctor started the consultation |
| COMPLETED | consultation finished |
| CANCELLED | reserved for administrative cancellation (not used by the UI) |

### routing_rules
| Column | Type | Notes |
|---|---|---|
| rule_id | INT, PK | |
| department | ENUM | |
| symptom_keyword | VARCHAR(80) | controlled keyword, lower case |
| suggested_specialization | VARCHAR(80) | |
| urgent | BOOLEAN | red-flag keyword: the visit becomes URGENT |
| active | BOOLEAN | rules can be switched off without deleting them |

`UNIQUE (department, symptom_keyword, suggested_specialization)`.
The check boxes on the visit form are generated from the active keywords of the department, so the lists
can be changed with SQL alone. **The sample rules are demonstration rules and need clinical validation.**

### recommendations  (suggestions, never diagnoses)
| Column | Type | Notes |
|---|---|---|
| recommendation_id | INT, PK | |
| visit_id | FK -> visits | |
| suggested_specialization | VARCHAR(80) | |
| reason | VARCHAR(1000) | which keywords matched, in words |
| review_status | ENUM('PENDING','ACCEPTED','REJECTED') | PENDING = nobody reviewed; ACCEPTED/REJECTED = a doctor of the department decided |
| reviewed_by_doctor_id | FK -> doctors NULL | |
| created_at | DATETIME | |

`UNIQUE (visit_id, suggested_specialization)`. A REJECTED suggestion is ignored when eligible doctors are chosen.

### appointments
| Column | Type | Notes |
|---|---|---|
| appointment_id | INT, PK | |
| visit_id | FK -> visits | |
| doctor_id | FK -> doctors | |
| queue_number | INT | 1, 2, 3 ... per doctor per day; shown as `Q-003`; never reused |
| appointment_date | DATE | |
| status | ENUM('WAITING','IN_PROGRESS','COMPLETED','CANCELLED') | |
| created_at | DATETIME | |
| active_visit_key | generated column | `visit_id` while WAITING/IN_PROGRESS, else NULL |

Two unique keys do the important work:

* `uq_one_active_appt_per_visit (active_visit_key)` - the database itself refuses a **second active appointment for
  the same visit**. After a cancellation `active_visit_key` becomes NULL, so a new appointment (rescheduling) is allowed.
* `uq_doctor_day_queue (doctor_id, appointment_date, queue_number)` - queue numbers are unique per doctor per day.

## Workload is never stored

"Number of patients per doctor" is calculated on demand:

```sql
SELECT d.doctor_id, d.name,
       COALESCE(SUM(a.status = 'WAITING'), 0)     AS waiting,
       COALESCE(SUM(a.status = 'IN_PROGRESS'), 0) AS in_progress,
       COALESCE(SUM(a.status = 'COMPLETED'), 0)   AS completed
FROM doctors d LEFT JOIN appointments a ON a.doctor_id = d.doctor_id
GROUP BY d.doctor_id, d.name;
```

Active patients = waiting + in progress.

## Useful queries for demos

```sql
-- who is waiting for which doctor, urgent first
SELECT d.name, a.queue_number, p.name AS patient, v.priority, a.status
FROM appointments a
JOIN doctors d ON d.doctor_id = a.doctor_id
JOIN visits v ON v.visit_id = a.visit_id
JOIN patients p ON p.patient_id = v.patient_id
WHERE a.status IN ('WAITING','IN_PROGRESS')
ORDER BY d.name, (v.priority = 'URGENT') DESC, a.queue_number;

-- visits that still need a doctor
SELECT visit_id, department, priority, symptoms FROM visits WHERE status = 'PENDING_ASSIGNMENT';
```
