package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.TeacherAttendanceService;
import com.apexinstitute.service.TeacherMarksService;
import com.apexinstitute.service.TeacherMaterialsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.apexinstitute.service.TeacherDashboardService;
import com.apexinstitute.service.TeacherSalaryService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherAttendanceService attendance;
    private final TeacherMarksService marks;
    private final TeacherMaterialsService materials;
    private final TeacherDashboardService dashboard;
    private final TeacherSalaryService salary;

    public TeacherController(TeacherAttendanceService attendance,
                             TeacherMarksService marks,
                             TeacherMaterialsService materials,
                             TeacherDashboardService dashboard,
                             TeacherSalaryService salary) {
        this.attendance = attendance;
        this.marks = marks;
        this.materials = materials;
        this.dashboard = dashboard;
        this.salary = salary;
    }

    /* ---------- Attendance ---------- */
    @GetMapping("/classes")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherClassesDto classes() {
        return attendance.classes();
    }

    @GetMapping("/attendance/roster")
    @PreAuthorize("hasRole('TEACHER')")
    public AttendanceRosterDto roster(@RequestParam Long classId, @RequestParam String date) {
        return attendance.roster(classId, date);
    }

    @PostMapping("/attendance/save")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> saveAttendance(@Valid @RequestBody SaveAttendanceRequest req) {
        try {
            int changed = attendance.save(req);
            return ResponseEntity.ok(Map.of("message", "Attendance saved", "changed", changed));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Exams & marks ---------- */
    @GetMapping("/exams")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherExamsDto exams() {
        return marks.exams();
    }

    @GetMapping("/exams/{examId}/marks")
    @PreAuthorize("hasRole('TEACHER')")
    public MarksRosterDto marks(@PathVariable Long examId) {
        return marks.roster(examId);
    }

    @PostMapping("/exams/{examId}/marks")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> saveMarks(@PathVariable Long examId,
                                       @Valid @RequestBody SaveMarksRequest req) {
        try {
            int changed = marks.save(examId, req);
            return ResponseEntity.ok(Map.of("message", "Marks saved", "changed", changed));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/exams/{examId}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> publish(@PathVariable Long examId) {
        try {
            marks.publish(examId);
            return ResponseEntity.ok(Map.of("message", "Exam published"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/exams")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> createExam(@Valid @RequestBody CreateExamRequest req) {
        try {
            Long id = marks.createExam(req);
            return ResponseEntity.ok(Map.of("message", "Exam created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/exams/{examId}/questions")
    @PreAuthorize("hasRole('TEACHER')")
    public java.util.List<ExamQuestionDto> questions(@PathVariable Long examId) {
        return marks.questions(examId);
    }

    @PostMapping("/exams/{examId}/questions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> addQuestion(@PathVariable Long examId,
                                         @Valid @RequestBody CreateQuestionRequest req) {
        try {
            Long qid = marks.addQuestion(examId, req);
            return ResponseEntity.ok(Map.of("message", "Question added", "id", qid));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/exams/{examId}/questions/{qId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long examId,
                                            @PathVariable Long qId) {
        try {
            marks.deleteQuestion(examId, qId);
            return ResponseEntity.ok(Map.of("message", "Question deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/exams/{examId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteExam(@PathVariable Long examId) {
        try {
            marks.deleteExam(examId);
            return ResponseEntity.ok(Map.of("message", "Exam deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
    @PatchMapping("/exams/{examId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> renameExam(@PathVariable Long examId,
                                        @Valid @RequestBody RenameExamRequest req) {
        try {
            marks.renameExam(examId, req.getTitle());
            return ResponseEntity.ok(Map.of("message", "Exam renamed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Materials ---------- */
    @GetMapping("/materials")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherMaterialsDto materials() {
        return materials.list();
    }

    @PostMapping(value = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> uploadMaterial(@RequestParam("classId") Long classId,
                                            @RequestParam("fileType") String fileType,
                                            @RequestPart("file") MultipartFile file) {
        try {
            Long id = materials.upload(classId, fileType, file);
            return ResponseEntity.ok(Map.of("message", "Uploaded", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(new ApiError("File save failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/materials/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteMaterial(@PathVariable Long id) {
        try {
            materials.delete(id);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Dashboard ---------- */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherDashboardDto dashboard() {
        return dashboard.forCurrentTeacher();
    }

    /* ---------- Salary ---------- */
    @GetMapping("/salary")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherSalaryDto salary() {
        return salary.forCurrentTeacher();
    }
}