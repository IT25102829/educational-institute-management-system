package com.apexinstitute.dto;

import java.util.List;

public class TeacherDashboardDto {

    private final TeacherSummary teacher;
    private final Stats stats;
    private final List<SessionItem> todaySchedule;
    private final List<TaskItem> tasks;
    private final List<NotificationItem> notifications;
    private final SalaryPreview salary;

    public TeacherDashboardDto(TeacherSummary teacher,
                               Stats stats,
                               List<SessionItem> todaySchedule,
                               List<TaskItem> tasks,
                               List<NotificationItem> notifications,
                               SalaryPreview salary) {
        this.teacher = teacher;
        this.stats = stats;
        this.todaySchedule = todaySchedule;
        this.tasks = tasks;
        this.notifications = notifications;
        this.salary = salary;
    }

    public TeacherSummary getTeacher() { return teacher; }
    public Stats getStats() { return stats; }
    public List<SessionItem> getTodaySchedule() { return todaySchedule; }
    public List<TaskItem> getTasks() { return tasks; }
    public List<NotificationItem> getNotifications() { return notifications; }
    public SalaryPreview getSalary() { return salary; }

    public record TeacherSummary(Long id, String teacherCode, String fullName) {}

    public record Stats(int classesCount, int studentsCount,
                        int todaySessions, double monthSalary, String salaryStatus) {}

    public record SessionItem(Long classId, String start, String end,
                              String subject, String grade, String room,
                              int students, int markedCount, String status) {}

    public record TaskItem(String type, String title, String meta,
                           boolean done, Long refId) {}

    public record NotificationItem(String title, String text,
                                   String time, String kind) {}

    public record SalaryPreview(String month, int year, double amount,
                                String status, String paidOn) {}
}