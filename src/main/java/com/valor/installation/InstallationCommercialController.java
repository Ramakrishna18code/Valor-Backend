package com.valor.installation;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") class InstallationCommercialController {
 private final InstallationProposalService service; InstallationCommercialController(InstallationProposalService service){this.service=service;}
 @GetMapping("/admin/installations/{id}/proposals") ApiResponse<List<InstallationProposalService.View>> history(@PathVariable Long id){return ok(service.history(id));}
 @PostMapping("/admin/installations/{id}/proposals") ApiResponse<InstallationProposalService.View> save(@PathVariable Long id,@Valid @RequestBody Write input){return ok(service.save(id,input.toInternal(id)));}
 @PostMapping("/admin/installations/proposals/{id}/present") ApiResponse<InstallationProposalService.View> present(@PathVariable Long id,@RequestParam long version){return ok(service.present(id,version));}
 @GetMapping("/customers/me/installations/{id}/proposal") ApiResponse<InstallationProposalService.View> customer(@PathVariable Long id){return ok(service.customer(id));}
 @GetMapping("/customers/me/installations/{id}/proposals") ApiResponse<List<InstallationProposalService.View>> customerHistory(@PathVariable Long id){return ok(service.customerHistory(id));}
 @PostMapping("/customers/me/installations/proposals/{id}/decision") ApiResponse<InstallationProposalService.View> decide(@PathVariable Long id,@Valid @RequestBody Decision input){return ok(service.decide(id,input.accepted(),input.notes(),input.version()));}
 @PostMapping("/admin/installations/{id}/start") ApiResponse<InstallationProposalService.View> start(@PathVariable Long id,@RequestParam long version){return ok(service.start(id,version));}
 @PostMapping("/admin/installations/{id}/requirements") ApiResponse<Void> requirements(@PathVariable Long id,@Valid @RequestBody Requirements input){service.reviewRequirements(id,input.requirements(),input.version());return ok(null);}
 @PostMapping("/admin/installations/{id}/complete") ApiResponse<InstallationProposalService.View> complete(@PathVariable Long id,@RequestParam long version){return ok(service.complete(id,version));}
 private static <T> ApiResponse<T> ok(T x){return ApiResponse.success("Success",x,200);}
 record Write(Long id,@NotNull @DecimalMin("0.00") BigDecimal amount,@Size(max=3) String currency,@Size(max=10000) String scope,@Size(max=10000) String notes,@Size(max=10000) String terms,LocalDateTime validUntil,Long version){InstallationProposalService.Write toInternal(Long ignored){return new InstallationProposalService.Write(id,amount,currency,scope,notes,terms,validUntil,version);}}
 record Decision(boolean accepted,@Size(max=2000) String notes,Long version){ }
 record Requirements(@NotBlank @Size(max=20000) String requirements,@NotNull Long version){ }
}
