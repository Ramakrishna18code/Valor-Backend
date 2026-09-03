package com.valor.response;

import com.valor.entity.Customer;

/** Safe customer representation for API responses. */
public record CustomerProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        boolean enabled
) {
    public static CustomerProfileResponse from(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getName(), customer.getEmail(),
                customer.getPhone(), customer.getRole().name(), Boolean.TRUE.equals(customer.getEnabled()));
    }
}
