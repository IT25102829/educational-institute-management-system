package com.apexinstitute.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank private String studentId;
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @NotBlank private String phone;
    @NotBlank private String grade;
    private String rfidTag;
    @NotBlank @Size(min = 6) private String password;

    @NotBlank private String parentFullName;
    @NotBlank @Email private String parentEmail;
    @NotBlank private String parentNic;
    @NotBlank @Size(min = 6) private String parentPassword;

    // getters + setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getRfidTag() { return rfidTag; }
    public void setRfidTag(String rfidTag) { this.rfidTag = rfidTag; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getParentFullName() { return parentFullName; }
    public void setParentFullName(String parentFullName) { this.parentFullName = parentFullName; }
    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }
    public String getParentNic() { return parentNic; }
    public void setParentNic(String parentNic) { this.parentNic = parentNic; }
    public String getParentPassword() { return parentPassword; }
    public void setParentPassword(String parentPassword) { this.parentPassword = parentPassword; }
}