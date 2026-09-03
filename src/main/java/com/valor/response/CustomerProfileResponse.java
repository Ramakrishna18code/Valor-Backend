package com.valor.response;

import com.valor.entity.Customer;

/** Safe customer representation for API responses. */
public record CustomerProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        boolean enabled,
        String address,
        String city,
        String state,
        String pincode,
        String accountStatus
) {
    public static CustomerProfileResponse from(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getName(), customer.getEmail(),
            customer.getPhone(), customer.getRole().name(), Boolean.TRUE.equals(customer.getEnabled()),
            customer.getAddress() == null ? null : customer.getAddress().getStreet(),
            customer.getAddress() == null ? null : customer.getAddress().getCity(),
            customer.getAddress() == null ? null : customer.getAddress().getState(),
            customer.getAddress() == null ? null : customer.getAddress().getPincode(),
            customer.getAccountStatus() == null ? null : customer.getAccountStatus().name());
    }
}
