CREATE DATABASE IF NOT EXISTS smart_hospital;
USE smart_hospital;

CREATE TABLE IF NOT EXISTS patients (
    patient_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    phone VARCHAR(15),
    CHECK (age >= 0 AND age <= 120)
);

CREATE TABLE IF NOT EXISTS doctors (
    doctor_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(10) NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (department IN ('EYE', 'SKIN'))
);

CREATE TABLE IF NOT EXISTS visits (
    visit_id INT PRIMARY KEY AUTO_INCREMENT,
    patient_id INT NOT NULL,
    department VARCHAR(10) NOT NULL,
    symptoms VARCHAR(500) NOT NULL,
    visit_date DATE NOT NULL,
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
    CHECK (department IN ('EYE', 'SKIN'))
);

CREATE TABLE IF NOT EXISTS appointments (
    appointment_id INT PRIMARY KEY AUTO_INCREMENT,
    patient_id INT NOT NULL,
    doctor_id INT NOT NULL,
    appointment_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

-- Fictional demo doctors for testing; replace/add according to the project demo.
INSERT INTO doctors (name, department, specialization)
SELECT 'Dr. Demo Eye', 'EYE', 'General Ophthalmology'
WHERE NOT EXISTS (
    SELECT 1 FROM doctors WHERE name = 'Dr. Demo Eye' AND department = 'EYE'
);

INSERT INTO doctors (name, department, specialization)
SELECT 'Dr. Demo Skin', 'SKIN', 'General Dermatology'
WHERE NOT EXISTS (
    SELECT 1 FROM doctors WHERE name = 'Dr. Demo Skin' AND department = 'SKIN'
);
