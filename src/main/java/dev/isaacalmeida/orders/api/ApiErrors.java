package dev.isaacalmeida.orders.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail duplicateKey(DataIntegrityViolationException ignored) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setDetail("A conflicting order already exists. Retry with the same Idempotency-Key.");
        return problem;
    }
}
