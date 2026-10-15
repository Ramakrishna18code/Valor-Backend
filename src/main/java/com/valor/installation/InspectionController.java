package com.valor.installation;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.valor.installation.InspectionDtos.*;
import com.valor.workflow.WorkflowDtos.PageView;
import java.time.LocalDateTime;

@RestController @RequestMapping("/api/v1") class InspectionController {
 private final InspectionService service; InspectionController(InspectionService service){this.service=service;}
 @GetMapping("/customers/me/installations/{requestId}/inspection") ApiResponse<View> customer(@PathVariable Long requestId){return ok(service.customerRead(requestId));}
 @PostMapping("/admin/installations/{requestId}/inspection") ApiResponse<View> request(@PathVariable Long requestId,@Valid @RequestBody RequestInspectionRequest input){return ok(service.request(requestId,input));}
 @GetMapping("/admin/installations/inspections/{id}") ApiResponse<View> admin(@PathVariable Long id){return ok(service.adminRead(id));}
 @GetMapping("/admin/installations/{requestId}/inspection") ApiResponse<View> adminForInstallation(@PathVariable Long requestId){return ok(service.adminReadForInstallation(requestId));}
 @GetMapping("/admin/installations/inspections") ApiResponse<PageView<Summary>> adminQueue(@RequestParam(required=false) InspectionStatus status,@RequestParam(required=false) Long technicianProfileId,@RequestParam(required=false) LocalDateTime from,@RequestParam(required=false) LocalDateTime to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){var p=service.adminQueue(status,technicianProfileId,from,to,page,size);return ok(new PageView<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()));}
 @PutMapping("/admin/installations/inspections/{id}/schedule") ApiResponse<View> schedule(@PathVariable Long id,@Valid @RequestBody ScheduleRequest in){return ok(service.schedule(id,in));}
 @PutMapping("/admin/installations/inspections/{id}/assign") ApiResponse<View> assign(@PathVariable Long id,@Valid @RequestBody AssignRequest in){return ok(service.assign(id,in));}
 @PutMapping("/admin/installations/inspections/{id}/reschedule") ApiResponse<View> reschedule(@PathVariable Long id,@Valid @RequestBody RescheduleRequest in){return ok(service.reschedule(id,in));}
 @PostMapping("/admin/installations/inspections/{id}/cancel") ApiResponse<View> cancel(@PathVariable Long id,@Valid @RequestBody CancelRequest in){return ok(service.cancel(id,in));}
 @PostMapping("/admin/installations/inspections/{id}/review") ApiResponse<View> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest in){return ok(service.review(id,in));}
 @GetMapping("/technician/me/inspections/{id}") ApiResponse<View> technician(@PathVariable Long id){return ok(service.technicianRead(id));}
 @GetMapping("/technician/me/inspections") ApiResponse<PageView<Summary>> technicianQueue(@RequestParam(required=false) InspectionStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){var p=service.technicianQueue(status,page,size);return ok(new PageView<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()));}
 @PostMapping("/technician/me/inspections/{id}/accept") ApiResponse<View> accept(@PathVariable Long id,@RequestParam long version){return ok(service.accept(id,version));}
 @PostMapping("/technician/me/inspections/{id}/start") ApiResponse<View> start(@PathVariable Long id){return ok(service.start(id));}
 @PutMapping("/technician/me/inspections/{id}/assessment") ApiResponse<View> assess(@PathVariable Long id,@Valid @RequestBody AssessmentRequest in){return ok(service.assess(id,in));}
 @PostMapping("/technician/me/inspections/{id}/evidence") ApiResponse<View> evidence(@PathVariable Long id,@Valid @RequestBody EvidenceRequest in){return ok(service.addEvidence(id,in));}
 private <T> ApiResponse<T> ok(T value){return ApiResponse.success("Success",value,200);}
}
