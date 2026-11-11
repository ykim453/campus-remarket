package com.campus.remarket.portal;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Order(-2)
@RestControllerAdvice(assignableTypes = {OwnerPortalController.class, AdminPortalController.class})
public class StaffPortalErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        String reason = exception.getReason();
        String message = reason == null ? "요청을 처리하지 못했습니다." : reason;
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }
}
