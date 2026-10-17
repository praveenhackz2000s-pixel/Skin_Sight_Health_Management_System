package com.hospital.dao;

import com.hospital.model.Patient;
import java.sql.SQLException;

/** Member 1: implement patient INSERT and SELECT statements here. */
public class PatientDAO {
    public int registerPatient(Patient patient) throws SQLException {
        // TODO: Use DBConnection + PreparedStatement; return generated patient_id.
        throw new UnsupportedOperationException("Patient registration not implemented yet.");
    }

    public Patient searchPatientById(int patientId) throws SQLException {
        // TODO: Query patients by patient_id and map the row to Patient.
        throw new UnsupportedOperationException("Patient search not implemented yet.");
    }
}
