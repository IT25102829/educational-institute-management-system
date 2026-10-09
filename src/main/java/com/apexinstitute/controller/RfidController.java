package com.apexinstitute.controller;

import com.apexinstitute.dto.ApiError;
import com.apexinstitute.service.RfidScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rfid")
public class RfidController {

    private final RfidScanService svc;

    public RfidController(RfidScanService svc) { this.svc = svc; }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> body) {
        try {
            String uid = body.get("uid");
            Map<String, Object> result = svc.mark(uid);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
}