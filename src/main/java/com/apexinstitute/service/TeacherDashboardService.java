package com.apexinstitute.service;

import com.apexinstitute.dto.TeacherDashboardDto;
import com.apexinstitute.dto.TeacherDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TeacherDashboardService {

    private final JdbcTemplate jdbc;

    public TeacherDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public TeacherDashboardDto forCurrentTeacher() {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        // ---------- 1. Teacher info ----------
        TeacherSummary teacher = jdbc.queryForObject("""
                SELECT t.id, t.teacher_id, t.user_id, u.full_name
                FROM teachers t
                JOIN users u ON u.id = t.user_id
                WHERE t.user_id = ?
                """,
                (rs, i) -> new TeacherSummary(
                        rs.getLong("id"),                  // teachers.id (numeric PK)
                        rs.getString("teacher_id"),        // "T0001" — already a string
                        rs.getString("full_name")),
                userId);
        if (teacher == null) throw new RuntimeException("Teacher record not found");
        Long teacherId = teacher.id();

        // ---------- 2. Stats ----------
        Integer classesCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT class_id) FROM timetable WHERE teacher_id = ?
                """, Integer.class, teacherId);

        Integer studentsCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT en.student_id)
                FROM enrollments en
                JOIN timetable t ON t.class_id = en.class_id
                WHERE t.teacher_id = ? AND en.is_active = 1
                """, Integer.class, teacherId);

        // ---------- 3. Today's schedule ----------
        String today = LocalDate.now().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        LocalDate todayDate = LocalDate.now();

        List<SessionItem> raw = jdbc.query("""
                SELECT t.start_time, t.end_time,
                       c.id AS class_id, c.name AS class_name, c.grade,
                       s.name AS subject,
                       r.name AS room,
                       (SELECT COUNT(*) FROM enrollments en
                        WHERE en.class_id = c.id AND en.is_active = 1) AS students,
                       (SELECT COUNT(*) FROM attendance a
                        WHERE a.class_id = c.id AND a.[date] = ?) AS marked_count
                FROM timetable t
                JOIN classes  c ON c.id = t.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN rooms    r ON r.id = t.room_id
                WHERE t.teacher_id = ? AND t.day_of_week = ?
                ORDER BY t.start_time
                """,
                (rs, i) -> new SessionItem(
                        rs.getLong("class_id"),
                        trim(rs.getString("start_time")),
                        trim(rs.getString("end_time")),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getString("room"),
                        rs.getInt("students"),
                        rs.getInt("marked_count"),
                        ""),
                Date.valueOf(todayDate), teacherId, today);

        // Mark status: past / next / upcoming
        LocalTime now = LocalTime.now();
        boolean nextAssigned = false;
        List<SessionItem> todaySchedule = new ArrayList<>();
        for (SessionItem s : raw) {
            LocalTime startT = LocalTime.parse(s.start());
            LocalTime endT = LocalTime.parse(s.end());
            String status;
            if (now.isAfter(endT)) {
                status = "done";
            } else if (now.isAfter(startT) && now.isBefore(endT)) {
                status = "live";
            } else if (!nextAssigned) {
                status = "next";
                nextAssigned = true;
            } else {
                status = "upcoming";
            }
            todaySchedule.add(new SessionItem(
                    s.classId(), s.start(), s.end(),
                    s.subject(), s.grade(), s.room(),
                    s.students(), s.markedCount(), status));
        }

        // ---------- 4. Tasks ----------
        List<TaskItem> tasks = new ArrayList<>();

        // 4a. Unmarked attendance for today's sessions that have already started
        for (SessionItem s : todaySchedule) {
            LocalTime startT = LocalTime.parse(s.start());
            if (now.isBefore(startT)) continue;
            if (s.markedCount() < s.students()) {
                tasks.add(new TaskItem(
                        "attendance",
                        "Mark attendance — Grade " + s.grade() + " " + s.subject(),
                        "Today · " + s.start() + " · " + s.students() + " students",
                        false,
                        s.classId()));
            }
        }

        // 4b. Unpublished exams
        List<TaskItem> unpublished = jdbc.query("""
                SELECT e.id, e.title, c.name AS class_name
                FROM exams e
                JOIN classes c ON c.id = e.class_id
                WHERE e.created_by = ? AND e.is_published = 0
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> new TaskItem(
                        "exam",
                        "Publish — " + rs.getString("title"),
                        rs.getString("class_name"),
                        false,
                        rs.getLong("id")),
                userId);
        tasks.addAll(unpublished);

        // Cap at 6 tasks for a clean UI
        if (tasks.size() > 6) tasks = tasks.subList(0, 6);

        // ---------- 5. Notifications ----------
        List<NotificationItem> notifications = jdbc.query("""
                SELECT n.title, n.message, n.created_at
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                WHERE nr.user_id = ?
                ORDER BY n.created_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new NotificationItem(
                            rs.getString("title"),
                            rs.getString("message"),
                            humanTime(dt),
                            "info");
                },
                userId);

        // ---------- 6. Latest salary ----------
        SalaryPreview salary = jdbc.query("""
                SELECT TOP 1 [month], [year], amount, status, payment_date
                FROM teacher_salaries
                WHERE teacher_id = ?
                ORDER BY [year] DESC, [month] DESC
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Date pd = rs.getDate("payment_date");
                    String paidOn = pd == null ? null
                            : pd.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    return new SalaryPreview(
                            monthName(rs.getInt("month")),
                            rs.getInt("year"),
                            rs.getBigDecimal("amount").doubleValue(),
                            rs.getString("status"),
                            paidOn);
                },
                teacherId);

        Stats stats = new Stats(
                classesCount == null ? 0 : classesCount,
                studentsCount == null ? 0 : studentsCount,
                todaySchedule.size(),
                salary != null ? salary.amount() : 0.0,
                salary != null ? salary.status() : "—");

        return new TeacherDashboardDto(teacher, stats, todaySchedule, tasks, notifications, salary);
    }

    /* ---------- helpers ---------- */
    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static String monthName(int m) {
        return java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String humanTime(LocalDateTime dt) {
        Duration d = Duration.between(dt, LocalDateTime.now());
        long m = d.toMinutes();
        if (m < 1) return "Just now";
        if (m < 60) return m + "m ago";
        long h = d.toHours();
        if (h < 24) return h + "h ago";
        long days = d.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + "d ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM"));
    }
}