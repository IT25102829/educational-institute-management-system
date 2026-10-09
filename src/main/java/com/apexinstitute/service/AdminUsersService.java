package com.apexinstitute.service;

import com.apexinstitute.dto.AdminUsersDto;
import com.apexinstitute.dto.AdminUsersDto.Stats;
import com.apexinstitute.dto.AdminUsersDto.UserItem;
import com.apexinstitute.dto.CreateUserRequest;
import com.apexinstitute.dto.UpdateUserRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminUsersService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AdminUsersService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /* ---------- GET /api/admin/users ---------- */
    public AdminUsersDto list() {

        List<UserItem> users = jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.phone, u.role, u.is_active, u.created_at,
                       t.teacher_id
                FROM users u
                LEFT JOIN teachers t ON t.user_id = u.id
                WHERE u.role IN ('ADMIN', 'MANAGEMENT', 'TEACHER')
                ORDER BY
                    CASE u.role
                        WHEN 'ADMIN' THEN 1
                        WHEN 'MANAGEMENT' THEN 2
                        WHEN 'TEACHER' THEN 3
                        ELSE 4
                    END,
                    u.full_name
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    String joined = ts == null ? "—"
                            : ts.toLocalDateTime().format(
                            DateTimeFormatter.ofPattern("d MMM yyyy"));
                    return new UserItem(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("role"),
                            rs.getString("teacher_id"),
                            rs.getBoolean("is_active"),
                            joined);
                });

        int admins     = (int) users.stream().filter(u -> "ADMIN".equals(u.role())).count();
        int management = (int) users.stream().filter(u -> "MANAGEMENT".equals(u.role())).count();
        int teachers   = (int) users.stream().filter(u -> "TEACHER".equals(u.role())).count();
        int active     = (int) users.stream().filter(UserItem::isActive).count();

        Stats stats = new Stats(users.size(), admins, management, teachers, active);

        return new AdminUsersDto(stats, users);
    }

    /* ---------- POST /api/admin/users ---------- */
    @Transactional
    public Long create(CreateUserRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = ?",
                Integer.class, email);
        if (exists != null && exists > 0)
            throw new RuntimeException("Email already in use");

        jdbc.update("""
                INSERT INTO users
                    (username, password_hash, role, full_name, email, phone, is_active,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, SYSDATETIME(), SYSDATETIME())
                """,
                email,
                encoder.encode(req.getPassword()),
                req.getRole(),
                req.getFullName().trim(),
                email,
                req.getPhone());

        Long userId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM users WHERE email = ?", Long.class, email);

        if ("TEACHER".equals(req.getRole())) {
            String teacherCode = nextTeacherCode();
            jdbc.update("""
                    INSERT INTO teachers (user_id, teacher_id, is_active, created_at)
                    VALUES (?, ?, 1, SYSDATETIME())
                    """, userId, teacherCode);
        }

        return userId;
    }

    /* ---------- PUT /api/admin/users/{id} ---------- */
    @Transactional
    public void update(Long userId, UpdateUserRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        String role = jdbc.query("""
                SELECT role FROM users WHERE id = ?
                """, rs -> rs.next() ? rs.getString("role") : null, userId);
        if (role == null)
            throw new RuntimeException("User not found");
        if ("STUDENT".equals(role) || "PARENT".equals(role))
            throw new RuntimeException("Use the student page for this user");

        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users WHERE LOWER(email) = ? AND id <> ?
                """, Integer.class, email, userId);
        if (dup != null && dup > 0)
            throw new RuntimeException("Email already in use");

        jdbc.update("""
                UPDATE users
                SET full_name = ?, email = ?, username = ?, phone = ?, updated_at = SYSDATETIME()
                WHERE id = ?
                """, req.getFullName().trim(), email, email, req.getPhone(), userId);

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            jdbc.update("""
                    UPDATE users SET password_hash = ?, updated_at = SYSDATETIME()
                    WHERE id = ?
                    """, encoder.encode(req.getNewPassword()), userId);
        }
    }

    /* ---------- PATCH /api/admin/users/{id}/toggle ---------- */
    @Transactional
    public void toggleActive(Long userId) {
        Integer updated = jdbc.update("""
                UPDATE users
                SET is_active = CASE WHEN is_active = 1 THEN 0 ELSE 1 END,
                    updated_at = SYSDATETIME()
                WHERE id = ? AND role IN ('ADMIN','MANAGEMENT','TEACHER')
                """, userId);
        if (updated == 0)
            throw new RuntimeException("User not found or cannot be toggled");
    }

    /* ---------- DELETE /api/admin/users/{id} ---------- */
    @Transactional
    public void delete(Long userId) {

        // 1. Verify user exists and is staff
        String role = jdbc.query("""
                SELECT role FROM users WHERE id = ?
                """, rs -> rs.next() ? rs.getString("role") : null, userId);
        if (role == null)
            throw new RuntimeException("User not found");
        if ("STUDENT".equals(role) || "PARENT".equals(role))
            throw new RuntimeException("Use the students page for this account");

        // 2. Prevent self-deletion (would lock you out)
        try {
            Long currentUserId = Long.parseLong(
                    SecurityContextHolder.getContext().getAuthentication().getName());
            if (currentUserId.equals(userId))
                throw new RuntimeException("You cannot delete your own account");
        } catch (NumberFormatException ignored) { /* not a numeric ID, skip */ }

        // ----------------------------------------------------------
        // Clear every FK to users that is NOT ON DELETE CASCADE.
        // Order matters: children before parents.
        // ----------------------------------------------------------

        // attendance_logs.changed_by → users (no cascade)
        jdbc.update("DELETE FROM attendance_logs WHERE changed_by = ?", userId);

        // attendance.marked_by → users (no cascade)
        jdbc.update("DELETE FROM attendance WHERE marked_by = ?", userId);

        // exam_results.entered_by → users (no cascade)
        // Covers marks this teacher entered on OTHER teachers' exams.
        // Results for exams they created cascade away in the next step.
        jdbc.update("DELETE FROM exam_results WHERE entered_by = ?", userId);

        // exams.created_by → users (no cascade)
        // Cascades to exam_questions and exam_results for those exams.
        jdbc.update("DELETE FROM exams WHERE created_by = ?", userId);

        // class_files.uploaded_by → users (no cascade)
        jdbc.update("DELETE FROM class_files WHERE uploaded_by = ?", userId);

        // payments.recorded_by → users (no cascade)
        jdbc.update("DELETE FROM payments WHERE recorded_by = ?", userId);

        // teacher_salaries.recorded_by → users (no cascade)
        jdbc.update("DELETE FROM teacher_salaries WHERE recorded_by = ?", userId);

        // notifications.created_by → users (no cascade)
        // Cascades to notification_recipients via FK.
        jdbc.update("DELETE FROM notifications WHERE created_by = ?", userId);

        // ----------------------------------------------------------
        // Final delete — cascades handle the rest:
        //   users      → teachers (CASCADE)
        //   teachers   → timetable (CASCADE)
        //   teachers   → teacher_salaries (CASCADE)
        //   users      → notification_recipients (CASCADE)
        // ----------------------------------------------------------
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    /* ---------- helpers ---------- */
    private String nextTeacherCode() {
        String top = jdbc.query("""
                SELECT TOP 1 teacher_id FROM teachers
                ORDER BY id DESC
                """, rs -> rs.next() ? rs.getString("teacher_id") : null);

        int next = 1;
        if (top != null && top.length() > 1) {
            try {
                next = Integer.parseInt(top.substring(1)) + 1;
            } catch (NumberFormatException ignored) { /* keep 1 */ }
        }
        return "T" + String.format("%04d", next);
    }
}