package com.valor.commerce;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.valor.commerce.ServiceQuotationDtos.*;

@RestController
@RequestMapping("/api/v1")
class ServiceQuotationController {
    private final ServiceQuotationService service;

    ServiceQuotationController(ServiceQuotationService service) {
        this.service = service;
    }

    @GetMapping("/customers/me/quotations")
    ApiResponse<CommerceDtos.PageView<View>> customerQuotations(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success("Customer quotations", service.customerQuotations(pageable), 200);
    }

    @GetMapping("/customers/me/quotations/{id}")
    ApiResponse<View> customerQuotation(@PathVariable Long id) {
        return ApiResponse.success("Customer quotation", service.customerQuotation(id), 200);
    }

    @PostMapping("/customers/me/quotations/{id}/decision")
    ApiResponse<View> decide(@PathVariable Long id, @Valid @RequestBody Decision input) {
        return ApiResponse.success("Quotation response recorded", service.decide(id, input), 200);
    }

    @GetMapping("/admin/quotations")
    ApiResponse<CommerceDtos.PageView<View>> adminQuotations(
            @RequestParam(required = false) ServiceQuotationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success("Admin quotations", service.adminQuotations(status, pageable), 200);
    }

    @GetMapping("/technician/me/jobs/{serviceRequestId}/quotations")
    ApiResponse<java.util.List<View>> technicianQuotations(@PathVariable Long serviceRequestId) {
        return ApiResponse.success("Assigned job quotations", service.technicianQuotations(serviceRequestId), 200);
    }

    @GetMapping("/admin/quotations/{id}")
    ApiResponse<View> adminQuotation(@PathVariable Long id) {
        return ApiResponse.success("Admin quotation", service.adminQuotation(id), 200);
    }

    @PostMapping("/admin/service-requests/{serviceRequestId}/quotations")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ApiResponse<View> create(@PathVariable Long serviceRequestId, @Valid @RequestBody Create input) {
        return ApiResponse.success("Quotation issued", service.createForServiceRequest(serviceRequestId, input), 201);
    }
    @PostMapping("/admin/service-requests/{serviceRequestId}/quotation-drafts")
    ApiResponse<View> draft(@PathVariable Long serviceRequestId,@Valid @RequestBody Create input){return ApiResponse.success("Quotation draft",service.draft(serviceRequestId,input),200);}
    @PostMapping("/admin/quotations/{id}/revisions")
    ApiResponse<View> revise(@PathVariable Long id,@Valid @RequestBody Revision input){return ApiResponse.success("Quotation revision draft",service.revise(id,input),200);}
    @PostMapping("/admin/quotations/{id}/release")
    ApiResponse<View> release(@PathVariable Long id){return ApiResponse.success("Quotation released",service.release(id),200);}
    @PostMapping("/admin/quotations/{id}/payment-plan")
    ApiResponse<View> plan(@PathVariable Long id,@Valid @RequestBody Plan input){return ApiResponse.success("Quotation payment plan",service.plan(id,input),200);}
    @GetMapping(value="/customers/me/quotations/{id}/document.pdf",produces="application/pdf")
    org.springframework.http.ResponseEntity<byte[]> quotationPdf(@PathVariable Long id){var row=service.customerQuotation(id);String text="VALOR LIFT SERVICES\nQuotation "+row.quotationNumber()+" v"+row.versionNumber()+"\n"+row.description()+"\n";
        for(var item:row.items())text+=item.description()+" | "+item.quantity()+" x "+item.unitAmount()+" = "+item.lineAmount()+"\n";
        text+="Total "+row.currency()+" "+row.totalAmount()+"\nStatus "+row.status()+"\n"+(row.revisionReason()==null?"":row.revisionReason());
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=quotation-"+id+".pdf").body(CommerceService.minimalPdf(text));}
}
