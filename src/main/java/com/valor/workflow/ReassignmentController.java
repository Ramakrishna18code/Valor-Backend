package com.valor.workflow;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.valor.response.ApiResponse;
import static com.valor.workflow.ReassignmentDtos.*;

@RestController @RequestMapping("/api/v1") class ReassignmentController {
    private final ReassignmentService service; ReassignmentController(ReassignmentService service){this.service=service;}
    @PostMapping("/technician/me/jobs/{id}/reassignment-requests") ApiResponse<View> create(@PathVariable Long id,@Valid @RequestBody Create input){return ok(service.create(id,input));}
    @GetMapping("/technician/me/jobs/{id}/reassignment-requests") ApiResponse<PageView<View>> technicianList(@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.technicianList(id,page,size));}
    @PostMapping("/technician/me/reassignment-requests/{id}/withdraw") ApiResponse<View> withdraw(@PathVariable Long id){return ok(service.withdraw(id));}
    @GetMapping("/admin/reassignment-requests") ApiResponse<PageView<View>> adminList(@RequestParam(required=false) ReassignmentStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.adminList(status,page,size));}
    @GetMapping("/admin/reassignment-requests/{id}") ApiResponse<View> read(@PathVariable Long id){return ok(service.adminRead(id));}
    @PostMapping("/admin/reassignment-requests/{id}/reject") ApiResponse<View> reject(@PathVariable Long id,@Valid @RequestBody Decision input){return ok(service.reject(id,input));}
    @PostMapping("/admin/reassignment-requests/{id}/approve") ApiResponse<View> approve(@PathVariable Long id,@Valid @RequestBody Approve input){return ok(service.approve(id,input));}
    @PostMapping("/admin/reassignment-requests/{id}/cancel") ApiResponse<View> cancel(@PathVariable Long id,@Valid @RequestBody Decision input){return ok(service.cancel(id,input));}
    @GetMapping("/admin/service-requests/{id}/assignment-history") ApiResponse<java.util.List<AssignmentHistory>> history(@PathVariable Long id){return ok(service.history(id));}
    private <T> ApiResponse<T> ok(T value){return ApiResponse.success("Success",value,200);}
}
