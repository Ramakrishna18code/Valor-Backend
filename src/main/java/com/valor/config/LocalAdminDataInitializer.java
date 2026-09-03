package com.valor.config;

import com.valor.entity.Admin;
import com.valor.enums.RoleName;
import com.valor.repository.AdminRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("test")
public class LocalAdminDataInitializer {

    @Bean
    public CommandLineRunner seedLocalAdmins(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createIfMissing(adminRepository, passwordEncoder, "admin@valor.com", "Admin User", "ADMIN001", "9000000001");
            createIfMissing(adminRepository, passwordEncoder, "ops@valor.com", "Operations Admin", "ADMIN002", "9000000002");
        };
    }

    private void createIfMissing(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                                 String email, String name, String employeeId, String phone) {
        if (adminRepository.findByEmail(email).isPresent()) {
            return;
        }
        adminRepository.save(Admin.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .employeeId(employeeId)
                .password(passwordEncoder.encode("Admin@123"))
                .role(RoleName.ADMIN)
                .designation("Operations Administrator")
                .active(true)
                .build());
    }
}
