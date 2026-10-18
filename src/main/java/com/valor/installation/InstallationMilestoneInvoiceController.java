package com.valor.installation;

import com.valor.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import static com.valor.installation.InstallationMilestoneInvoiceDtos.*;

@RestController @RequestMapping("/api/v1/admin/installations/milestones")
class InstallationMilestoneInvoiceController {
    private final InstallationMilestoneInvoiceService service;
    InstallationMilestoneInvoiceController(InstallationMilestoneInvoiceService s){service=s;}
    @PostMapping("/{id}/invoice") ApiResponse<View> generate(@PathVariable Long id){return ApiResponse.success("Success",service.generate(id),200);}
}
