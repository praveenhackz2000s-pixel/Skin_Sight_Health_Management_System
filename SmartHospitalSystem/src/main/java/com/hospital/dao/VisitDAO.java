package com.hospital.dao;

import com.hospital.model.Visit;
import java.sql.SQLException;
import java.util.List;

/** Member 2: implement visit INSERT and historical visit SELECT statements here. */
public class VisitDAO {
    public int addVisit(Visit visit) throws SQLException {
        // TODO: Insert the visit and return generated visit_id.
        throw new UnsupportedOperationException("Adding visits not implemented yet.");
    }

    public List<Visit> getVisitsByPatientId(int patientId) throws SQLException {
        // TODO: Retrieve visits ordered by visit_date, newest first.
        throw new UnsupportedOperationException("Visit history not implemented yet.");
    }
}
