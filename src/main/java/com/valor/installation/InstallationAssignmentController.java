package com.valor.installation;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") class InstallationAssignmentController {
 private final InstallationAssignmentService service; InstallationAssignmentController(InstallationAssignmentService s){service=s;}
 @GetMapping("/admin/installations/{id}/assignments") ApiResponse<List<InstallationAssignmentService.View>> adminList(@PathVariable Long id){return ok(service.adminList(id));}
 @PostMapping("/admin/installations/{id}/assignments") ApiResponse<InstallationAssignmentService.View> assign(@PathVariable Long id,@Valid @RequestBody Write input){return ok(service.assign(id,input.technicianProfileId(),input.notes()));}
 @PostMapping("/admin/installations/assignments/{id}/cancel") ApiResponse<InstallationAssignmentService.View> cancel(@PathVariable Long id,@RequestParam long version){return ok(service.cancel(id,version));}
 @GetMapping("/technician/me/installations/assignments") ApiResponse<List<InstallationAssignmentService.View>> technicianList(){return ok(service.technicianList());}
 @PostMapping("/technician/me/installations/assignments/{id}/accept") ApiResponse<InstallationAssignmentService.View> accept(@PathVariable Long id,@RequestParam long version){return ok(service.respond(id,version,true));}
 @PostMapping("/technician/me/installations/assignments/{id}/decline") ApiResponse<InstallationAssignmentService.View> decline(@PathVariable Long id,@RequestParam long version){return ok(service.respond(id,version,false));}
 private static <T> ApiResponse<T> ok(T body){return ApiResponse.success("Success",body,200);} record Write(@NotNull Long technicianProfileId,String notes){}
}
