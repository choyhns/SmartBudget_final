package com.smartbudget.ml;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ML 서버 상태 확인 컨트롤러
 */
@RestController
@RequestMapping("/api/ml")
public class MLStatusController {
    
    @Value("${ocr.engine:python}")
    private String ocrEngine;
    
    @Value("${ml.classifier.engine:python}")
    private String classifierEngine;
    
    @Autowired
    private PythonMLService pythonMLService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean mlServerHealthy = pythonMLService.isServerHealthy();
        
        return ResponseEntity.ok(Map.of(
            "ocrEngine", ocrEngine,
            "classifierEngine", classifierEngine,
            "mlServerHealthy", mlServerHealthy,
            "fallbackAvailable", true // Gemini fallback
        ));
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean healthy = pythonMLService.isServerHealthy();
        
        if (healthy) {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "mlServer", "connected"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "DEGRADED",
                "mlServer", "disconnected",
                "fallback", "gemini"
            ));
        }
    }
}
