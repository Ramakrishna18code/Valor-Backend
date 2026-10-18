package com.valor.installation;

import com.valor.auth.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallationScheduleFoundationTest {
    @Mock InstallationScheduleRepository schedules;
    @Mock InstallationScheduleApprovalRepository approvals;
    @Mock InstallationRequestRepository requests;
    @Mock WorkflowIdentityAccess identities;
    @Mock AssetIdentityAccess access;
    @Mock AuditService audit;

    @Test
    void incompleteTermsCannotBeCreated() {
        var service = service();
        when(requests.findById(1L)).thenReturn(Optional.of(mock(InstallationRequest.class)));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, new InstallationScheduleDtos.Write("INR", " ")));
        verifyNoInteractions(schedules);
    }

    @Test
    void firstVersionGetsStableScheduleKeyAndDraftStatus() {
        var request = mock(InstallationRequest.class);
        var actor = mock(User.class);
        when(requests.findById(1L)).thenReturn(Optional.of(request));
        when(schedules.findByRequestIdOrderByVersionNoDesc(1L)).thenReturn(List.of());
        when(access.actor()).thenReturn(actor);
        when(schedules.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service().create(1L, new InstallationScheduleDtos.Write("inr", "[{\"code\":\"ADVANCE\",\"sequence\":1}]"));
        assertEquals(1, result.versionNo());
        assertEquals(InstallationScheduleStatus.DRAFT, result.status());
        assertEquals("INR", result.currency());
        assertNotNull(result.scheduleKey());
    }

    @Test
    void customerApprovalCannotBeForgedForAnotherInstallation() {
        var request = mock(InstallationRequest.class);
        var customer = mock(CustomerProfile.class);
        var otherCustomer = mock(CustomerProfile.class);
        var schedule = mock(InstallationSchedule.class);
        var actor = mock(User.class);
        when(access.actor()).thenReturn(actor);
        when(identities.customer(actor)).thenReturn(customer);
        when(schedules.findById(9L)).thenReturn(Optional.of(schedule));
        when(schedule.getRequest()).thenReturn(request);
        when(request.getCustomer()).thenReturn(otherCustomer);
        when(customer.getId()).thenReturn(1L);
        when(otherCustomer.getId()).thenReturn(2L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> service().customerApprove(9L, new InstallationScheduleDtos.Approval("forged")));
        verifyNoInteractions(approvals);
    }

    @Test
    void approvedVersionsAreNotSubmittedAgain() {
        var schedule = mock(InstallationSchedule.class);
        when(schedules.findById(3L)).thenReturn(Optional.of(schedule));
        when(schedule.getStatus()).thenReturn(InstallationScheduleStatus.APPROVED);
        when(schedule.getCurrency()).thenReturn("INR");
        when(schedule.getMilestoneDefinitions()).thenReturn("[{\"code\":\"ADVANCE\"}]");
        assertThrows(IllegalStateException.class, () -> service().submit(3L));
    }

    private InstallationScheduleService service() {
        return new InstallationScheduleService(schedules, approvals, requests, identities, access, audit);
    }
}
