package com.example.faceapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    @PostMapping("/save-report")
    public ResponseEntity<Map<String, Object>> saveReport(@RequestBody Map<String, Object> report) {
        log.info("Received face analysis report: {}", report);
        return ResponseEntity.ok(Map.of("saved", true, "message", "Report logged successfully"));
    }
}
