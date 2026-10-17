package com.hospital.dao;

import com.hospital.model.Appointment;
import java.sql.SQLException;
import java.util.List;

/** Member 3: implement appointment INSERT and SELECT statements here. */
public class AppointmentDAO {
    public int bookAppointment(Appointment appointment) throws SQLException {
        // TODO: Insert appointment and return generated appointment_id.
        throw new UnsupportedOperationException("Booking appointments not implemented yet.");
    }

    public List<Appointment> getAllAppointments() throws SQLException {
        // TODO: Join appointments with patients and doctors for a readable list.
        throw new UnsupportedOperationException("Listing appointments not implemented yet.");
    }
}
