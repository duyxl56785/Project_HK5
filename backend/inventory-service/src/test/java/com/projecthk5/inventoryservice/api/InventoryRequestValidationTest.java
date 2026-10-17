package com.projecthk5.inventoryservice.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InventoryRequestValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void initializeSkuAccepts128CharactersAndRejects129() {
        assertTrue(validator.validate(new InitializeInventoryRequest("S".repeat(128), 0)).isEmpty());

        Set<String> invalidFields = validator.validate(
                        new InitializeInventoryRequest("S".repeat(129), 0)).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("sku"), invalidFields);
    }

    @Test
    void operationIdAndSkuAccept128CharactersAndReject129() {
        InventoryOperationRequest valid = new InventoryOperationRequest(
                "O".repeat(128), InventoryOperationType.RESERVE,
                List.of(new InventoryOperationRequest.ItemQuantity("S".repeat(128), 1)));
        assertTrue(validator.validate(valid).isEmpty());

        InventoryOperationRequest invalid = new InventoryOperationRequest(
                "O".repeat(129), InventoryOperationType.RESERVE,
                List.of(new InventoryOperationRequest.ItemQuantity("S".repeat(129), 1)));
        Set<String> invalidFields = validator.validate(invalid).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("operationId", "items[0].sku"), invalidFields);
    }

    @Test
    void invalidArgumentAndOverflowUseBadRequestErrorCodes() throws NoSuchMethodException {
        InventoryExceptionHandler handler = new InventoryExceptionHandler();

        Map<String, Object> invalid = handler.invalidArgument(new IllegalArgumentException("private detail"));
        Map<String, Object> overflow = handler.quantityOverflow(new ArithmeticException("private detail"));

        assertEquals("INVALID_REQUEST", invalid.get("code"));
        assertEquals("Request contains an invalid value", invalid.get("message"));
        assertEquals("QUANTITY_OVERFLOW", overflow.get("code"));
        assertEquals("Quantity exceeds the supported range", overflow.get("message"));
        assertEquals(HttpStatus.BAD_REQUEST,
                InventoryExceptionHandler.class.getMethod("invalidArgument", IllegalArgumentException.class)
                        .getAnnotation(ResponseStatus.class).value());
        assertEquals(HttpStatus.BAD_REQUEST,
                InventoryExceptionHandler.class.getMethod("quantityOverflow", ArithmeticException.class)
                        .getAnnotation(ResponseStatus.class).value());
    }
}
