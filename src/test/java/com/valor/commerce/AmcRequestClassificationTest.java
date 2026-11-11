package com.valor.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AmcRequestClassificationTest {
    @Test
    void usesRecordedVisitDecisionToSeparatePlanFromIncludedWork() {
        FinanceAccess access = mock(FinanceAccess.class);
        AmcCoverageService service = new AmcCoverageService(access);
        when(access.job(42L, false)).thenReturn(Map.of("liftId", 7L, "preferredVisitDate", LocalDate.of(2026, 11, 15)));
        when(access.rows(anyString(), eq(42L)))
                .thenReturn(List.of(Map.of("contractId", 9L, "outcome", "COVERED")))
                .thenReturn(List.of(Map.of("contractId", 9L, "outcome", "EXCLUDED")))
                .thenReturn(List.of());
        when(access.rows(anyString(), eq(7L), eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2026, 11, 15))))
                .thenReturn(List.of(Map.of("id", 9L)));

        assertEquals(new AmcCoverageService.RequestClassification("AMC", "COVERED"), service.requestClassification(42L));
        assertEquals(new AmcCoverageService.RequestClassification("AMC", "EXCLUDED"), service.requestClassification(42L));
        assertEquals(new AmcCoverageService.RequestClassification("AMC", "UNCONFIRMED"), service.requestClassification(42L));
    }

    @Test
    void recordsWithoutAPlanAreNonAmc() {
        FinanceAccess access = mock(FinanceAccess.class);
        AmcCoverageService service = new AmcCoverageService(access);
        when(access.job(43L, false)).thenReturn(Map.of("liftId", 7L));
        when(access.rows(anyString(), eq(43L))).thenReturn(List.of(new java.util.HashMap<>(Map.of("outcome", "UNCONFIRMED"))));
        assertEquals(new AmcCoverageService.RequestClassification("NON_AMC", "UNCONFIRMED"), service.requestClassification(43L));
    }

    @Test
    void noRecordedDecisionAndNoPlanForVisitDateIsNonAmc() {
        FinanceAccess access = mock(FinanceAccess.class);
        AmcCoverageService service = new AmcCoverageService(access);
        when(access.job(44L, false)).thenReturn(Map.of("liftId", 7L, "preferredVisitDate", LocalDate.of(2026, 11, 15)));
        when(access.rows(anyString(), eq(44L))).thenReturn(List.of());
        when(access.rows(anyString(), eq(7L), eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2026, 11, 15))))
                .thenReturn(List.of());
        assertEquals(new AmcCoverageService.RequestClassification("NON_AMC", "UNCONFIRMED"), service.requestClassification(44L));
    }
}
