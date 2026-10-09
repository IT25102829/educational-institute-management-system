package com.apexinstitute.service;

import com.apexinstitute.dto.*;
import com.apexinstitute.dto.AdminStudentsDto.StudentItem;
import com.apexinstitute.dto.AdminStudentsDto.Stats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminStudentsService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AdminStudentsService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /* ---------- GET /api/admin/students ---------- */
    public AdminStudentsDto list() {

        List<StudentItem> students = jdbc.query("""
                SELECT s.id, s.student_id, s.grade, s.rfid_tag, s.is_active, s.created_at,
                       u.full_name, u.email, u.phone,
                       (SELECT COUNT(*) FROM enrollments en
                        WHERE en.student_id = s.id AND en.is_active = 1) AS enrolled
                FROM students s
                JOIN users u ON u.id = s.user_id
                ORDER BY s.student_id
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    String joined = ts == null ? "—"
                            : ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    return new StudentItem(
                            rs.getLong("id"),
                            rs.getString("student_id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("grade"),
                            rs.getString("rfid_tag"),
                            rs.getInt("enrolled"),
                            rs.getBoolean("is_active"),
                            joined);
                });

        int active   = (int) students.stream().filter(StudentItem::isActive).count();
        int withRfid = (int) students.stream().filter(s -> s.rfidTag() != null).count();
        int grade10Plus = students.size();

        Stats stats = new Stats(students.size(), active, withRfid, grade10Plus);

        return new AdminStudentsDto(stats, students);
    }

    /* ---------- POST /api/admin/students ---------- */
    @Transactional
    public Long create(CreateStudentRequest req) {

        String code  = req.getStudentCode().trim().toUpperCase();
        String email = req.getEmail().trim().toLowerCase();
        String rfid  = req.getRfidTag() == null || req.getRfidTag().isBlank()
                ? null : req.getRfidTag().trim();

        /* ---------- Student uniqueness ---------- */
        Integer codeDup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM students WHERE student_id = ?",
                Integer.class, code);
        if (codeDup != null && codeDup > 0)
            throw new RuntimeException("Student ID already in use");

        Integer emailDup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = ?",
                Integer.class, email);
        if (emailDup != null && emailDup > 0)
            throw new RuntimeException("Student email already in use");

        if (rfid != null) {
            Integer rfidDup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM students WHERE rfid_tag = ?",
                    Integer.class, rfid);
            if (rfidDup != null && rfidDup > 0)
                throw new RuntimeException("RFID tag already in use");
        }

        /* ---------- 1. Student user ---------- */
        String studentHash = encoder.encode(req.getPassword());

        jdbc.update("""
                INSERT INTO users
                    (username, password_hash, role, full_name, email, phone,
                     is_active, created_at, updated_at)
                VALUES (?, ?, 'STUDENT', ?, ?, ?, 1, SYSDATETIME(), SYSDATETIME())
                """,
                code, studentHash, req.getFullName().trim(), email, req.getPhone().trim());

        Long userId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM users WHERE LOWER(email) = ?", Long.class, email);

        /* ---------- 2. Student row ---------- */
        jdbc.update("""
                INSERT INTO students
                    (user_id, student_id, grade, rfid_tag, is_active, created_at)
                VALUES (?, ?, ?, ?, 1, SYSDATETIME())
                """,
                userId, code, req.getGrade(), rfid);

        Long studentId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM students WHERE student_id = ?", Long.class, code);

        /* ---------- 3. Parent: find by NIC, create if missing ---------- */
        String parentNic   = req.getParentNic().trim();
        String parentEmail = req.getParentEmail().trim().toLowerCase();

        Long parentId = jdbc.query(
                "SELECT id FROM parents WHERE nic = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                parentNic);

        if (parentId == null) {
            Integer parentEmailDup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(email) = ?",
                    Integer.class, parentEmail);
            if (parentEmailDup != null && parentEmailDup > 0)
                throw new RuntimeException("Parent email already in use");

            String parentHash = encoder.encode(req.getParentPassword());

            jdbc.update("""
                    INSERT INTO users
                        (username, password_hash, role, full_name, email, phone,
                         is_active, created_at, updated_at)
                    VALUES (?, ?, 'PARENT', ?, ?, NULL, 1, SYSDATETIME(), SYSDATETIME())
                    """,
                    parentEmail, parentHash, req.getParentFullName().trim(), parentEmail);

            Long parentUserId = jdbc.queryForObject(
                    "SELECT TOP 1 id FROM users WHERE LOWER(email) = ?",
                    Long.class, parentEmail);

            jdbc.update("""
                    INSERT INTO parents
                        (user_id, nic, is_active, created_at)
                    VALUES (?, ?, 1, SYSDATETIME())
                    """,
                    parentUserId, parentNic);

            parentId = jdbc.queryForObject(
                    "SELECT TOP 1 id FROM parents WHERE user_id = ?",
                    Long.class, parentUserId);
        }

        /* ---------- 4. Link parent ↔ student ---------- */
        Integer linkExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parent_child WHERE parent_id = ? AND student_id = ?",
                Integer.class, parentId, studentId);
        if (linkExists == null || linkExists == 0) {
            jdbc.update("""
                    INSERT INTO parent_child (parent_id, student_id, created_at)
                    VALUES (?, ?, SYSDATETIME())
                    """,
                    parentId, studentId);
        }

        return studentId;
    }

    /* ---------- PATCH /api/admin/students/{id}/rfid ---------- */
    @Transactional
    public void issueRfid(Long studentId, IssueRfidRequest req) {
        String rfid = req.getRfidTag().trim();

        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM students WHERE rfid_tag = ? AND id <> ?
                """, Integer.class, rfid, studentId);
        if (dup != null && dup > 0)
            throw new RuntimeException("RFID tag already in use");

        int updated = jdbc.update("""
                UPDATE students SET rfid_tag = ? WHERE id = ?
                """, rfid, studentId);
        if (updated == 0) throw new RuntimeException("Student not found");
    }

    /* ---------- PATCH /api/admin/students/{id}/toggle ---------- */
    @Transactional
    public void toggleActive(Long studentId) {
        Long userId = jdbc.query("""
            SELECT user_id FROM students WHERE id = ?
            """, rs -> rs.next() ? rs.getLong("user_id") : null, studentId);
        if (userId == null) throw new RuntimeException("Student not found");

        jdbc.update("""
            UPDATE users
            SET is_active = CASE WHEN is_active = 1 THEN 0 ELSE 1 END,
                updated_at = SYSDATETIME()
            WHERE id = ?
            """, userId);

        jdbc.update("""
            UPDATE students
            SET is_active = CASE WHEN is_active = 1 THEN 0 ELSE 1 END
            WHERE id = ?
            """, studentId);
    }

    /* ---------- DELETE /api/admin/students/{id} ---------- */
    @Transactional
    public void delete(Long studentId) {

        Long userId = jdbc.query("""
                SELECT user_id FROM students WHERE id = ?
                """, rs -> rs.next() ? rs.getLong("user_id") : null, studentId);
        if (userId == null) throw new RuntimeException("Student not found");

        // 1. Unlink parent (parent account stays)
        jdbc.update("DELETE FROM parent_child WHERE student_id = ?", studentId);

        // 2. Attendance records
        jdbc.update("DELETE FROM attendance WHERE student_id = ?", studentId);

        // 3. Exam results
        jdbc.update("DELETE FROM exam_results WHERE student_id = ?", studentId);

        // 4. Class enrollments
        jdbc.update("DELETE FROM enrollments WHERE student_id = ?", studentId);

        // 5. Student row
        jdbc.update("DELETE FROM students WHERE id = ?", studentId);

        // 6. User (login) row
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    /* ---------- GET /api/admin/attendance/today ---------- */
    public AdminAttendanceDto todayRfidAttendance() {

        String date = jdbc.queryForObject(
                "SELECT CONVERT(varchar(10), CAST(GETDATE() AS DATE), 120)",
                String.class);

        List<AdminAttendanceDto.Record> records = jdbc.query("""
                SELECT s.student_id,
                       u.full_name,
                       u.email,
                       s.grade,
                       a.status,
                       a.source,
                       CONVERT(varchar(8), a.created_at, 108) AS tap_time
                FROM attendance a
                JOIN students s ON s.id = a.student_id
                JOIN users    u ON u.id = s.user_id
                WHERE a.date = CAST(GETDATE() AS DATE)
                  AND a.source = 'RFID'
                ORDER BY a.created_at DESC
                """,
                (rs, i) -> new AdminAttendanceDto.Record(
                        rs.getString("student_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("grade"),
                        rs.getString("status"),
                        rs.getString("source"),
                        rs.getString("tap_time")));

        return new AdminAttendanceDto(date, records);
    }
}