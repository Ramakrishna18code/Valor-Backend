package com.valor.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration(proxyBeanMethods = false)
@Import(AuthConfig.class)
public class PartsCatalogSecurityTestBeans {
    @Bean
    JwtService jwtService() { return mock(JwtService.class); }

    @Bean
    UserRepo userRepo() { return mock(UserRepo.class); }

    @Bean
    PermissionService permissionService() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.permissionsFor(any())).thenReturn(java.util.Set.of());
        return permissions;
    }

    @Bean
    JwtFilter jwtFilter(JwtService jwtService, UserRepo userRepo, PermissionService permissionService) {
        return new JwtFilter(jwtService, userRepo, permissionService);
    }

    @Bean
    SyntheticAuth syntheticAuth(JwtService jwtService, UserRepo userRepo) {
        return new SyntheticAuth(jwtService, userRepo);
    }

    public static final class SyntheticAuth {
        private final JwtService jwt;
        private final UserRepo users;

        SyntheticAuth(JwtService jwt, UserRepo users) { this.jwt = jwt; this.users = users; }

        public void configure(String token, long id, User user) {
            org.mockito.Mockito.when(jwt.subject(token)).thenReturn(id);
            org.mockito.Mockito.when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        }
    }
}
