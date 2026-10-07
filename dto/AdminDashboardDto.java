package com.apexinstitute.dto;

import java.util.List;

public class AdminDashboardDto {

    private final Stats stats;
    private final List<ClassCapacity> classCapacity;
    private final List<ActivityItem> activity;
    private final List<Announcement> announcements;

    public AdminDashboardDto(Stats stats,
                             List<ClassCapacity> classCapacity,
                             List<ActivityItem> activity,
                             List<Announcement> announcements) {
        this.stats = stats;
        this.classCapacity = classCapacity;
        this.activity = activity;
        this.announcements = announcements;
    }

    public Stats getStats() { return stats; }
    public List<ClassCapacity> getClassCapacity() { return classCapacity; }
    public List<ActivityItem> getActivity() { return activity; }
    public List<Announcement> getAnnouncements() { return announcements; }

    public record Stats(int students, int teachers, int classes, int parents,
                        double attendancePct, double monthRevenue) {}

    public record ClassCapacity(Long classId, String className,
                                int students, int capacity) {}

    public record ActivityItem(String kind, String title, String text, String time) {}

    public record Announcement(String title, String audience, String when, String author) {}
}