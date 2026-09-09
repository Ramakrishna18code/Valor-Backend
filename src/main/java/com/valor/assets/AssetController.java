package com.valor.assets;

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

    @GetMapping("/buildings")
    ApiResponse<List<BuildingView>> buildings() { return ok(assets.buildings()); }
    @PostMapping("/buildings")
    ApiResponse<BuildingView> createBuilding(@Valid @RequestBody BuildingWrite input) { return ok(assets.createBuilding(input)); }
    @PutMapping("/buildings/{id}")
    ApiResponse<BuildingView> updateBuilding(@PathVariable Long id, @Valid @RequestBody BuildingWrite input) {
        return ok(assets.updateBuilding(id, input));
    }
    @DeleteMapping("/buildings/{id}")
    ApiResponse<Void> deactivateBuilding(@PathVariable Long id) {
        assets.deactivateBuilding(id);
        return ApiResponse.success("Building deactivated", null, 200);
    }

    @GetMapping("/lifts")
    ApiResponse<List<LiftView>> lifts() { return ok(assets.lifts()); }
    @PostMapping("/lifts")
    ApiResponse<LiftView> createLift(@Valid @RequestBody LiftWrite input) { return ok(assets.createLift(input)); }
    @PutMapping("/lifts/{id}")
    ApiResponse<LiftView> updateLift(@PathVariable Long id, @Valid @RequestBody LiftWrite input) {
        return ok(assets.updateLift(id, input));
    }
    @DeleteMapping("/lifts/{id}")
    ApiResponse<Void> deactivateLift(@PathVariable Long id) {
        assets.deactivateLift(id);
        return ApiResponse.success("Lift deactivated", null, 200);
    }

    @GetMapping("/amc-contracts")
    ApiResponse<List<AmcView>> contracts() { return ok(assets.contracts()); }
    @PostMapping("/amc-contracts")
    ApiResponse<AmcView> createContract(@Valid @RequestBody AmcWrite input) { return ok(assets.createContract(input)); }
    @PutMapping("/amc-contracts/{id}/renew")
    ApiResponse<AmcView> renewContract(@PathVariable Long id, @Valid @RequestBody AmcRenew input) {
        return ok(assets.renewContract(id, input));
    }

    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success("Success", data, 200); }
}
