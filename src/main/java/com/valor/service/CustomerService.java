package com.valor.service;

import com.valor.dto.CustomerDto;
import com.valor.dto.CustomerProfileUpdateDto;
import com.valor.entity.Customer;

import java.util.Optional;

public interface CustomerService {
    Customer registerCustomer(CustomerDto customerDto);
    Customer loginCustomer(String email, String password);
    boolean existsByEmail(String email);
    Optional<Customer> getCustomerById(Long id);
    Optional<Customer> getCustomerByEmail(String email);
    Optional<Customer> getCustomerByPhone(String phone);
    Customer updateProfile(Long id, CustomerProfileUpdateDto customerDto);
    void deleteAccount(Long id);
}
