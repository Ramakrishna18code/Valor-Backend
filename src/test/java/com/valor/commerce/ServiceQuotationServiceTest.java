package com.valor.commerce;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.AuditService;
import com.valor.auth.CustomerProfile;
import com.valor.auth.Role;
import com.valor.auth.User;
import com.valor.pricing.PricingCatalogue;
import com.valor.workflow.RequestStatus;
import com.valor.workflow.ServiceRequest;
import com.valor.workflow.AssignmentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import static com.valor.commerce.ServiceQuotationDtos.Decision;
import static com.valor.commerce.ServiceQuotationDtos.ServiceQuotationDecision.ACCEPT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import static com.valor.commerce.ServiceQuotationDtos.Create;

@ExtendWith(MockitoExtension.class)
class ServiceQuotationServiceTest {
    @Mock private AssetIdentityAccess identities;
    @Mock private ServiceQuotationRepository quotations;
    @Mock private QuotationServiceRequestRepository requests;
    @Mock private CommerceAssignmentRepository assignments;
    @Mock private InvoiceRepository invoices;
    @Mock private PricingCatalogue catalogue;
    @Mock private AuditService audit;

    private ServiceQuotationService service;
    private ServiceRequest request;

    @BeforeEach
    void setUp() {
        service = new ServiceQuotationService(identities, quotations, requests, assignments, invoices, catalogue, audit);
        User admin = new User();
        admin.setRole(Role.ADMIN);
        lenient().when(identities.actor()).thenReturn(admin);
        request = mock(ServiceRequest.class);
        lenient().when(request.getStatus()).thenReturn(RequestStatus.PENDING);
        lenient().when(requests.lockForQuotation(42L)).thenReturn(Optional.of(request));
    }

    @Test
    void fixedCataloguePriceCannotBeOverriddenInQuotation() {
        var item = new PricingCatalogue.Item("BREAKDOWN", "EMERGENCY_SERVICE", "Breakdown call-out",
                "Approved price", PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null,
                new BigDecimal("2000.00"), null, null, false, "APPROVED");
        when(catalogue.findByCode("BREAKDOWN")).thenReturn(Optional.of(item));
        when(catalogue.approvedForFinancialUse(item)).thenReturn(true);

        assertThrows(CommerceException.class, () -> service.createForServiceRequest(42L,
                new Create("BREAKDOWN", "Breakdown call-out", new BigDecimal("1999.99"),
                        BigDecimal.ZERO, "INR", null, null)));
        verify(quotations, never()).saveAndFlush(any());
    }

    @Test
    void rangedCataloguePriceCannotBeQuotedOutsidePublishedRange() {
        var item = new PricingCatalogue.Item("REPAIR", "REPAIR_SPARE_PART", "Repair",
                "Approved price range", PricingCatalogue.PricingType.RANGE, "PER_JOB", null, null, null,
                null, new BigDecimal("1800.00"), new BigDecimal("3000.00"), false, "APPROVED");
        when(catalogue.findByCode("REPAIR")).thenReturn(Optional.of(item));
        when(catalogue.approvedForFinancialUse(item)).thenReturn(true);

        assertThrows(CommerceException.class, () -> service.createForServiceRequest(42L,
                new Create("REPAIR", "Repair", new BigDecimal("3000.01"),
                        BigDecimal.ZERO, "INR", null, null)));
        verify(quotations, never()).saveAndFlush(any());
    }

    @Test
    void itemizedQuotationUsesPublishedAndQuoteRequiredUnitPricesAndBackendTotals() {
        var fixed = new PricingCatalogue.Item("INSPECT", "ONE_TIME_SERVICE", "Lift inspection",
                "Published price", PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null,
                new BigDecimal("999.00"), null, null, false, "APPROVED");
        var custom = new PricingCatalogue.Item("ENTRAPMENT", "EMERGENCY_SERVICE", "Passenger entrapment",
                "Requires assessment", PricingCatalogue.PricingType.QUOTE_REQUIRED, "PER_JOB", null, null, null,
                null, null, null, true, "APPROVED");
        when(catalogue.findByCode("INSPECT")).thenReturn(Optional.of(fixed));
        when(catalogue.findByCode("ENTRAPMENT")).thenReturn(Optional.of(custom));
        when(catalogue.approvedForFinancialUse(fixed)).thenReturn(true);
        when(catalogue.approvedForFinancialUse(custom)).thenReturn(true);
        when(quotations.saveAndFlush(any(ServiceQuotation.class))).thenAnswer(invocation -> {
            ServiceQuotation row = invocation.getArgument(0);
            row.setId(12L);
            return row;
        });

        var result = service.createForServiceRequest(42L, new Create(null, null, null, BigDecimal.ZERO,
                "INR", null, java.util.List.of(
                        new ServiceQuotationDtos.LineItemCreate("INSPECT", null, 2, null),
                        new ServiceQuotationDtos.LineItemCreate("ENTRAPMENT", "Rescue response", 1,
                                new BigDecimal("2500.00")))));

        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("4498.00"), result.subtotal());
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.ZERO.setScale(2), result.taxAmount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("4498.00"), result.totalAmount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.items().size());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1998.00"), result.items().get(0).lineAmount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("2500.00"), result.items().get(1).lineAmount());
        verify(audit).record(eq("SERVICE_QUOTATION_ISSUED"), eq("SERVICE_QUOTATION"), eq(12L), contains("4498.00"));
    }

    @Test
    void taxAmountCannotBeAddedToTaxInclusiveServicePrices() {
        assertThrows(CommerceException.class, () -> service.createForServiceRequest(42L,
                new Create(null, null, null, new BigDecimal("180.00"), "INR", null,
                        java.util.List.of(new ServiceQuotationDtos.LineItemCreate("CUSTOM", "Service", 1,
                                new BigDecimal("1000.00"))))));
        verify(quotations, never()).saveAndFlush(any());
    }

    @Test
    void customerCannotReadAnotherCustomersQuotation() {
        User customerActor = mock(User.class);
        when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        when(customerActor.getId()).thenReturn(7L);
        when(identities.actor()).thenReturn(customerActor);
        when(quotations.findForCustomerById(9L, 7L)).thenReturn(Optional.empty());

        assertThrows(CommerceException.class, () -> service.customerQuotation(9L));
        verify(quotations).findForCustomerById(9L, 7L);
    }

    @Test
    void technicianReadsOnlyAssignedJobQuotationHistory() {
        User technician = mock(User.class);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(technician.getId()).thenReturn(51L);
        when(identities.actor()).thenReturn(technician);
        when(assignments.existsByRequestIdAndTechnicianUserIdAndStatusIn(42L, 51L,
                java.util.List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED, AssignmentStatus.COMPLETED)))
                .thenReturn(true);
        ServiceQuotation quote = mock(ServiceQuotation.class);
        when(quote.getServiceRequest()).thenReturn(request);
        when(request.getId()).thenReturn(42L);
        when(quotations.findByServiceRequestIdOrderByCreatedAtDescIdDesc(42L)).thenReturn(java.util.List.of(quote));

        var result = service.technicianQuotations(42L);

        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        verify(quotations).findByServiceRequestIdOrderByCreatedAtDescIdDesc(42L);
    }

    @Test
    void technicianCannotReadQuotationHistoryForUnassignedJob() {
        User technician = mock(User.class);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(technician.getId()).thenReturn(51L);
        when(identities.actor()).thenReturn(technician);
        when(assignments.existsByRequestIdAndTechnicianUserIdAndStatusIn(eq(42L), eq(51L), anyList())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.technicianQuotations(42L));
        verifyNoInteractions(quotations);
    }

    @Test
    void acceptingQuotationCreatesInvoiceFromBackendQuotationAmounts() {
        User customerActor = mock(User.class);
        when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        when(customerActor.getId()).thenReturn(7L);
        when(identities.actor()).thenReturn(customerActor);
        CustomerProfile customer = mock(CustomerProfile.class);
        when(customer.getUser()).thenReturn(customerActor);
        when(request.getId()).thenReturn(42L);
        when(request.getTitle()).thenReturn("Repair");

        ServiceQuotation quotation = new ServiceQuotation();
        quotation.setId(9L);
        quotation.setCustomer(customer);
        quotation.setServiceRequest(request);
        quotation.setCreatedBy(new User());
        quotation.setDescription("Approved repair quotation");
        quotation.setSubtotal(new BigDecimal("2000.00"));
        quotation.setTaxAmount(new BigDecimal("0.00"));
        quotation.setTotalAmount(new BigDecimal("2000.00"));
        quotation.setCurrency("INR");
        quotation.setStatus(ServiceQuotationStatus.ISSUED);
        when(quotations.lockById(9L)).thenReturn(Optional.of(quotation));
        when(quotations.findById(9L)).thenReturn(Optional.of(quotation));
        when(requests.lockForQuotation(42L)).thenReturn(Optional.of(request));
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(51L);
            return invoice;
        });

        var result = service.decide(9L, new Decision(ACCEPT));

        org.junit.jupiter.api.Assertions.assertEquals(ServiceQuotationStatus.ACCEPTED, result.status());
        org.junit.jupiter.api.Assertions.assertEquals(51L, result.invoiceId());
        verify(invoices).saveAndFlush(argThat(invoice ->
                invoice.getSubtotal().compareTo(new BigDecimal("2000.00")) == 0
                        && invoice.getTaxAmount().compareTo(BigDecimal.ZERO) == 0
                        && invoice.getTotalAmount().compareTo(new BigDecimal("2000.00")) == 0));
        assertThrows(CommerceException.class, () -> service.decide(9L, new Decision(ACCEPT)));
        verify(invoices, times(1)).saveAndFlush(any(Invoice.class));
    }

    @Test
    void adminCannotUseCustomerQuotationDecision() {
        assertThrows(AccessDeniedException.class,
                () -> service.customerQuotation(9L));
        verifyNoInteractions(quotations);
    }
}
