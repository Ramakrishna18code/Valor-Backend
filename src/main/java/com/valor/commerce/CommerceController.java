package com.valor.commerce;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.valor.commerce.CommerceDtos.*;

@RestController
@RequestMapping("/api/v1")
class CommerceController {
    private final CommerceService service;
    CommerceController(CommerceService service) { this.service = service; }

    @GetMapping("/payments")
    ApiResponse<PageView<PaymentView>> payments(@PageableDefault(size = 20) Pageable page) { return ok(service.payments(page)); }
    @PostMapping("/payments")
    ApiResponse<PaymentView> createPayment(@Valid @RequestBody PaymentCreate input) { return ok(service.createPayment(input)); }
    @GetMapping("/payments/{id}")
    ApiResponse<PaymentView> payment(@PathVariable Long id) { return ok(service.payment(id)); }
    @PutMapping("/payments/{id}/status")
    ApiResponse<PaymentView> paymentStatus(@PathVariable Long id, @Valid @RequestBody PaymentStatusUpdate input) { return ok(service.updatePaymentStatus(id, input)); }
    @PostMapping("/payments/razorpay/checkout")
    ApiResponse<RazorpayCheckoutView> razorpayCheckout(@Valid @RequestBody RazorpayCheckoutCreate input) { return ok(service.createRazorpayCheckout(input)); }
    @PostMapping("/payments/cash/otp/request")
    ApiResponse<CashPaymentOtpView> cashOtp(@Valid @RequestBody CashPaymentOtpRequest input) { return ok(service.requestCashPaymentOtp(input)); }
    @PostMapping("/payments/cash/otp/verify")
    ApiResponse<CashPaymentOtpView> verifyCashOtp(@Valid @RequestBody CashPaymentOtpVerify input) { return ok(service.verifyCashPaymentOtp(input)); }
    @GetMapping("/payments/{id}/cash-otp")
    ApiResponse<CashPaymentOtpView> cashOtpState(@PathVariable Long id) { return ok(service.latestCashPaymentOtp(id)); }
    @PostMapping("/payments/{id}/refunds")
    ApiResponse<PaymentView> refund(@PathVariable Long id, @Valid @RequestBody RefundCreate input) { return ok(service.requestRefund(id, input)); }
    @GetMapping("/payments/{id}/refunds")
    ApiResponse<List<RefundView>> refunds(@PathVariable Long id) { return ok(service.paymentRefunds(id)); }
    @GetMapping("/admin/payments/reconciliation")
    ApiResponse<List<ReconciliationIssue>> reconciliation() { return ok(service.reconciliation()); }
    @PostMapping("/webhooks/razorpay")
    ApiResponse<Void> razorpayWebhook(@RequestBody String payload, @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        service.razorpayWebhook(payload, signature); return ok(null);
    }

    @GetMapping("/invoices")
    ApiResponse<PageView<InvoiceView>> invoices(@PageableDefault(size = 20) Pageable page) { return ok(service.invoices(page)); }
    @PostMapping("/invoices")
    ApiResponse<InvoiceView> createInvoice(@Valid @RequestBody InvoiceCreate input) { return ok(service.createInvoice(input)); }
    @GetMapping("/invoices/{id}")
    ApiResponse<InvoiceView> invoice(@PathVariable Long id) { return ok(service.invoice(id)); }
    @GetMapping("/technician/me/jobs/{id}/payment")
    ApiResponse<TechnicianServicePaymentView> technicianServicePayment(@PathVariable Long id) { return ok(service.technicianServicePayment(id)); }
    @PostMapping("/admin/service-requests/{id}/invoice")
    ApiResponse<InvoiceView> serviceInvoice(@PathVariable Long id, @Valid @RequestBody(required = false) ServiceInvoiceCreate input) { return ok(service.createServiceInvoice(id, input)); }
    @PutMapping("/invoices/{id}/status")
    ApiResponse<InvoiceView> invoiceStatus(@PathVariable Long id, @Valid @RequestBody InvoiceStatusUpdate input) { return ok(service.updateInvoiceStatus(id, input)); }

    @GetMapping("/support-tickets")
    ApiResponse<PageView<SupportTicketView>> tickets(@PageableDefault(size = 20) Pageable page) { return ok(service.tickets(page)); }
    @PostMapping("/support-tickets")
    ApiResponse<SupportTicketView> createTicket(@Valid @RequestBody SupportTicketCreate input) { return ok(service.createTicket(input)); }
    @GetMapping("/support-tickets/{id}")
    ApiResponse<SupportTicketView> ticket(@PathVariable Long id) { return ok(service.ticket(id)); }
    @PutMapping("/support-tickets/{id}/status")
    ApiResponse<SupportTicketView> ticketStatus(@PathVariable Long id, @Valid @RequestBody SupportTicketStatusUpdate input) { return ok(service.updateTicket(id, input)); }

    @GetMapping("/amc-renewal-requests")
    ApiResponse<PageView<RenewalView>> renewals(@PageableDefault(size = 20) Pageable page) { return ok(service.renewals(page)); }
    @PostMapping("/customers/me/amc-contracts/{id}/renewal-requests")
    ApiResponse<RenewalView> createRenewal(@PathVariable Long id, @Valid @RequestBody RenewalCreate input) { return ok(service.createRenewal(id, input)); }
    @GetMapping("/amc-renewal-requests/{id}")
    ApiResponse<RenewalView> renewal(@PathVariable Long id) { return ok(service.renewal(id)); }
    @PutMapping("/amc-renewal-requests/{id}/quote")
    ApiResponse<RenewalView> quoteRenewal(@PathVariable Long id, @Valid @RequestBody RenewalQuote input) { return ok(service.quoteRenewal(id, input)); }
    @PutMapping("/amc-renewal-requests/{id}/status")
    ApiResponse<RenewalView> renewalStatus(@PathVariable Long id, @Valid @RequestBody RenewalStatusUpdate input) { return ok(service.updateRenewal(id, input)); }

    @GetMapping("/buildings/{id}/documents")
    ApiResponse<List<AssetDocumentView>> buildingDocuments(@PathVariable Long id) { return ok(service.listDocuments(AssetDocumentOwnerType.BUILDING, id)); }
    @PostMapping(value = "/buildings/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AssetDocumentView> uploadBuildingDocument(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return ok(service.uploadDocument(AssetDocumentOwnerType.BUILDING, id, file)); }
    @GetMapping("/buildings/{id}/documents/{documentId}")
    ResponseEntity<Resource> downloadBuildingDocument(@PathVariable Long id, @PathVariable Long documentId) { return download(service.downloadDocument(AssetDocumentOwnerType.BUILDING, id, documentId)); }
    @DeleteMapping("/buildings/{id}/documents/{documentId}")
    ApiResponse<Void> deleteBuildingDocument(@PathVariable Long id, @PathVariable Long documentId) { service.deleteDocument(AssetDocumentOwnerType.BUILDING, id, documentId); return ok(null); }

    @GetMapping("/lifts/{id}/documents")
    ApiResponse<List<AssetDocumentView>> liftDocuments(@PathVariable Long id) { return ok(service.listDocuments(AssetDocumentOwnerType.LIFT, id)); }
    @PostMapping(value = "/lifts/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AssetDocumentView> uploadLiftDocument(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return ok(service.uploadDocument(AssetDocumentOwnerType.LIFT, id, file)); }
    @GetMapping("/lifts/{id}/documents/{documentId}")
    ResponseEntity<Resource> downloadLiftDocument(@PathVariable Long id, @PathVariable Long documentId) { return download(service.downloadDocument(AssetDocumentOwnerType.LIFT, id, documentId)); }
    @DeleteMapping("/lifts/{id}/documents/{documentId}")
    ApiResponse<Void> deleteLiftDocument(@PathVariable Long id, @PathVariable Long documentId) { service.deleteDocument(AssetDocumentOwnerType.LIFT, id, documentId); return ok(null); }

    @GetMapping("/customers/me/buildings/{id}/documents")
    ApiResponse<List<AssetDocumentView>> customerBuildingDocuments(@PathVariable Long id) { return buildingDocuments(id); }
    @PostMapping(value = "/customers/me/buildings/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AssetDocumentView> uploadCustomerBuildingDocument(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return uploadBuildingDocument(id, file); }
    @GetMapping("/customers/me/buildings/{id}/documents/{documentId}")
    ResponseEntity<Resource> downloadCustomerBuildingDocument(@PathVariable Long id, @PathVariable Long documentId) { return downloadBuildingDocument(id, documentId); }
    @DeleteMapping("/customers/me/buildings/{id}/documents/{documentId}")
    ApiResponse<Void> deleteCustomerBuildingDocument(@PathVariable Long id, @PathVariable Long documentId) { return deleteBuildingDocument(id, documentId); }

    @GetMapping("/customers/me/lifts/{id}/documents")
    ApiResponse<List<AssetDocumentView>> customerLiftDocuments(@PathVariable Long id) { return liftDocuments(id); }
    @PostMapping(value = "/customers/me/lifts/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AssetDocumentView> uploadCustomerLiftDocument(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return uploadLiftDocument(id, file); }
    @GetMapping("/customers/me/lifts/{id}/documents/{documentId}")
    ResponseEntity<Resource> downloadCustomerLiftDocument(@PathVariable Long id, @PathVariable Long documentId) { return downloadLiftDocument(id, documentId); }
    @DeleteMapping("/customers/me/lifts/{id}/documents/{documentId}")
    ApiResponse<Void> deleteCustomerLiftDocument(@PathVariable Long id, @PathVariable Long documentId) { return deleteLiftDocument(id, documentId); }

    @GetMapping("/service-requests/{id}/report.pdf")
    ResponseEntity<Resource> serviceReportPdf(@PathVariable Long id) { return download(service.serviceReportPdf(id)); }

    private static <T> ApiResponse<T> ok(T data) { return ApiResponse.success("OK", data, 200); }
    private static ResponseEntity<Resource> download(CommerceService.Download file) {
        return ResponseEntity.ok().contentLength(file.size()).contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.resource());
    }
}
