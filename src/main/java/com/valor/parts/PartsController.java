package com.valor.parts;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import static com.valor.parts.PartsDtos.*;

@RestController
@RequestMapping("/api/v1")
class PartsController {
    private final PartsService service;
    PartsController(PartsService service) { this.service=service; }
    @GetMapping("/parts/categories") ApiResponse<List<CategoryView>> categories() { return ok(service.categories(false)); }
    @GetMapping("/parts") ApiResponse<Page<ItemView>> catalog(@RequestParam(required=false) String q,@RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.catalog(q,categoryId,false,PageRequest.of(page,size))); }
    @GetMapping("/parts/{id}") ApiResponse<ItemView> item(@PathVariable Long id) { return ok(service.item(id)); }
    @GetMapping("/customers/me/service-requests/{id}/parts-status") ApiResponse<List<CustomerStatusView>> customerStatus(@PathVariable Long id) { return ok(service.customerStatuses(id)); }
    @PostMapping("/technician/me/part-requests") ApiResponse<RequestView> submit(@Valid @RequestBody RequestWrite input) { return ok(service.submit(input)); }
    @GetMapping("/technician/me/part-requests") ApiResponse<Page<RequestView>> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.myRequests(PageRequest.of(page,size))); }
    @GetMapping("/technician/me/part-requests/{id}") ApiResponse<RequestView> mineDetail(@PathVariable Long id) { return ok(service.detail(id)); }
    @PostMapping("/technician/me/part-requests/{id}/cancel") ApiResponse<RequestView> cancel(@PathVariable Long id) { service.cancel(id); return ok(service.detail(id)); }

    @GetMapping("/admin/part-categories") ApiResponse<List<CategoryView>> adminCategories() { return ok(service.categories(true)); }
    @PostMapping("/admin/part-categories") ApiResponse<CategoryView> createCategory(@Valid @RequestBody CategoryWrite input) { return ok(service.saveCategory(null,input)); }
    @PatchMapping("/admin/part-categories/{id}") ApiResponse<CategoryView> updateCategory(@PathVariable Long id,@Valid @RequestBody CategoryWrite input) { return ok(service.saveCategory(id,input)); }
    @GetMapping("/admin/parts") ApiResponse<Page<ItemView>> adminParts(@RequestParam(required=false) String q,@RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.catalog(q,categoryId,true,PageRequest.of(page,size))); }
    @PostMapping("/admin/parts") ApiResponse<ItemView> createPart(@Valid @RequestBody ItemWrite input) { return ok(service.saveItem(null,input)); }
    @GetMapping("/admin/parts/{id}") ApiResponse<ItemView> adminPart(@PathVariable Long id) { service.categories(true); return ok(service.item(id)); }
    @PatchMapping("/admin/parts/{id}") ApiResponse<ItemView> updatePart(@PathVariable Long id,@Valid @RequestBody ItemWrite input) { return ok(service.saveItem(id,input)); }
    @PostMapping("/admin/parts/{id}/stock-movements") ApiResponse<ItemView> movement(@PathVariable Long id,@Valid @RequestBody StockMovementWrite input) { return ok(service.movement(id,input)); }
    @GetMapping("/admin/parts/{id}/stock-movements") ApiResponse<List<PartStockMovement>> movements(@PathVariable Long id) { return ok(service.movements(id)); }
    @GetMapping("/admin/part-requests") ApiResponse<Page<RequestView>> requests(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.adminRequests(PageRequest.of(page,size))); }
    @GetMapping("/admin/part-requests/{id}") ApiResponse<RequestView> request(@PathVariable Long id) { return ok(service.detail(id)); }
    @PostMapping("/admin/part-requests/{id}/approve") ApiResponse<RequestView> approve(@PathVariable Long id,@RequestBody(required=false) DecisionWrite input) { return ok(service.approve(id,true,input == null ? null : input.reason())); }
    @PostMapping("/admin/part-requests/{id}/reject") ApiResponse<RequestView> reject(@PathVariable Long id,@RequestBody(required=false) DecisionWrite input) { return ok(service.approve(id,false,input == null ? null : input.reason())); }
    @PostMapping("/admin/part-requests/{id}/ready") ApiResponse<RequestView> ready(@PathVariable Long id) { return ok(service.ready(id)); }
    @PostMapping("/admin/part-requests/{id}/issue") ApiResponse<RequestView> issue(@PathVariable Long id) { return ok(service.issue(id)); }
    private static <T> ApiResponse<T> ok(T value) { return ApiResponse.success("Success", value, 200); }
}
