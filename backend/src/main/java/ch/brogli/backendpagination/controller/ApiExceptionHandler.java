package ch.brogli.backendpagination.controller;

import ch.brogli.backendpagination.exception.BadRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        String type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?";
        String detail =
                "invalid value for '"
                        + e.getName()
                        + "': "
                        + e.getValue()
                        + " (expected "
                        + type
                        + ")";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException e) {
        String detail =
                e.getConstraintViolations().stream()
                        .map(this::violationMessage)
                        .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail methodValidation(HandlerMethodValidationException e) {
        String detail =
                e.getValueResults().stream()
                        .flatMap(v -> v.getResolvableErrors().stream())
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = e.getMessage();
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private String violationMessage(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        return (path.isBlank() ? "" : path + ": ") + v.getMessage();
    }
}
