package com.valor.commerce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompanyCashLedgerControllerTest {
    @Test
    void remittanceSubmissionReturnsMatchingHttpAndEnvelopeStatuses() throws Exception {
        var service = mock(CompanyCashLedgerService.class);
        when(service.submit(any())).thenReturn(new CompanyCashLedgerService.Remittance(
                8L, 7L, new BigDecimal("125.00"), "OFFICE_HANDOVER", "OFFICE-123",
                "PENDING_VERIFICATION", LocalDateTime.of(2026, 11, 8, 12, 0), null, null));
        var mvc = MockMvcBuilders.standaloneSetup(new CompanyCashLedgerController(service)).build();
        mvc.perform(post("/api/v1/technician/me/company-cash/remittances")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"cash-key-123\",\"amount\":125.00,\"method\":\"OFFICE_HANDOVER\",\"handoverReference\":\"OFFICE-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));
    }
}
