package com.valor.installation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.valor.installation.InstallationScheduleDtos.*;

@RestController @RequestMapping("/api/v1")
class InstallationScheduleController {
    private final InstallationScheduleService service;
    InstallationScheduleController(InstallationScheduleService s){service=s;}
    @GetMapping("/admin/installations/{requestId}/schedules") ApiResponse<List<View>> list(@PathVariable Long requestId){return ok(service.list(requestId));}
    @PostMapping("/admin/installations/{requestId}/schedules") ApiResponse<View> create(@PathVariable Long requestId,@Valid @RequestBody Write in){return ok(service.create(requestId,in));}
    @PostMapping("/admin/installations/schedules/{id}/submit") ApiResponse<View> submit(@PathVariable Long id){return ok(service.submit(id));}
    @PostMapping("/admin/installations/schedules/{id}/company-approve") ApiResponse<View> company(@PathVariable Long id,@Valid @RequestBody(required=false) Approval in){return ok(service.companyApprove(id,in));}
    @PostMapping("/customers/me/installations/schedules/{id}/approve") ApiResponse<View> customer(@PathVariable Long id,@Valid @RequestBody(required=false) Approval in){return ok(service.customerApprove(id,in));}
    private <T> ApiResponse<T> ok(T value){return ApiResponse.success("Success",value,200);}
}
