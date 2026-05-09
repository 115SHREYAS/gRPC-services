package com.example.grpc.gateway.rest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GrpcRestExceptionHandler {

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGrpcException(StatusRuntimeException ex) {
        Status status = Status.fromThrowable(ex);
        HttpStatus httpStatus = switch (status.getCode()) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED_PRECONDITION -> HttpStatus.PRECONDITION_FAILED;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grpcStatus", status.getCode().name());
        body.put("message", status.getDescription());
        return ResponseEntity.status(httpStatus).body(body);
    }
}
