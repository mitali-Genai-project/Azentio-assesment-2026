package com.azentio.clearlist.exception;

import com.azentio.clearlist.dto.ErrorResponse;
import com.azentio.clearlist.dto.ErrorResponse.Detail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every exception to the single {@link ErrorResponse} JSON shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new Detail(e.getField(), e.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                            HttpServletRequest request) {
        List<Detail> details = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Detail(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        List<Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new Detail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", request, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", request,
                List.of(new Detail(ex.getName(), "invalid value '" + ex.getValue() + "'")));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ErrorResponse> handleMissingParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON for this endpoint", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint " + request.getRequestURI(), request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "Uploaded file is too large", request, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified by another request; reload and retry", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request,
                List.of());
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                       HttpServletRequest request, List<Detail> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), details));
    }
}
