package com.apexinstitute.service;

import com.apexinstitute.dto.AdminDashboardDto;
import com.apexinstitute.dto.AdminDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbc;

    public AdminDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AdminDashboardDto load() {

        // ---------- 1. Counts ----------
        int students = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM students WHERE is_active = 1", Integer.class));
        int teachers = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM teachers WHERE is_active = 1", Integer.class));
        int classes  = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM classes WHERE is_active = 1", Integer.class));
        int parents  = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM parents WHERE is_active = 1", Integer.class));

        // ---------- 2. Attendance % ----------
        Double att = jdbc.queryForObject("""
                SELECT CASE WHEN COUNT(*) = 0 THEN 0.0
                            ELSE SUM(CASE WHEN status='Present' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)
                       END
                FROM attendance
                """, Double.class);
        double attendancePct = att == null ? 0.0 : round1(att);

        // ---------- 3. Revenue this month ----------
        BigDecimal rev = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payments
                WHERE MONTH(payment_date) = MONTH(SYSDATETIME())
                  AND YEAR(payment_date)  = YEAR(SYSDATETIME())
                """, BigDecimal.class);
        double monthRevenue = rev == null ? 0.0 : rev.doubleValue();

        Stats stats = new Stats(students, teachers, classes, parents, attendancePct, monthRevenue);

        // ---------- 4. Class capacity ----------
        List<ClassCapacity> capacity = jdbc.query("""
                SELECT TOP 5 c.id, c.name,
                       (SELECT COUNT(*) FROM enrollments e
                        WHERE e.class_id = c.id AND e.is_active = 1) AS students
                FROM classes c
                WHERE c.is_active = 1
                ORDER BY (SELECT COUNT(*) FROM enrollments e
                          WHERE e.class_id = c.id AND e.is_active = 1) DESC
                """,
                (rs, i) -> new ClassCapacity(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("students"),
                        30));

        // ---------- 5. Activity feed (mixed sources) ----------
        List<ActivityItem> activity = new ArrayList<>();

        jdbc.query("""
                SELECT TOP 3 u.full_name, u.role, u.created_at
                FROM users u
                WHERE u.role IN ('STUDENT','TEACHER','PARENT')
                ORDER BY u.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        String role = rs.getString("role").toLowerCase();
                        activity.add(new ActivityItem(
                                "add",
                                "New " + role + " registered",
                                rs.getString("full_name") + " joined the platform.",
                                humanTime(rs.getTimestamp("created_at"))));
                    }
                    return null;
                });

        jdbc.query("""
                SELECT TOP 3 n.title, n.audience, n.created_at, u.full_name AS author
                FROM notifications n
                JOIN users u ON u.id = n.created_by
                ORDER BY n.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        activity.add(new ActivityItem(
                                "gold",
                                "Notice: " + rs.getString("title"),
                                "Posted to " + rs.getString("audience")
                                        + " by " + rs.getString("author") + ".",
                                humanTime(rs.getTimestamp("created_at"))));
                    }
                    return null;
                });

        jdbc.query("""
                SELECT TOP 2 e.title, c.name AS class_name, e.created_at
                FROM exams e
                JOIN classes c ON c.id = e.class_id
                ORDER BY e.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        activity.add(new ActivityItem(
                                "edit",
                                "Exam created",
                                rs.getString("title") + " for " + rs.getString("class_name") + ".",
                                humanTime(rs.getTimestamp("created_at"))));
                    }
                    return null;
                });

        // ---------- 6. Announcements ----------
        List<Announcement> anns = jdbc.query("""
                SELECT TOP 5 n.title, n.audience, n.created_at, u.full_name AS author
                FROM notifications n
                JOIN users u ON u.id = n.created_by
                ORDER BY n.created_at DESC
                """,
                (rs, i) -> new Announcement(
                        rs.getString("title"),
                        rs.getString("audience"),
                        humanTime(rs.getTimestamp("created_at")),
                        rs.getString("author")));

        return new AdminDashboardDto(stats, capacity, activity, anns);
    }

    private static int nz(Integer n) { return n == null ? 0 : n; }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }

    private static String humanTime(Timestamp ts) {
        if (ts == null) return "—";
        LocalDateTime dt = ts.toLocalDateTime();
        Duration d = Duration.between(dt, LocalDateTime.now());
        long mins = d.toMinutes();
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + "m ago";
        long h = d.toHours();
        if (h < 24) return h + "h ago";
        long days = d.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + "d ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM"));
    }
}