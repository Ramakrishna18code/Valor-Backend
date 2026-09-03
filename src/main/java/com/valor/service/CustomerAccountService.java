package com.valor.service;

import com.valor.dto.CustomerDto;
import com.valor.dto.CustomerProfileUpdateDto;
import com.valor.entity.Customer;
import com.valor.enums.CustomerStatus;
import com.valor.enums.RoleName;
import com.valor.entity.Address;
import com.valor.exception.DuplicateResourceException;
import com.valor.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class CustomerAccountService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Customer registerCustomer(CustomerDto customerDto) {
        if (customerRepository.existsByEmail(customerDto.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (customerDto.getPhone() != null && customerRepository.existsByPhone(customerDto.getPhone())) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        Customer customer = Customer.builder()
                .name(customerDto.getName())
                .email(customerDto.getEmail())
                .password(passwordEncoder.encode(customerDto.getPassword()))
                .phone(customerDto.getPhone())
                .role(RoleName.CUSTOMER)
                .address(Address.builder().street(customerDto.getAddress()).city(customerDto.getCity())
                    .state(customerDto.getState()).pincode(customerDto.getPincode()).build())
                .accountStatus(customerDto.getAccountStatus() == null ? CustomerStatus.ACTIVE : customerDto.getAccountStatus())
                .enabled(true)
                .build();

        return customerRepository.save(customer);
    }

    public Customer loginCustomer(String email, String password) {
        Optional<Customer> customerOpt = customerRepository.findByEmail(email);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            if (Boolean.TRUE.equals(customer.getEnabled())
                    && customer.getAccountStatus() == CustomerStatus.ACTIVE
                    && passwordEncoder.matches(password, customer.getPassword())) {
                return customer;
            }
        }
        return null;
    }

    public boolean existsByEmail(String email) {
        return customerRepository.existsByEmail(email);
    }

    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Optional<Customer> getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public Optional<Customer> getCustomerByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }

    public Customer updateProfile(Long id, CustomerProfileUpdateDto customerDto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        customer.setName(customerDto.getName());
        customer.setPhone(customerDto.getPhone());
        return customerRepository.save(customer);
    }

    public void deleteAccount(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        customer.setEnabled(false);
        customerRepository.save(customer);
    }
}