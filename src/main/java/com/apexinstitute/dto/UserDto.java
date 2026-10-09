package com.apexinstitute.dto;

import com.apexinstitute.entity.User;

public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String profilePicture;

    public static UserDto from(User u) {
        UserDto d = new UserDto();
        d.id = u.getId();
        d.username = u.getUsername();
        d.fullName = u.getFullName();
        d.email = u.getEmail();
        d.phone = u.getPhone();
        d.role = u.getRole();
        d.profilePicture = u.getProfilePicture();
        return d;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getProfilePicture() { return profilePicture; }
}