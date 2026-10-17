package com.hospital.dao;

import com.hospital.model.Doctor;
import java.sql.SQLException;
import java.util.List;

/** Member 3: implement doctor lookup here. */
public class DoctorDAO {
    public List<Doctor> getDoctorsByDepartment(String department) throws SQLException {
        // TODO: Query active doctors for EYE or SKIN using PreparedStatement.
        throw new UnsupportedOperationException("Doctor listing not implemented yet.");
    }
}
