package com.valor.workflow;
import io.swagger.v3.oas.annotations.Operation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.valor.workflow.WorkflowDtos.*;

@RestController
@RequestMapping("/api/v1")
class WorkflowController {
    private final WorkflowService service;
    private final ServiceRequestEngagementService engagement;
    WorkflowController(WorkflowService service, ServiceRequestEngagementService engagement) { this.service = service; this.engagement = engagement; }
    @Operation(operationId="serviceRequestCreate")
    @PostMapping("/service-requests")
    ApiResponse<Detail> create(@Valid @RequestBody CreateRequest input) { return ok(service.create(input)); }
    @Operation(operationId="serviceRequestRead")
    @GetMapping("/service-requests/{id}")
    ApiResponse<Detail> read(@PathVariable Long id) { return ok(service.read(id, false)); }
    @Operation(operationId="serviceRequestList")
    @GetMapping("/service-requests")
    ApiResponse<PageView<RequestView>> list(@RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestPriority priority, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ok(service.list(status, priority, page, size)); }
    @Operation(operationId="serviceRequestAssign")
    @PostMapping("/service-requests/{id}/assignments")
    ApiResponse<Detail> assign(@PathVariable Long id, @Valid @RequestBody AssignRequest input) { return ok(service.assign(id, input)); }
    @Operation(operationId="serviceRequestAccept")
    @PostMapping("/service-requests/{id}/assignments/{assignmentId}/accept")
    ApiResponse<Detail> accept(@PathVariable Long id, @PathVariable Long assignmentId) { return ok(service.accept(id, assignmentId)); }
    @Operation(operationId="serviceRequestStatus")
    @PostMapping("/service-requests/{id}/status")
    ApiResponse<Detail> status(@PathVariable Long id, @Valid @RequestBody StatusRequest input) { return ok(service.status(id, input)); }
    @Operation(operationId="serviceRequestJobs")
    @GetMapping("/technician/me/jobs")
    ApiResponse<PageView<RequestView>> jobs(@RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ok(service.jobs(status, page, size)); }
    @Operation(operationId="serviceRequestJob")
    @GetMapping("/technician/me/jobs/{id}")
    ApiResponse<Detail> job(@PathVariable Long id) { return ok(service.read(id, true)); }
    @Operation(operationId="serviceRequestReport")
    @PostMapping("/technician/me/jobs/{id}/report")
    ApiResponse<ReportView> report(@PathVariable Long id, @Valid @RequestBody ReportRequest input) { return ok(service.report(id, input)); }
    @Operation(operationId="getCustomerServiceRequests") @GetMapping("/customers/me/service-requests")
    ApiResponse<PageView<RequestView>> customerRequests(@RequestParam(required=false) RequestStatus status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.customerRequests(status,page,size));}
    @Operation(operationId="serviceRequestAttachmentList")
    @GetMapping("/service-requests/{id}/attachments")
    ApiResponse<java.util.List<AttachmentView>> attachments(@PathVariable Long id) { return ok(engagement.listAttachments(id)); }
    @Operation(operationId="serviceRequestAttachmentUpload")
    @PostMapping(value="/service-requests/{id}/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AttachmentView> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return ok(engagement.upload(id, file)); }
    @Operation(operationId="serviceRequestAttachmentDownload")
    @GetMapping("/service-requests/{id}/attachments/{attachmentId}")
    ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        var file = engagement.download(id, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.resource());
    }
    @Operation(operationId="serviceRequestAttachmentDelete")
    @DeleteMapping("/service-requests/{id}/attachments/{attachmentId}")
    ApiResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) { engagement.delete(id, attachmentId); return ok(null); }
    @Operation(operationId="serviceRequestFeedbackRead")
    @GetMapping("/service-requests/{id}/feedback")
    ApiResponse<FeedbackView> feedback(@PathVariable Long id) { return ok(engagement.ownFeedback(id)); }
    @Operation(operationId="serviceRequestFeedbackSubmit")
    @PutMapping("/service-requests/{id}/feedback")
    ApiResponse<FeedbackView> feedback(@PathVariable Long id, @Valid @RequestBody FeedbackWrite input) { return ok(engagement.upsertFeedback(id, input)); }
    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success("Success", data, 200); }
}
