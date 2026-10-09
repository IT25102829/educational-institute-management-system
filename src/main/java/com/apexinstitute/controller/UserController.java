package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserService svc;

    public UserController(UserService svc) { this.svc = svc; }

    @GetMapping
    public UserDto me() { return svc.me(); }

    @PutMapping
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        return svc.updateProfile(req);
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        try {
            svc.changePassword(req);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Profile picture ---------- */

    @PostMapping(value = "/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPicture(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(svc.uploadProfilePicture(file));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/picture")
    public ResponseEntity<?> removePicture() {
        try {
            return ResponseEntity.ok(svc.removeProfilePicture());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
}