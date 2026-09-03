package com.valor.security;

import com.valor.entity.Customer;
import com.valor.entity.Admin;
import com.valor.entity.Technician;
import com.valor.repository.AdminRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.TechnicianRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final TechnicianRepository technicianRepository;

    public CustomUserDetailsService(CustomerRepository customerRepository,
                                    AdminRepository adminRepository,
                                    TechnicianRepository technicianRepository) {
        this.customerRepository = customerRepository;
        this.adminRepository = adminRepository;
        this.technicianRepository = technicianRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Customer customer = customerRepository.findByEmailOrPhone(username, username).orElse(null);
        if (customer != null) {
            return User.withUsername(username)
                .password(customer.getPassword())
                .roles(customer.getRole().name())
                .build();
        }

        Admin admin = adminRepository.findByEmail(username).orElse(null);
        if (admin != null && Boolean.TRUE.equals(admin.getActive())) {
            return User.withUsername(username)
                .password(admin.getPassword())
                .roles(admin.getRole().name())
                .build();
        }

        Technician technician = technicianRepository.findByEmail(username).orElse(null);
        if (technician != null) {
            return User.withUsername(username)
                .password(technician.getPassword())
                .roles(technician.getRole().name())
                .build();
        }

        throw new UsernameNotFoundException("User not found");
    }
}
