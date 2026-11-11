package com.valor.commerce;

import com.valor.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class ServiceTeamController {
    private final ServiceTeamService team;
    private final FinanceEvidenceService evidence;
    ServiceTeamController(ServiceTeamService team,FinanceEvidenceService evidence){this.team=team;this.evidence=evidence;}
    private <T> ApiResponse<T> ok(T value){return ApiResponse.success("Service team",value,200);}
    @GetMapping("/service-requests/{id}/team") ApiResponse<Map<String,Object>> view(@PathVariable long id){return ok(team.view(id));}
    @GetMapping("/technician/me/helper-candidates") ApiResponse<java.util.List<Map<String,Object>>> candidates(){return ok(team.candidates());}
    @GetMapping("/technician/me/helper-assignments") ApiResponse<java.util.List<Map<String,Object>>> assignments(){return ok(team.helperAssignments());}
    @PutMapping("/admin/technicians/{id}/public-name") ApiResponse<Map<String,Object>> publicName(@PathVariable long id,@RequestBody ServiceTeamService.PublicName input){return ok(team.publicName(id,input));}
    @PostMapping("/technician/me/jobs/{id}/helpers") ApiResponse<Map<String,Object>> request(@PathVariable long id,@RequestBody ServiceTeamService.HelperRequest input){return ok(team.request(id,input));}
    @PostMapping("/technician/me/helpers/{id}/work-logs") ApiResponse<Map<String,Object>> log(@PathVariable long id,@RequestBody ServiceTeamService.WorkLog input){return ok(team.log(id,input));}
    @PostMapping("/admin/finance-workflow/helpers/{id}/review") ApiResponse<Map<String,Object>> review(@PathVariable long id,@RequestBody ServiceTeamService.Review input){return ok(team.review(id,input));}
    @PostMapping("/admin/finance-workflow/helper-work-logs/{id}/review") ApiResponse<Map<String,Object>> reviewLog(@PathVariable long id,@RequestBody ServiceTeamService.LogReview input){return ok(team.reviewLog(id,input));}
    @PostMapping("/admin/finance-workflow/helpers/{id}/allowance") ApiResponse<Map<String,Object>> allowance(@PathVariable long id,@RequestBody ServiceTeamService.Allowance input){return ok(team.allowance(id,input));}
    @PostMapping("/admin/finance-workflow/helper-allowances/{id}/approve") ApiResponse<Map<String,Object>> approve(@PathVariable long id){return ok(team.approveAllowance(id));}
    @PostMapping(value="/admin/finance-workflow/helper-allowances/{id}/evidence",consumes="multipart/form-data") ApiResponse<Map<String,Object>> evidence(@PathVariable long id,@RequestPart("file") org.springframework.web.multipart.MultipartFile file){return ok(evidence.uploadHelper(id,file));}
    @PostMapping("/admin/finance-workflow/helper-allowances/{id}/pay") ApiResponse<Map<String,Object>> pay(@PathVariable long id,@RequestBody ServiceTeamService.Transfer input){return ok(team.payAllowance(id,input));}
}
