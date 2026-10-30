package com.valor.pricing;

import com.valor.auth.AssetIdentityAccess;
import com.valor.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/pricing-catalogue")
class CustomerPricingCatalogueController {
    private final CustomerPricingCatalogueService service;
    private final AssetIdentityAccess identities;

    CustomerPricingCatalogueController(CustomerPricingCatalogueService service, AssetIdentityAccess identities) {
        this.service = service;
        this.identities = identities;
    }

    @GetMapping
    ApiResponse<CustomerPricingCatalogueResponse> get() {
        identities.requireActiveCustomerUser(identities.actor());
        return ApiResponse.success("Customer pricing catalogue", service.read(), 200);
    }
}
