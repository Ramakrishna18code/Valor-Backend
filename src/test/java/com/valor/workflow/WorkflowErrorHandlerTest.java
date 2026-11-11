package com.valor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class WorkflowErrorHandlerTest {
    private final WorkflowErrorHandler handler = new WorkflowErrorHandler(null);

    @Test
    void reportsInvalidRequestFieldsWithoutEchoingSubmittedValues() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "createRequest");
        binding.addError(new FieldError("createRequest", "preferredVisitDate", "private-value",
                false, null, null, "must be a valid date"));
        var parameter = new org.springframework.core.MethodParameter(
                WorkflowErrorHandlerTest.class.getDeclaredMethod("create", WorkflowDtos.CreateRequest.class), 0);

        var response = handler.invalidFields(new MethodArgumentNotValidException(parameter, binding));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid workflow fields: preferredVisitDate", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("private-value"));
    }

    @Test
    void reportsTheFieldForMalformedValuesWithoutEchoingTheValue() {
        var cause = InvalidFormatException.from(null, "invalid", "private-value", java.time.LocalDate.class);
        cause.prependPath(WorkflowDtos.CreateRequest.class, "preferredVisitDate");
        var error = new HttpMessageNotReadableException("Unreadable request", cause);

        var response = handler.invalidBody(error);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid value for workflow field: preferredVisitDate", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("private-value"));
    }

    private static void create(WorkflowDtos.CreateRequest request) {}
}
