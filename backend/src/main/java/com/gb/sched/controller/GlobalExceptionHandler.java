package com.gb.sched.controller;

import com.gb.sched.service.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of(
        "error", ex.status().getReasonPhrase(),
        "message", ex.getMessage(),
        "timestamp", LocalDateTime.now().toString()));
  }
}
