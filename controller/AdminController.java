package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.AdminTimetableService;
import com.apexinstitute.service.AdminUsersService;
import com.apexinstitute.service.AdminSubjectsService;
import com.apexinstitute.service.AdminRoomsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.apexinstitute.service.AdminClassesService;
import com.apexinstitute.service.AdminNotificationsService;
import com.apexinstitute.service.AdminStudentsService;
import com.apexinstitute.service.AdminDashboardService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminTimetableService timetable;
    private final AdminUsersService users;
    private final AdminSubjectsService subjects;
    private final AdminRoomsService rooms;
    private final AdminClassesService classes;
    private final AdminNotificationsService notifications;
    private final AdminStudentsService students;
    private final AdminDashboardService dashboard;

    public AdminController(AdminTimetableService timetable,
                           AdminUsersService users,
                           AdminSubjectsService subjects,
                           AdminRoomsService rooms,
                           AdminClassesService classes,
                           AdminNotificationsService notifications,
                           AdminStudentsService students,
                           AdminDashboardService dashboard) {
        this.timetable = timetable;
        this.users = users;
        this.subjects = subjects;
        this.rooms = rooms;
        this.classes = classes;
        this.notifications = notifications;
        this.students = students;
        this.dashboard = dashboard;
    }

    /* ---------- Timetable ---------- */
    @GetMapping("/timetable")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminTimetableDto grid() {
        return timetable.loadGrid();
    }

    @PostMapping("/timetable/check")
    @PreAuthorize("hasRole('ADMIN')")
    public TimetableConflictDto check(@Valid @RequestBody TimetableSlotRequest req) {
        return timetable.check(req);
    }

    @PostMapping("/timetable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody TimetableSlotRequest req) {
        try {
            Long id = timetable.create(req);
            return ResponseEntity.ok(Map.of("message", "Slot created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PutMapping("/timetable/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody TimetableSlotRequest req) {
        try {
            timetable.update(id, req);
            return ResponseEntity.ok(Map.of("message", "Slot updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/timetable/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            timetable.delete(id);
            return ResponseEntity.ok(Map.of("message", "Slot deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Staff users ---------- */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUsersDto users() {
        return users.list();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req) {
        try {
            Long id = users.create(req);
            return ResponseEntity.ok(Map.of("message", "User created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRequest req) {
        try {
            users.update(id, req);
            return ResponseEntity.ok(Map.of("message", "User updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/users/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleUser(@PathVariable Long id) {
        try {
            users.toggleActive(id);
            return ResponseEntity.ok(Map.of("message", "User toggled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            users.delete(id);
            return ResponseEntity.ok(Map.of("message", "User deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Subjects ---------- */
    @GetMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminSubjectsDto subjects() {
        return subjects.list();
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSubject(@Valid @RequestBody CreateSubjectRequest req) {
        try {
            Long id = subjects.create(req);
            return ResponseEntity.ok(Map.of("message", "Subject created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        try {
            subjects.delete(id);
            return ResponseEntity.ok(Map.of("message", "Subject deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Rooms ---------- */
    @GetMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminRoomsDto rooms() {
        return rooms.list();
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest req) {
        try {
            Long id = rooms.create(req);
            return ResponseEntity.ok(Map.of("message", "Room created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        try {
            rooms.delete(id);
            return ResponseEntity.ok(Map.of("message", "Room deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Classes ---------- */
    @GetMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminClassesDto classes() {
        return classes.list();
    }

    @PostMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createClass(@Valid @RequestBody CreateClassRequest req) {
        try {
            Long id = classes.create(req);
            return ResponseEntity.ok(Map.of("message", "Class created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/classes/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleClass(@PathVariable Long id) {
        try {
            classes.toggle(id);
            return ResponseEntity.ok(Map.of("message", "Class toggled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteClass(@PathVariable Long id) {
        try {
            classes.delete(id);
            return ResponseEntity.ok(Map.of("message", "Class deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Notifications ---------- */
    @GetMapping("/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminNotificationsDto notifications() {
        return notifications.list();
    }

    @PostMapping("/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createNotification(@Valid @RequestBody CreateNotificationRequest req) {
        try {
            Long id = notifications.create(req);
            return ResponseEntity.ok(Map.of("message", "Notification posted", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        try {
            notifications.delete(id);
            return ResponseEntity.ok(Map.of("message", "Notification deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Students ---------- */
    @GetMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStudentsDto students() {
        return students.list();
    }

    @PostMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createStudent(@Valid @RequestBody CreateStudentRequest req) {
        try {
            Long id = students.create(req);
            return ResponseEntity.ok(Map.of("message", "Student created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/students/{id}/rfid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> issueRfid(@PathVariable Long id,
                                       @Valid @RequestBody IssueRfidRequest req) {
        try {
            students.issueRfid(id, req);
            return ResponseEntity.ok(Map.of("message", "RFID assigned"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/students/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleStudent(@PathVariable Long id) {
        try {
            students.toggleActive(id);
            return ResponseEntity.ok(Map.of("message", "Student toggled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- NEW: Delete student ---------- */
    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        try {
            students.delete(id);
            return ResponseEntity.ok(Map.of("message", "Student deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Attendance ---------- */
    @GetMapping("/attendance/today")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminAttendanceDto todayAttendance() {
        return students.todayRfidAttendance();
    }

    /* ---------- Dashboard ---------- */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardDto dashboard() {
        return dashboard.load();
    }
}