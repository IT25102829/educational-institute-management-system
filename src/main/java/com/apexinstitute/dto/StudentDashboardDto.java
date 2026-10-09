package com.apexinstitute.dto;

import java.util.List;

public class StudentDashboardDto {

    private final StudentSummary student;
    private final Stats stats;
    private final List<ScheduleItem> todaySchedule;
    private final List<UpcomingExam> upcomingExams;
    private final List<NotificationItem> notifications;

    public StudentDashboardDto(StudentSummary student,
                               Stats stats,
                               List<ScheduleItem> todaySchedule,
                               List<UpcomingExam> upcomingExams,
                               List<NotificationItem> notifications) {
        this.student = student;
        this.stats = stats;
        this.todaySchedule = todaySchedule;
        this.upcomingExams = upcomingExams;
        this.notifications = notifications;
    }

    public StudentSummary getStudent() { return student; }
    public Stats getStats() { return stats; }
    public List<ScheduleItem> getTodaySchedule() { return todaySchedule; }
    public List<UpcomingExam> getUpcomingExams() { return upcomingExams; }
    public List<NotificationItem> getNotifications() { return notifications; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Stats(double attendancePct, int enrolledClasses,
                        int upcomingExams, String feeStatus) {}

    public record ScheduleItem(String start, String end, String subject,
                               String teacher, String room, String status) {}

    public record UpcomingExam(Long id, String subject, String title,
                               String date, String day, String mon, String room) {}

    public record NotificationItem(Long id, String title, String text,
                                   String time, String kind, boolean unread) {}
}