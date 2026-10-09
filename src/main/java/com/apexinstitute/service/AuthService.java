package com.apexinstitute.service;

import com.apexinstitute.dto.*;
import com.apexinstitute.entity.*;
import com.apexinstitute.repository.*;
import com.apexinstitute.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final StudentRepository students;
    private final ParentRepository parents;
    private final ParentChildRepository parentChild;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, StudentRepository students,
                       ParentRepository parents, ParentChildRepository parentChild,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.students = students;
        this.parents = parents;
        this.parentChild = parentChild;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest req) {
        // Accept email or username (students log in with "S0001")
        String id = req.getIdentifier().trim();
        User u = users.findByEmailOrUsername(id, id)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!encoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }
        if (Boolean.FALSE.equals(u.getIsActive())) {
            throw new RuntimeException("Account is deactivated");
        }

        String token = jwt.generateToken(u.getId(), u.getEmail(), u.getRole());
        return new LoginResponse(token, UserDto.from(u));
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.getEmail()))
            throw new RuntimeException("Email already registered");
        if (students.existsByStudentId(req.getStudentId()))
            throw new RuntimeException("Student ID already in use");
        if (req.getRfidTag() != null && !req.getRfidTag().isBlank()
                && students.existsByRfidTag(req.getRfidTag()))
            throw new RuntimeException("RFID tag already in use");

        // --- Student user ---
        User stuUser = new User();
        stuUser.setUsername(req.getStudentId());
        stuUser.setPasswordHash(encoder.encode(req.getPassword()));
        stuUser.setRole("STUDENT");
        stuUser.setFullName(req.getFullName());
        stuUser.setEmail(req.getEmail());
        stuUser.setPhone(req.getPhone());
        stuUser.setIsActive(true);
        users.save(stuUser);

        Student student = new Student();
        student.setUser(stuUser);
        student.setStudentId(req.getStudentId());
        student.setGrade(req.getGrade());
        student.setRfidTag(req.getRfidTag());
        student.setIsActive(true);
        students.save(student);

        // --- Parent: reuse if NIC exists ---
        Parent parent = parents.findByNic(req.getParentNic()).orElse(null);

        if (parent == null) {
            if (users.existsByEmail(req.getParentEmail()))
                throw new RuntimeException("Parent email already registered");

            User parUser = new User();
            parUser.setUsername(req.getParentEmail());
            parUser.setPasswordHash(encoder.encode(req.getParentPassword()));
            parUser.setRole("PARENT");
            parUser.setFullName(req.getParentFullName());
            parUser.setEmail(req.getParentEmail());
            parUser.setIsActive(true);
            users.save(parUser);

            parent = new Parent();
            parent.setUser(parUser);
            parent.setNic(req.getParentNic());
            parent.setIsActive(true);
            parents.save(parent);
        }

        if (!parentChild.existsByParentIdAndStudentId(parent.getId(), student.getId())) {
            ParentChild pc = new ParentChild();
            pc.setParent(parent);
            pc.setStudent(student);
            parentChild.save(pc);
        }

        String token = jwt.generateToken(stuUser.getId(), stuUser.getEmail(), stuUser.getRole());
        return new LoginResponse(token, UserDto.from(stuUser));
    }
}