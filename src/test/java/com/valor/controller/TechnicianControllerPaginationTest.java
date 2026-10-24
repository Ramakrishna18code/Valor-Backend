package com.valor.controller;

import com.valor.entity.Technician;
import com.valor.repository.ServiceRequestRepository;
import com.valor.repository.TechnicianRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TechnicianControllerPaginationTest {
    private TechnicianRepository technicians;
    private ServiceRequestRepository requests;
    private TechnicianController controller;

    @BeforeEach
    void setUp() {
        technicians = mock(TechnicianRepository.class);
        requests = mock(ServiceRequestRepository.class);
        controller = new TechnicianController(technicians, requests, mock(PasswordEncoder.class));
    }

    @Test
    void technicianListUsesDefaultAndDatabasePaging() {
        when(technicians.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.getAllTechnicians(0, 20);

        verify(technicians).findAll(eq(org.springframework.data.domain.PageRequest.of(0, 20,
                Sort.by(Sort.Direction.ASC, "id"))));
    }

    @Test
    void assignmentListUsesLaterPageAndExistingDescendingOrder() {
        when(technicians.existsById(9L)).thenReturn(true);
        when(requests.findByAssignedTechnicianId(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        Authentication admin = mock(Authentication.class);
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();

        controller.getAssignments(9L, admin, 2, 100);

        verify(requests).findByAssignedTechnicianId(eq(9L), eq(org.springframework.data.domain.PageRequest.of(2, 100,
                Sort.by(Sort.Direction.DESC, "serviceRequestedAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")))));
    }

    @Test
    void invalidPagingIsRejectedForBothEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> controller.getAllTechnicians(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.getAllTechnicians(0, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.getAllTechnicians(0, 101));
        Authentication admin = mock(Authentication.class);
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();
        assertThrows(IllegalArgumentException.class, () -> controller.getAssignments(9L, admin, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.getAssignments(9L, admin, 0, -1));
    }

    @Test
    void technicianCannotReadAnotherTechniciansAssignments() {
        Technician own = new Technician();
        own.setId(4L);
        own.setEmail("tech@example.invalid");
        when(technicians.findByEmail("tech@example.invalid")).thenReturn(Optional.of(own));
        Authentication technician = mock(Authentication.class);
        when(technician.getName()).thenReturn("tech@example.invalid");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))).when(technician).getAuthorities();

        assertThrows(RuntimeException.class, () -> controller.getAssignments(9L, technician, 0, 20));
    }
}
