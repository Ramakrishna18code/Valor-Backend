package com.valor.parts;

import com.valor.auth.*;
import com.valor.parts.PartsDtos.LineWrite;
import com.valor.parts.PartsDtos.RequestWrite;
import com.valor.workflow.RequestStatus;
import com.valor.workflow.ServiceRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartsServiceAuthorizationTest {
    @Mock PartCategoryRepository categories;
    @Mock PartItemRepository items;
    @Mock PartMovementRepository movements;
    @Mock PartRequestRepository requests;
    @Mock PartLineRepository lines;
    @Mock PartEventRepository events;
    @Mock PartsTechnicianRepository technicians;
    @Mock PartsServiceRequestRepository serviceRequests;
    @Mock AssetIdentityAccess identities;
    @Mock AuditService audit;

    @InjectMocks PartsService service;

    @Test
    void validTechnicianProfileCanSubmitForOpenRequest() {
        User actor = mock(User.class);
        TechnicianProfile technician = mock(TechnicianProfile.class);
        ServiceRequest serviceRequest = serviceRequest(42L, RequestStatus.REPAIR_IN_PROGRESS);
        PartItem item = new PartItem();
        item.id = 11L;
        item.active = true;
        when(actor.getId()).thenReturn(7L);
        when(actor.getRole()).thenReturn(Role.TECHNICIAN);
        when(technician.getUser()).thenReturn(actor);
        when(technician.getId()).thenReturn(9L);
        when(identities.actor()).thenReturn(actor);
        when(technicians.findByUserId(7L)).thenReturn(Optional.of(technician));
        when(serviceRequests.findById(42L)).thenReturn(Optional.of(serviceRequest));
        when(items.findById(11L)).thenReturn(Optional.of(item));
        when(requests.findById(100L)).thenAnswer(invocation -> Optional.of(savedRequest(100L, serviceRequest, technician)));
        when(lines.findByRequestId(100L)).thenReturn(List.of());
        when(events.findByRequestIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        doAnswer(invocation -> { invocation.<ServiceRequestPartRequest>getArgument(0).id = 100L; return null; })
                .when(requests).saveAndFlush(any(ServiceRequestPartRequest.class));

        assertDoesNotThrow(() -> service.submit(new RequestWrite(42L, "Needs replacement", List.of(new LineWrite(11L, 1)))));
        verify(requests).saveAndFlush(any(ServiceRequestPartRequest.class));
        verify(lines).save(any(ServiceRequestPartLine.class));
    }

    @Test
    void missingTechnicianProfileIsDeniedBeforePersistence() {
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(7L);
        when(identities.actor()).thenReturn(actor);
        when(technicians.findByUserId(7L)).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.submit(new RequestWrite(42L, null, List.of(new LineWrite(11L, 1)))));
        verifyNoInteractions(serviceRequests, requests, lines, items);
    }

    @Test
    void nonexistentServiceRequestIsRejectedBeforePartRequestPersistence() {
        User actor = mock(User.class);
        TechnicianProfile technician = mock(TechnicianProfile.class);
        when(actor.getId()).thenReturn(7L);
        when(identities.actor()).thenReturn(actor);
        when(technicians.findByUserId(7L)).thenReturn(Optional.of(technician));
        when(serviceRequests.findById(42L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submit(new RequestWrite(42L, null, List.of(new LineWrite(11L, 1)))));
        assertEquals("Service request not found", error.getMessage());
        verifyNoInteractions(requests, lines, items);
    }

    @Test
    void terminalServiceRequestIsRejectedBeforePersistence() {
        User actor = mock(User.class);
        TechnicianProfile technician = mock(TechnicianProfile.class);
        when(actor.getId()).thenReturn(7L);
        when(identities.actor()).thenReturn(actor);
        when(technicians.findByUserId(7L)).thenReturn(Optional.of(technician));
        ServiceRequest closed = mock(ServiceRequest.class);
        when(closed.getStatus()).thenReturn(RequestStatus.COMPLETED);
        when(serviceRequests.findById(42L)).thenReturn(Optional.of(closed));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.submit(new RequestWrite(42L, null, List.of(new LineWrite(11L, 1)))));
        assertEquals("Service request is closed", error.getMessage());
        verifyNoInteractions(requests, lines, items);
    }

    @Test
    void customerCatalogOmitsInternalInventoryFields() {
        User actor = mock(User.class);
        PartItem item = partItem();
        when(actor.getRole()).thenReturn(Role.CUSTOMER);
        when(identities.actor()).thenReturn(actor);
        when(items.search(isNull(), isNull(), eq(false), any())).thenReturn(new PageImpl<>(List.of(item)));
        when(items.findWithCategoryById(11L)).thenReturn(Optional.of(item));

        Object listed = service.catalog(null, null, false, PageRequest.of(0, 20)).getContent().get(0);
        Object detailed = service.item(11L);

        assertInstanceOf(PartsDtos.CustomerItemView.class, listed);
        assertInstanceOf(PartsDtos.CustomerItemView.class, detailed);
        assertFalse(listed.toString().contains("quantityOnHand"));
        assertFalse(listed.toString().contains("reorderThreshold"));
    }

    @Test
    void technicianCatalogPreservesCatalogIdentityFieldsWithoutInventoryQuantities() {
        User actor = mock(User.class);
        PartItem item = partItem();
        when(actor.getRole()).thenReturn(Role.TECHNICIAN);
        when(identities.actor()).thenReturn(actor);
        when(items.search(isNull(), isNull(), eq(false), any())).thenReturn(new PageImpl<>(List.of(item)));

        Object listed = service.catalog(null, null, false, PageRequest.of(0, 20)).getContent().get(0);

        assertInstanceOf(PartsDtos.TechnicianItemView.class, listed);
        PartsDtos.TechnicianItemView view = (PartsDtos.TechnicianItemView) listed;
        assertEquals("SKU-11", view.sku());
        assertEquals("compat", view.compatibilityMetadata());
    }

    @Test
    void adminCatalogRetainsFullInventoryFields() {
        User actor = mock(User.class);
        PartItem item = partItem();
        when(actor.getRole()).thenReturn(Role.ADMIN);
        when(identities.actor()).thenReturn(actor);
        when(items.search(isNull(), isNull(), eq(true), any())).thenReturn(new PageImpl<>(List.of(item)));

        Object listed = service.catalog(null, null, true, PageRequest.of(0, 20)).getContent().get(0);

        assertInstanceOf(PartsDtos.ItemView.class, listed);
        PartsDtos.ItemView view = (PartsDtos.ItemView) listed;
        assertEquals(7, view.quantityOnHand());
        assertEquals(2, view.reorderThreshold());
        assertEquals("SKU-11", view.sku());
    }

    @Test
    void unsupportedRoleCannotReadCatalog() {
        User actor = mock(User.class);
        when(actor.getRole()).thenReturn(null);
        when(identities.actor()).thenReturn(actor);

        assertThrows(AccessDeniedException.class,
                () -> service.catalog(null, null, false, PageRequest.of(0, 20)));
        verifyNoInteractions(items);
    }

    private PartItem partItem() {
        PartCategory category = new PartCategory();
        category.id = 3L;
        category.name = "Filters";
        category.active = true;
        PartItem item = new PartItem();
        item.id = 11L;
        item.category = category;
        item.name = "Filter";
        item.sku = "SKU-11";
        item.description = "Replacement filter";
        item.unit = "piece";
        item.quantityOnHand = 7;
        item.reorderThreshold = 2;
        item.active = true;
        item.compatibilityMetadata = "compat";
        return item;
    }

    private ServiceRequest serviceRequest(Long id, RequestStatus status) {
        ServiceRequest row = mock(ServiceRequest.class);
        when(row.getId()).thenReturn(id);
        when(row.getStatus()).thenReturn(status);
        return row;
    }

    private ServiceRequestPartRequest savedRequest(Long id, ServiceRequest request, TechnicianProfile technician) {
        ServiceRequestPartRequest row = new ServiceRequestPartRequest();
        row.id = id;
        row.serviceRequest = request;
        row.technician = technician;
        row.status = PartRequestStatus.SUBMITTED;
        return row;
    }
}
