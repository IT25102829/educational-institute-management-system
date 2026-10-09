package com.apexinstitute.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RfidScanService {

    private final JdbcTemplate jdbc;

    public RfidScanService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Map<String, Object> mark(String uid) {
        if (uid == null || uid.isBlank())
            throw new RuntimeException("Missing UID");

        uid = uid.trim().toUpperCase();

        // 1. Look up student by RFID tag
        Long studentId = jdbc.query(
                "SELECT id FROM students WHERE rfid_tag = ? AND is_active = 1",
                rs -> rs.next() ? rs.getLong("id") : null,
                uid);

        if (studentId == null)
            throw new RuntimeException("Card not registered");

        // 2. Find the class this student belongs to for today's attendance.
        //    We use the student's first active enrollment.
        Long classId = jdbc.query(
                "SELECT TOP 1 class_id FROM enrollments WHERE student_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getLong("class_id") : null,
                studentId);

        if (classId == null)
            throw new RuntimeException("Student not enrolled in any class");

        // 3. Insert or update today's attendance (unique key on student+class+date)
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM attendance
                WHERE student_id = ? AND class_id = ? AND date = CAST(GETDATE() AS DATE)
                """, Integer.class, studentId, classId);

        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE attendance
                    SET status = 'Present', source = 'RFID', updated_at = SYSDATETIME()
                    WHERE student_id = ? AND class_id = ? AND date = CAST(GETDATE() AS DATE)
                    """, studentId, classId);
        } else {
            jdbc.update("""
                    INSERT INTO attendance
                        (student_id, class_id, date, status, source, created_at, updated_at)
                    VALUES (?, ?, CAST(GETDATE() AS DATE), 'Present', 'RFID',
                            SYSDATETIME(), SYSDATETIME())
                    """, studentId, classId);
        }

        // 4. Return student info for the OLED (optional but useful)
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Attendance marked");
        out.put("studentId", jdbc.queryForObject(
                "SELECT student_id FROM students WHERE id = ?", String.class, studentId));
        out.put("uid", uid);
        return out;
    }
}