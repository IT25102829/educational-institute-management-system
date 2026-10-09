package com.apexinstitute.service;

import com.apexinstitute.dto.*;
import com.apexinstitute.entity.User;
import com.apexinstitute.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");
    private static final String WEB_PREFIX = "/uploads/profiles/";

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null)
            throw new RuntimeException("Not authenticated");
        Long id = Long.parseLong(auth.getName());
        return users.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDto me() { return UserDto.from(currentUser()); }

    @Transactional
    public UserDto updateProfile(UpdateProfileRequest req) {
        User u = currentUser();

        if (!u.getEmail().equalsIgnoreCase(req.getEmail())
                && users.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        u.setFullName(req.getFullName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        users.save(u);
        return UserDto.from(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User u = currentUser();
        if (!encoder.matches(req.getCurrentPassword(), u.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.getNewPassword()));
        users.save(u);
    }

    /* ---------- Profile picture ---------- */

    @Transactional
    public UserDto uploadProfilePicture(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new RuntimeException("Please choose a file");
        if (file.getSize() > MAX_IMAGE_BYTES)
            throw new RuntimeException("Image is too large (max 2 MB)");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new RuntimeException("Only PNG, JPG, or WebP images are allowed");

        User u = currentUser();
        String oldPath = u.getProfilePicture();

        String ext = extensionFor(file.getContentType());
        String filename = UUID.randomUUID() + ext;

        Path dir = Paths.get("uploads", "profiles").toAbsolutePath();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory");
        }

        Path target = dir.resolve(filename);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("Could not save file");
        }

        u.setProfilePicture(WEB_PREFIX + filename);
        users.save(u);

        deleteFileIfExists(oldPath);
        return UserDto.from(u);
    }

    @Transactional
    public UserDto removeProfilePicture() {
        User u = currentUser();
        String old = u.getProfilePicture();
        u.setProfilePicture(null);
        users.save(u);
        deleteFileIfExists(old);
        return UserDto.from(u);
    }

    /* ---------- helpers ---------- */

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private void deleteFileIfExists(String webPath) {
        if (webPath == null || !webPath.startsWith("/uploads/")) return;
        try {
            String rel = webPath.substring("/uploads/".length());
            Path p = Paths.get("uploads").toAbsolutePath().resolve(rel).normalize();
            // Guard against path traversal
            if (!p.startsWith(Paths.get("uploads").toAbsolutePath())) return;
            Files.deleteIfExists(p);
        } catch (IOException ignored) { /* best-effort */ }
    }
}