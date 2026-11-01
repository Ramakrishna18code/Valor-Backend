package com.valor.workflow;

import com.valor.assets.Lift;
import com.valor.auth.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceCodeStateTest {
    final LiftServiceCodeRepository codes = mock(LiftServiceCodeRepository.class);
    final ServiceCodeVerificationRepository verifications = mock(ServiceCodeVerificationRepository.class);
    final RequestRepository requests = mock(RequestRepository.class);
    final AssignmentRepository assignments = mock(AssignmentRepository.class);
    final AssetIdentityAccess identities = mock(AssetIdentityAccess.class);
    final WorkflowIdentityAccess profiles = mock(WorkflowIdentityAccess.class);
    final TechnicianProfile technician = mock(TechnicianProfile.class);
    final ServiceRequest request = mock(ServiceRequest.class);
    final TechnicianAssignment assignment = mock(TechnicianAssignment.class);
    final com.valor.notifications.NotificationService notifications = mock(com.valor.notifications.NotificationService.class);
    final ServiceCodeService service = new ServiceCodeService(codes, verifications, requests, assignments,
            identities, profiles, mock(PasswordEncoder.class), notifications, mock(WorkflowLiftRepository.class), "test-secret");

    @BeforeEach void setup() {
        User actor = new User();
        when(identities.actor()).thenReturn(actor);
        when(profiles.technician(actor)).thenReturn(technician);
        when(technician.getId()).thenReturn(7L);
        when(assignments.active(1L)).thenReturn(Optional.of(assignment));
        when(assignment.getTechnician()).thenReturn(technician);
        when(requests.findById(1L)).thenReturn(Optional.of(request));
        Lift lift = mock(Lift.class); when(lift.getId()).thenReturn(10L);
        when(request.getLift()).thenReturn(lift);
        LiftServiceCode code = new LiftServiceCode(); code.lift = lift;
        code.codeCiphertext = "must-not-be-exposed";
        when(codes.findByLiftId(10L)).thenReturn(Optional.of(code));
    }

    @Test void oldOrOtherTechnicianVerificationDoesNotAuthorizeCurrentTechnician() {
        ServiceCodeVerification previous = new ServiceCodeVerification(); previous.verifiedAt = LocalDateTime.now();
        when(verifications.findTopByRequestIdAndActionOrderByCreatedAtDescIdDesc(1L, ServiceCodeAction.START)).thenReturn(Optional.of(previous));
        var state = service.technicianState(1L, ServiceCodeAction.START);
        assertEquals("AVAILABLE", state.status()); assertNull(state.code()); assertNull(state.verifiedAt());
    }

    @Test void reportsCurrentTechniciansStartVerificationWithoutExposingCode() {
        ServiceCodeVerification verification = new ServiceCodeVerification(); verification.verifiedAt = LocalDateTime.now();
        when(verifications.lock(1L, 7L, ServiceCodeAction.START)).thenReturn(Optional.of(verification));
        var state = service.technicianState(1L, ServiceCodeAction.START);
        assertEquals("VERIFIED", state.status()); assertNull(state.code());
        assertEquals("AVAILABLE", service.technicianState(1L, ServiceCodeAction.COMPLETE).status());
    }

    @Test void deniesUnassignedTechnician() {
        TechnicianProfile other = mock(TechnicianProfile.class); when(other.getId()).thenReturn(8L);
        when(assignment.getTechnician()).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> service.technicianState(1L, ServiceCodeAction.START));
        verifyNoInteractions(codes, verifications);
    }

    @Test void installationWithoutLiftDoesNotRequireCode() {
        when(request.getLift()).thenReturn(null);
        assertNull(service.technicianState(1L, ServiceCodeAction.START));
    }

    @Test void reportsActiveLock() {
        ServiceCodeVerification verification = new ServiceCodeVerification(); verification.lockedUntil = LocalDateTime.now().plusMinutes(10);
        when(verifications.lock(1L, 7L, ServiceCodeAction.START)).thenReturn(Optional.of(verification));
        assertEquals("LOCKED", service.technicianState(1L, ServiceCodeAction.START).status());
    }

    @Test void sendsCodeForJobAlreadyAtSiteWithoutExposingItToTechnician() {
        var sending = spy(service);
        when(requests.lockById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(RequestStatus.REACHED_SITE);
        CustomerProfile customer = mock(CustomerProfile.class);
        User recipient = mock(User.class);
        when(request.getCustomer()).thenReturn(customer);
        when(customer.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(42L);
        doReturn(new ServiceCodeService.CodeView(10L, "123456", "AVAILABLE", null, 0, null))
                .when(sending).stateForRequest(1L, ServiceCodeAction.START, true);
        var result = sending.sendToCustomer(1L, ServiceCodeAction.START);
        verify(notifications).createSystem(eq(42L), eq("Arrival service code"), contains("Service code: 123456. Request #1"));
        assertNull(result.code());
    }

    @Test void doesNotSendCodeForUnauthorizedTechnician() {
        TechnicianProfile other = mock(TechnicianProfile.class);
        when(other.getId()).thenReturn(8L);
        when(assignment.getTechnician()).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> service.sendToCustomer(1L, ServiceCodeAction.START));
        verifyNoInteractions(notifications);
    }

    @Test void doesNotSendCodeBeforeArrival() {
        when(requests.lockById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(RequestStatus.ON_THE_WAY);
        assertThrows(WorkflowException.class, () -> service.sendToCustomer(1L, ServiceCodeAction.START));
        verifyNoInteractions(notifications);
    }
}
