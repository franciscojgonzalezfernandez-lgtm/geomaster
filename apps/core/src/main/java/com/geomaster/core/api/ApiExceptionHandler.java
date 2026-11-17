package com.geomaster.core.api;

import com.geomaster.core.plans.PlanLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidInputException.class)
    ProblemDetail invalid(InvalidInputException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Invalid input");
        p.setProperty("field", e.field());
        return p;
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    ProblemDetail planLimit(PlanLimitExceededException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Plan limit exceeded");
        p.setProperty("limit", e.limit());
        p.setProperty("allowed", e.allowed());
        p.setProperty("planId", e.planId());
        return p;
    }
}
