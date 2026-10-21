package com.valor.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class ChecklistInitializationRaceTest {
    @Test
    void onlyTheChecklistRequestUniqueConstraintIsClassifiedAsInitializationRace() {
        assertTrue(ChecklistService.isChecklistInitializationDuplicate(
                new DataIntegrityViolationException("Duplicate entry for uk_job_checklist_request")));
        assertFalse(ChecklistService.isChecklistInitializationDuplicate(
                new DataIntegrityViolationException("Duplicate entry for an unrelated constraint")));
    }
}
