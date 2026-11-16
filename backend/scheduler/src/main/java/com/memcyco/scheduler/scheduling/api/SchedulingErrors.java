package com.memcyco.scheduler.scheduling.api;

import java.time.DateTimeException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class SchedulingErrors {

  @ExceptionHandler({
    IllegalArgumentException.class,
    DateTimeException.class,
    ArithmeticException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ProblemDetail badRequest(Exception exception) {
    String detail =
        exception instanceof HttpMessageNotReadableException
            ? "Request body must be a valid JSON object"
            : exception.getMessage();
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, detail == null ? "Invalid request" : detail);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail status(ResponseStatusException exception) {
    return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail conflict(OptimisticLockingFailureException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Scheduling was changed by another request");
  }
}
