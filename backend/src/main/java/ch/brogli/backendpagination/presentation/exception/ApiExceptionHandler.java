package ch.brogli.backendpagination.presentation.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Binding and type-mismatch failures are turned into RFC 7807 responses by Spring itself (see
 * {@code spring.mvc.problemdetails.enabled}). The generated {@code BooksApi} is {@code @Validated},
 * so bean-validation failures on its parameters arrive as {@link ConstraintViolationException}
 * instead of Spring's own {@code HandlerMethodValidationException} and still need a hand-written
 * mapping here, alongside {@link BadRequestException}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException e) {
        String detail =
                e.getConstraintViolations().stream()
                        .map(this::violationMessage)
                        .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private String violationMessage(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        return (path.isBlank() ? "" : path + ": ") + v.getMessage();
    }
}
