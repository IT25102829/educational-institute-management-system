package com.apexinstitute.service;

import com.apexinstitute.dto.StudentDashboardDto;
import com.apexinstitute.dto.StudentDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StudentDashboardDto forCurrentStudent() {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        // ---------- 1. Student summary ----------
        StudentSummary student = jdbc.queryForObject("""
                SELECT s.id, s.student_id, s.grade, u.full_name
                FROM students s
                JOIN users u ON u.id = s.user_id
                WHERE s.user_id = ?
                """,
                (rs, i) -> new StudentSummary(
                        rs.getLong("id"),
                        rs.getString("student_id"),
                        rs.getString("full_name"),
                        rs.getString("grade")),
                userId);

        if (student == null) throw new RuntimeException("Student record not found");
        Long studentPk = student.id();

        // ---------- 2. Stats ----------
        Map<String, Object> att = jdbc.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COALESCE(SUM(CASE WHEN status = 'Present' THEN 1 ELSE 0 END), 0) AS present
                FROM attendance
                WHERE student_id = ?
                """, studentPk);

        long total   = ((Number) att.get("total")).longValue();
        long present = ((Number) att.get("present")).longValue();
        double attendancePct = total == 0 ? 0.0
                : Math.round(present * 1000.0 / total) / 10.0;

        Integer enrolledClasses = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enrollments
                WHERE student_id = ? AND is_active = 1
                """, Integer.class, studentPk);

        Integer upcomingExams = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams e
                JOIN enrollments en ON en.class_id = e.class_id
                WHERE en.student_id = ? AND en.is_active = 1 AND e.is_published = 1
                """, Integer.class, studentPk);

        Integer unpaidInvoices = jdbc.queryForObject("""
                SELECT COUNT(*) FROM invoices
                WHERE student_id = ? AND status = 'unpaid'
                """, Integer.class, studentPk);

        String feeStatus = (unpaidInvoices == 0) ? "Paid" : "Due";

        Stats stats = new Stats(
                attendancePct,
                enrolledClasses == null ? 0 : enrolledClasses,
                upcomingExams == null ? 0 : upcomingExams,
                feeStatus);

        // ---------- 3. Today's schedule ----------
        String today = LocalDate.now().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        List<ScheduleItem> scheduleRaw = jdbc.query("""
                SELECT t.start_time, t.end_time,
                       s.name  AS subject,
                       u.full_name AS teacher,
                       r.name  AS room
                FROM timetable t
                JOIN classes   c  ON c.id = t.class_id
                JOIN subjects  s  ON s.id = c.subject_id
                JOIN teachers  te ON te.id = t.teacher_id
                JOIN users     u  ON u.id = te.user_id
                JOIN rooms     r  ON r.id = t.room_id
                JOIN enrollments en ON en.class_id = c.id
                WHERE en.student_id = ? AND en.is_active = 1
                  AND t.day_of_week = ?
                ORDER BY t.start_time
                """,
                (rs, i) -> new ScheduleItem(
                        formatTime(rs.getString("start_time")),
                        formatTime(rs.getString("end_time")),
                        rs.getString("subject"),
                        rs.getString("teacher"),
                        rs.getString("room"),
                        "upcoming"),
                studentPk, today);

        // Mark first as "next"
        List<ScheduleItem> todaySchedule = new ArrayList<>(scheduleRaw);
        if (!todaySchedule.isEmpty()) {
            ScheduleItem first = todaySchedule.get(0);
            todaySchedule.set(0, new ScheduleItem(
                    first.start(), first.end(), first.subject(),
                    first.teacher(), first.room(), "next"));
        }

        // ---------- 4. Upcoming exams ----------
        List<UpcomingExam> upcomingExamsList = jdbc.query("""
                SELECT e.id, e.title, e.created_at,
                       s.name AS subject,
                       (SELECT TOP 1 r.name
                        FROM timetable t JOIN rooms r ON r.id = t.room_id
                        WHERE t.class_id = c.id ORDER BY t.start_time) AS room
                FROM exams e
                JOIN classes  c ON c.id = e.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN enrollments en ON en.class_id = c.id
                WHERE en.student_id = ? AND en.is_active = 1 AND e.is_published = 1
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new UpcomingExam(
                            rs.getLong("id"),
                            rs.getString("subject"),
                            rs.getString("title"),
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            String.format("%02d", dt.getDayOfMonth()),
                            dt.format(DateTimeFormatter.ofPattern("MMM")),
                            rs.getString("room") == null ? "TBD" : rs.getString("room"));
                },
                studentPk);

        // ---------- 5. Notifications ----------
        List<NotificationItem> notifications = jdbc.query("""
                SELECT n.id, n.title, n.message, n.created_at, nr.is_read
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                WHERE nr.user_id = ?
                ORDER BY n.created_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new NotificationItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("message"),
                            humanTime(dt),
                            "info",
                            !rs.getBoolean("is_read"));
                },
                userId);

        return new StudentDashboardDto(student, stats, todaySchedule, upcomingExamsList, notifications);
    }

    // ---------- helpers ----------

    private static String formatTime(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static String humanTime(LocalDateTime dt) {
        LocalDateTime now = LocalDateTime.now();
        Duration diff = Duration.between(dt, now);
        long mins = diff.toMinutes();
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + "m ago";
        long hours = diff.toHours();
        if (hours < 24) return hours + "h ago";
        long days = diff.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + "d ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM"));
    }
}