package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final DashboardService dashboard;
    private final TimetableService timetable;
    private final ExamsService exams;
    private final AttendanceService attendance;
    private final ClassesService classes;
    private final MaterialsService materials;

    public StudentController(DashboardService dashboard,
                             TimetableService timetable,
                             ExamsService exams,
                             AttendanceService attendance,
                             ClassesService classes,
                             MaterialsService materials) {
        this.dashboard = dashboard;
        this.timetable = timetable;
        this.exams = exams;
        this.attendance = attendance;
        this.classes = classes;
        this.materials = materials;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboardDto dashboard() { return dashboard.forCurrentStudent(); }

    @GetMapping("/timetable")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTimetableDto timetable() { return timetable.forCurrentStudent(); }

    /* ---------- Exams ---------- */
    @GetMapping("/exams")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentExamsDto exams() { return exams.forCurrentStudent(); }

    @GetMapping("/exams/{examId}/questions")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ExamQuestionDto> examQuestions(@PathVariable Long examId) {
        return exams.questionsForExam(examId);
    }

    /* NEW — available to take */
    @GetMapping("/exams/available")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentAvailableExamsDto availableExams() {
        return exams.availableForCurrentStudent();
    }

    /* NEW — take questions (no correct answer leaked) */
    @GetMapping("/exams/{examId}/take")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ExamTakeQuestionDto> takeQuestions(@PathVariable Long examId) {
        return exams.questionsForTaking(examId);
    }

    /* NEW — submit */
    @PostMapping("/exams/{examId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> submitExam(@PathVariable Long examId,
                                        @RequestBody SubmitExamRequest req) {
        try {
            return ResponseEntity.ok(exams.submitExam(examId, req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Attendance, classes, materials ---------- */
    @GetMapping("/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentAttendanceDto attendance() { return attendance.forCurrentStudent(); }

    @GetMapping("/classes")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentClassesDto classes() { return classes.forCurrentStudent(); }

    @PostMapping("/enroll/{classId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> enroll(@PathVariable Long classId) {
        try {
            classes.enroll(classId);
            return ResponseEntity.ok(java.util.Map.of("message", "Enrolled successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/materials")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentMaterialsDto materials() { return materials.forCurrentStudent(); }
}