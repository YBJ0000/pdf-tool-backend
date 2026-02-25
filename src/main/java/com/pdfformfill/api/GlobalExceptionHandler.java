package com.pdfformfill.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 将未捕获异常转为 500 响应并返回异常信息，便于排查。
 */
@RestControllerAdvice(basePackageClasses = PdfMergeController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception for /api/pdf/merge", e);
        String message = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "(no message)");
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", message, "error", e.getClass().getSimpleName()));
    }
}
