-- ============================================================================
-- Smart Eye & Skin Specialist Hospital - database schema (MySQL 8)
--
-- Run by hand:   mysql -u root -p < schema.sql
-- Or let the application do it: it runs this file automatically on first start
-- (see AUTO_INIT_DB in README.md).
--
-- Safe to run more than once: every statement uses IF NOT EXISTS.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS smart_hospital
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_hospital;

-- ---------------------------------------------------------------------------
-- patients
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    patient_id  INT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    age         INT          NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (patient_id),
    CONSTRAINT chk_patient_age CHECK (age BETWEEN 0 AND 120),
    INDEX idx_patient_name  (name),
    INDEX idx_patient_phone (phone)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- doctors
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doctors (
    doctor_id       INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    department      ENUM('EYE','SKIN') NOT NULL,
    specialization  VARCHAR(80)  NOT NULL,
    available       BOOLEAN      NOT NULL DEFAULT TRUE,   -- accepting patients right now
    active          BOOLEAN      NOT NULL DEFAULT TRUE,   -- still works at the hospital
    PRIMARY KEY (doctor_id),
    INDEX idx_doctor_dept   (department, active, available),
    INDEX idx_doctor_spec   (specialization)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- visits (previous AND current visits live in this one table)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS visits (
    visit_id                INT          NOT NULL AUTO_INCREMENT,
    patient_id              INT          NOT NULL,
    department              ENUM('EYE','SKIN') NOT NULL,
    symptoms                TEXT         NOT NULL,                 -- normalized, comma separated
    priority                ENUM('ROUTINE','URGENT') NOT NULL DEFAULT 'ROUTINE',
    clinical_findings       TEXT         NULL,                     -- written by the doctor
    test_results            TEXT         NULL,                     -- written by the doctor
    confirmed_diagnosis     VARCHAR(500) NULL,                     -- ONLY ever typed by a doctor, never by the rule engine
    confirmed_by_doctor_id  INT          NULL,
    confirmed_at            DATETIME     NULL,
    visit_date              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status                  ENUM('PENDING_ASSIGNMENT','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')
                                         NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
    PRIMARY KEY (visit_id),
    CONSTRAINT fk_visit_patient
        FOREIGN KEY (patient_id) REFERENCES patients (patient_id),
    CONSTRAINT fk_visit_confirming_doctor
        FOREIGN KEY (confirmed_by_doctor_id) REFERENCES doctors (doctor_id),
    -- a diagnosis must always name the doctor who confirmed it
    CONSTRAINT chk_dx_has_doctor
        CHECK (confirmed_diagnosis IS NULL OR confirmed_by_doctor_id IS NOT NULL),
    INDEX idx_visit_patient   (patient_id),
    INDEX idx_visit_dept_date (department, visit_date),
    INDEX idx_visit_status    (status, priority)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- routing_rules (explicit, clinician-approved keyword rules - NOT machine learning)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS routing_rules (
    rule_id                   INT          NOT NULL AUTO_INCREMENT,
    department                ENUM('EYE','SKIN') NOT NULL,
    symptom_keyword           VARCHAR(80)  NOT NULL,
    suggested_specialization  VARCHAR(80)  NOT NULL,
    urgent                    BOOLEAN      NOT NULL DEFAULT FALSE,  -- red-flag keyword -> visit marked URGENT
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uq_rule (department, symptom_keyword, suggested_specialization),
    INDEX idx_rule_dept_active (department, active)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- recommendations (rule-based SUGGESTIONS awaiting doctor review)
--   review_status: PENDING  = nobody has looked at it yet
--                  ACCEPTED = a doctor agreed the category is suitable
--                  REJECTED = a doctor disagreed (then it is ignored when choosing doctors)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recommendations (
    recommendation_id         INT           NOT NULL AUTO_INCREMENT,
    visit_id                  INT           NOT NULL,
    suggested_specialization  VARCHAR(80)   NOT NULL,
    reason                    VARCHAR(1000) NOT NULL,
    review_status             ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by_doctor_id     INT           NULL,
    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recommendation_id),
    CONSTRAINT fk_rec_visit
        FOREIGN KEY (visit_id) REFERENCES visits (visit_id),
    CONSTRAINT fk_rec_reviewer
        FOREIGN KEY (reviewed_by_doctor_id) REFERENCES doctors (doctor_id),
    UNIQUE KEY uq_rec_visit_spec (visit_id, suggested_specialization),
    INDEX idx_rec_visit (visit_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- appointments (a place in a doctor's queue)
--   active_visit_key is a generated column: it equals visit_id while the appointment is WAITING or
--   IN_PROGRESS, otherwise NULL. Its UNIQUE index guarantees that a visit never has two active
--   appointments at once, while still allowing a new one after cancellation (rescheduling).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS appointments (
    appointment_id    INT      NOT NULL AUTO_INCREMENT,
    visit_id          INT      NOT NULL,
    doctor_id         INT      NOT NULL,
    queue_number      INT      NOT NULL,
    appointment_date  DATE     NOT NULL,
    status            ENUM('WAITING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'WAITING',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_visit_key  INT GENERATED ALWAYS AS
                      (CASE WHEN status IN ('WAITING','IN_PROGRESS') THEN visit_id ELSE NULL END) STORED,
    PRIMARY KEY (appointment_id),
    CONSTRAINT fk_appt_visit
        FOREIGN KEY (visit_id)  REFERENCES visits  (visit_id),
    CONSTRAINT fk_appt_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors (doctor_id),
    CONSTRAINT chk_queue_positive CHECK (queue_number > 0),
    UNIQUE KEY uq_one_active_appt_per_visit (active_visit_key),
    UNIQUE KEY uq_doctor_day_queue (doctor_id, appointment_date, queue_number),
    INDEX idx_appt_doctor_status (doctor_id, status),
    INDEX idx_appt_visit         (visit_id),
    INDEX idx_appt_date          (appointment_date)
) ENGINE=InnoDB;
