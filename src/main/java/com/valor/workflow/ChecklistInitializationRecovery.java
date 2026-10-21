package com.valor.workflow;

import com.valor.workflow.ChecklistDtos.JobChecklistView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChecklistInitializationRecovery {
    private final ChecklistService checklists;

    ChecklistInitializationRecovery(ChecklistService checklists) {
        this.checklists = checklists;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    JobChecklistView recover(Long requestId) {
        return checklists.committedView(requestId);
    }
}
