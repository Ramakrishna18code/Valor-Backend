package com.valor.assets;
import io.swagger.v3.oas.annotations.Operation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static com.valor.assets.AssetDtos.*;

@RestController
@RequestMapping("/api/v1")
class AssetController {
    private final AssetService assets;
    AssetController(AssetService assets) { this.assets = assets; }

    @Operation(operationId="buildings")
    @GetMapping("/buildings")
    ApiResponse<List<BuildingView>> buildings(@RequestParam(required=false) String status,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ok(slice(assets.buildings().stream().filter(b->status==null||status.equals(b.status())).toList(),page,size));
    }
    @Operation(operationId="createBuilding")
    @PostMapping("/buildings")
    ApiResponse<BuildingView> createBuilding(@Valid @RequestBody BuildingWrite input) { return ok(assets.createBuilding(input)); }
    @Operation(operationId="updateBuilding")
    @PutMapping("/buildings/{id}")
    ApiResponse<BuildingView> updateBuilding(@PathVariable Long id, @Valid @RequestBody BuildingWrite input) {
        return ok(assets.updateBuilding(id, input));
    }
    @Operation(operationId="deactivateBuilding")
    @DeleteMapping("/buildings/{id}")
    ApiResponse<Void> deactivateBuilding(@PathVariable Long id) {
        assets.deactivateBuilding(id);
        return ApiResponse.success("Building deactivated", null, 200);
    }

    @Operation(operationId="lifts")
    @GetMapping("/lifts")
    ApiResponse<List<LiftView>> lifts(@RequestParam(required=false) LiftStatus status,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ok(slice(assets.lifts().stream().filter(l->status==null||status==l.currentStatus()).toList(),page,size));
    }
    @Operation(operationId="liftCatalog")
    @GetMapping("/lift-catalog")
    ApiResponse<LiftCatalog> liftCatalog() { return ok(assets.liftCatalog()); }
    @Operation(operationId="createLift")
    @PostMapping("/lifts")
    ApiResponse<LiftView> createLift(@Valid @RequestBody LiftWrite input) { return ok(assets.createLift(input)); }
    @Operation(operationId="updateLift")
    @PutMapping("/lifts/{id}")
    ApiResponse<LiftView> updateLift(@PathVariable Long id, @Valid @RequestBody LiftWrite input) {
        return ok(assets.updateLift(id, input));
    }
    @Operation(operationId="deactivateLift")
    @DeleteMapping("/lifts/{id}")
    ApiResponse<Void> deactivateLift(@PathVariable Long id) {
        assets.deactivateLift(id);
        return ApiResponse.success("Lift deactivated", null, 200);
    }

    @Operation(operationId="contracts")
    @GetMapping("/amc-contracts")
    ApiResponse<List<AmcView>> contracts(@RequestParam(required=false) AmcStatus status,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ok(slice(assets.contracts().stream().filter(a->status==null||status==a.status()).toList(),page,size));
    }
    @Operation(operationId="createContract")
    @PostMapping("/amc-contracts")
    ApiResponse<AmcView> createContract(@Valid @RequestBody AmcWrite input) { return ok(assets.createContract(input)); }
    @Operation(operationId="renewContract")
    @PutMapping("/amc-contracts/{id}/renew")
    ApiResponse<AmcView> renewContract(@PathVariable Long id, @Valid @RequestBody AmcRenew input) {
        return ok(assets.renewContract(id, input));
    }

    @Operation(operationId="getCustomerBuildings") @GetMapping("/customers/me/buildings")
    ApiResponse<List<BuildingView>> customerBuildings(){return ok(assets.customerBuildings());}
    @Operation(operationId="getCustomerBuilding") @GetMapping("/customers/me/buildings/{id}")
    ApiResponse<BuildingView> customerBuilding(@PathVariable Long id){return ok(assets.customerBuilding(id));}
    @Operation(operationId="createCustomerBuilding") @PostMapping("/customers/me/buildings")
    ApiResponse<BuildingView> customerBuilding(@Valid @RequestBody CustomerBuildingWrite input){return ok(assets.createCustomerBuilding(input));}
    @Operation(operationId="updateCustomerBuilding") @PutMapping("/customers/me/buildings/{id}")
    ApiResponse<BuildingView> updateCustomerBuilding(@PathVariable Long id, @Valid @RequestBody CustomerBuildingWrite input){return ok(assets.updateCustomerBuilding(id,input));}
    @Operation(operationId="deactivateCustomerBuilding") @DeleteMapping("/customers/me/buildings/{id}")
    ApiResponse<Void> deactivateCustomerBuilding(@PathVariable Long id){assets.deactivateCustomerBuilding(id);return ApiResponse.success("Building deactivated",null,200);}
    @Operation(operationId="getCustomerLifts") @GetMapping("/customers/me/lifts")
    ApiResponse<List<LiftView>> customerLifts(){return ok(assets.customerLifts());}
    @Operation(operationId="getCustomerLift") @GetMapping("/customers/me/lifts/{id}")
    ApiResponse<LiftView> customerLift(@PathVariable Long id){return ok(assets.customerLift(id));}
    @Operation(operationId="createCustomerLift") @PostMapping("/customers/me/lifts")
    ApiResponse<LiftView> customerLift(@Valid @RequestBody LiftWrite input){return ok(assets.createCustomerLift(input));}
    @Operation(operationId="updateCustomerLift") @PutMapping("/customers/me/lifts/{id}")
    ApiResponse<LiftView> updateCustomerLift(@PathVariable Long id,@Valid @RequestBody LiftWrite input){return ok(assets.updateCustomerLift(id,input));}
    @Operation(operationId="deactivateCustomerLift") @DeleteMapping("/customers/me/lifts/{id}")
    ApiResponse<Void> deactivateCustomerLift(@PathVariable Long id){assets.deactivateCustomerLift(id);return ApiResponse.success("Lift deactivated",null,200);}

    private <T> List<T> slice(List<T> rows,Integer page,Integer size) {
        if(page==null&&size==null)return rows;
        int index=page==null?0:page,limit=size==null?20:size;
        if(index<0||limit<1||limit>100)throw new IllegalArgumentException("Invalid page");
        return rows.stream().skip((long)index*limit).limit(limit).toList();
    }
    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success("Success", data, 200); }
}
