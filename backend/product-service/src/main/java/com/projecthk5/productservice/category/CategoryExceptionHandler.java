package com.projecthk5.productservice.category;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = CategoryController.class)
public class CategoryExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CategoryException.class)
    ResponseEntity<CategoryError> handleCategory(CategoryException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case CATEGORY_NOT_FOUND, CATEGORY_PARENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CATEGORY_CYCLE, CATEGORY_HAS_CHILDREN -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new CategoryError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<CategoryError> handleIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CategoryError(
                "CATEGORY_CONFLICT", "Category operation conflicts with stored relationships"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<CategoryError> handleUnexpected(Exception exception) {
        logger.error("Category operation failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CategoryError("INTERNAL_ERROR", "Category operation failed"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return handleExceptionInternal(exception,
                new CategoryError("VALIDATION_ERROR", "Invalid category input", errors),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object response = body instanceof CategoryError ? body : new CategoryError(
                status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR",
                status.is4xxClientError() ? "Invalid category request" : "Category operation failed");
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }
}
