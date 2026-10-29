package com.valor.pricing;

import com.valor.auth.AssetIdentityAccess;
import com.valor.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pricing-catalogue")
class PricingCatalogueController {
    private final PricingCatalogue catalogue;
    private final AssetIdentityAccess identities;

    PricingCatalogueController(PricingCatalogue catalogue, AssetIdentityAccess identities) { this.catalogue = catalogue; this.identities = identities; }

    @GetMapping
    ApiResponse<PricingCatalogue.CatalogueDocument> get() {
        identities.requireAdmin();
        return ApiResponse.success("Pricing catalogue", catalogue.read(), 200);
    }
}
