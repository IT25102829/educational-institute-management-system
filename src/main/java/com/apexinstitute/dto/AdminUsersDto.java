package com.apexinstitute.dto;

import java.util.List;

public class AdminUsersDto {

    private final Stats stats;
    private final List<UserItem> users;

    public AdminUsersDto(Stats stats, List<UserItem> users) {
        this.stats = stats;
        this.users = users;
    }

    public Stats getStats() { return stats; }
    public List<UserItem> getUsers() { return users; }

    public record Stats(int total, int admins, int management, int teachers, int active) {}

    public record UserItem(Long id,
                           String name,
                           String email,
                           String phone,
                           String role,
                           String teacherCode,      // null unless role = TEACHER
                           boolean isActive,
                           String joined) {}
}