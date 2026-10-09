package com.apexinstitute.dto;

import java.util.List;

public class AdminStudentsDto {

    private final Stats stats;
    private final List<StudentItem> students;

    public AdminStudentsDto(Stats stats, List<StudentItem> students) {
        this.stats = stats;
        this.students = students;
    }

    public Stats getStats() { return stats; }
    public List<StudentItem> getStudents() { return students; }

    public record Stats(int total, int active, int withRfid, int grade10Plus) {}

    public record StudentItem(Long id,
                              String studentCode,       // "S0001"
                              String fullName,
                              String email,
                              String phone,
                              String grade,
                              String rfidTag,
                              int enrolledClasses,
                              boolean isActive,
                              String joined) {}
}