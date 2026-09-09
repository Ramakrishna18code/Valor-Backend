package com.valor.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@Profile("dev")
class DevBootstrap implements ApplicationRunner {
  private final UserRepo users; private final PasswordEncoder encoder;
  private final boolean enabled; private final String configuredEmail; private final String configuredPassword;
  DevBootstrap(UserRepo users, PasswordEncoder encoder,
      @Value("${dev.bootstrap.enabled:false}") boolean enabled,
      @Value("${dev.bootstrap.super-admin-email:}") String configuredEmail,
      @Value("${dev.bootstrap.super-admin-password:}") String configuredPassword){this.users=users;this.encoder=encoder;this.enabled=enabled;this.configuredEmail=configuredEmail;this.configuredPassword=configuredPassword;}
  public void run(ApplicationArguments args){
    if (!enabled) return;
    String email=configuredEmail; String password=configuredPassword;
    if(email==null||email.isBlank()||password==null||password.isBlank()) throw new IllegalStateException("Development bootstrap requires configured environment credentials");
    email=email.trim().toLowerCase(java.util.Locale.ROOT); final String normalizedEmail=email; final String configuredPassword=password;
    User u=users.findByEmail(normalizedEmail).orElseGet(()->{User n=new User();n.setEmail(normalizedEmail);n.setRole(Role.SUPER_ADMIN);n.setPasswordHash(encoder.encode(configuredPassword));return users.save(n);});
    if(u.getPasswordHash()==null){u.setPasswordHash(encoder.encode(configuredPassword));users.save(u);}
  }
}
