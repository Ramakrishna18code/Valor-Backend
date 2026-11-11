package com.valor.commerce;

import com.valor.response.ApiResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class FinanceWorkflowController {
    final ServiceFinanceWorkflow jobs;final TechnicianFinanceService finance;final FinanceEvidenceService evidence;
    FinanceWorkflowController(ServiceFinanceWorkflow jobs,TechnicianFinanceService finance,FinanceEvidenceService evidence){this.jobs=jobs;this.finance=finance;this.evidence=evidence;}
    private <T> ApiResponse<T> ok(T data){return ApiResponse.success("Finance workflow",data,200);}
    @GetMapping("/service-requests/{id}/finance") ApiResponse<Map<String,Object>> job(@PathVariable long id){return ok(jobs.view(id));}
    @PostMapping("/technician/me/jobs/{id}/inspection") ApiResponse<Map<String,Object>> inspect(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.Inspection input){return ok(jobs.inspect(id,input));}
    @PostMapping("/technician/me/jobs/{id}/additional-work") ApiResponse<Map<String,Object>> work(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.Work input){return ok(jobs.requestWork(id,input));}
    @PostMapping("/technician/me/jobs/{id}/part-usage") ApiResponse<Map<String,Object>> usage(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.Usage input){return ok(jobs.usage(id,input));}
    @PostMapping("/admin/finance-workflow/inspections/{id}/review") ApiResponse<Map<String,Object>> reviewInspection(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.Review input){return ok(jobs.reviewInspection(id,input));}
    @PostMapping("/admin/finance-workflow/work/{id}/review") ApiResponse<Map<String,Object>> reviewWork(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.WorkReview input){return ok(jobs.reviewWork(id,input));}
    @PostMapping("/admin/finance-workflow/jobs/{id}/holds") ApiResponse<Map<String,Object>> hold(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.Hold input){return ok(jobs.hold(id,input));}
    @PostMapping("/admin/finance-workflow/jobs/{id}/completion-terms") ApiResponse<Map<String,Object>> completionTerms(@PathVariable long id,@RequestBody ServiceFinanceWorkflow.CompletionTerms input){return ok(jobs.completionTerms(id,input));}
    @GetMapping("/admin/finance-workflow/purchases") ApiResponse<List<Map<String,Object>>> purchases(){return ok(jobs.purchases());}
    @PostMapping("/admin/finance-workflow/purchases") ApiResponse<List<Map<String,Object>>> purchase(@RequestBody ServiceFinanceWorkflow.Purchase input){return ok(jobs.purchase(input));}
    @GetMapping("/admin/finance-workflow/rules") ApiResponse<List<Map<String,Object>>> rules(){return ok(finance.rules());}
    @PostMapping("/admin/finance-workflow/rules") ApiResponse<Map<String,Object>> rule(@RequestBody TechnicianFinanceService.Rule input){return ok(finance.rule(input));}
    @PostMapping("/admin/finance-workflow/rules/{id}/approve") ApiResponse<Map<String,Object>> approveRule(@PathVariable long id){return ok(finance.approveRule(id));}
    @PostMapping("/admin/finance-workflow/rules/{id}/retire") ApiResponse<Map<String,Object>> retireRule(@PathVariable long id){return ok(finance.retireRule(id));}
    @PostMapping("/admin/finance-workflow/commission-bases") ApiResponse<Map<String,Object>> basis(@RequestBody TechnicianFinanceService.Basis input){return ok(finance.basis(input));}
    @GetMapping("/admin/finance-workflow/commissions") ApiResponse<List<Map<String,Object>>> commissions(){return ok(finance.commissions());}
    @PostMapping("/admin/finance-workflow/commissions") ApiResponse<Map<String,Object>> accrue(@RequestBody TechnicianFinanceService.Accrue input){return ok(finance.accrue(input));}
    @PostMapping("/admin/finance-workflow/commissions/{id}/review") ApiResponse<Map<String,Object>> decide(@PathVariable long id,@RequestBody TechnicianFinanceService.Decision input){return ok(finance.decideCommission(id,input));}
    @PostMapping("/admin/finance-workflow/commissions/{id}/adjustments") ApiResponse<Map<String,Object>> adjust(@PathVariable long id,@RequestBody TechnicianFinanceService.Adjustment input){return ok(finance.adjust(id,input));}
    @GetMapping("/admin/finance-workflow/settings") ApiResponse<Map<String,Object>> settings(){return ok(finance.settings());}
    @PutMapping("/admin/finance-workflow/settings") ApiResponse<Map<String,Object>> settings(@RequestBody TechnicianFinanceService.Settings input){return ok(finance.settings(input));}
    @GetMapping("/admin/finance-workflow/payouts") ApiResponse<List<Map<String,Object>>> payouts(){return ok(finance.payouts());}
    @PostMapping("/admin/finance-workflow/payouts") ApiResponse<Map<String,Object>> batch(@RequestBody TechnicianFinanceService.Batch input){return ok(finance.batch(input));}
    @GetMapping("/admin/finance-workflow/payouts/{id}") ApiResponse<Map<String,Object>> payout(@PathVariable long id){return ok(finance.payout(id));}
    @PostMapping("/admin/finance-workflow/payouts/{id}/actions") ApiResponse<Map<String,Object>> transfer(@PathVariable long id,@RequestBody TechnicianFinanceService.Transfer input){return ok(finance.transfer(id,input));}
    @GetMapping("/technician/me/finance/earnings") ApiResponse<Map<String,Object>> earnings(){return ok(finance.earnings());}
    @GetMapping("/technician/me/finance/expenses") ApiResponse<List<Map<String,Object>>> expenses(){return ok(finance.expenses());}
    @PostMapping("/technician/me/finance/expenses") ApiResponse<List<Map<String,Object>>> expense(@RequestBody TechnicianFinanceService.Expense input){return ok(finance.expense(input));}
    @GetMapping("/admin/finance-workflow/expenses") ApiResponse<List<Map<String,Object>>> adminExpenses(){return ok(finance.expenses());}
    @PostMapping("/admin/finance-workflow/expenses/{id}/review") ApiResponse<List<Map<String,Object>>> reviewExpense(@PathVariable long id,@RequestBody TechnicianFinanceService.ExpenseReview input){return ok(finance.reviewExpense(id,input));}
    @GetMapping("/admin/finance-workflow/report") ApiResponse<Map<String,Object>> report(@RequestParam java.util.Map<String,String> filters){return ok(finance.report(filters));}
    @PostMapping(value="/admin/finance-workflow/payouts/{id}/evidence",consumes="multipart/form-data") ApiResponse<Map<String,Object>> payoutEvidence(@PathVariable long id,@RequestPart("file") org.springframework.web.multipart.MultipartFile file){return ok(evidence.upload(id,null,file));}
    @PostMapping(value="/technician/me/jobs/{id}/expense-evidence",consumes="multipart/form-data") ApiResponse<Map<String,Object>> expenseEvidence(@PathVariable long id,@RequestPart("file") org.springframework.web.multipart.MultipartFile file){return ok(evidence.upload(null,id,file));}
    @GetMapping("/admin/finance-workflow/evidence/{id}") org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadEvidence(@PathVariable long id){return download(evidence.download(id));}
    @GetMapping("/admin/finance-workflow/expenses/{id}/receipt") org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadExpenseReceipt(@PathVariable long id){return download(evidence.expenseReceipt(id));}
    private org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(CommerceService.Download file){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(file.contentType())).header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(file.filename()).build().toString()).body(file.resource());}
}
