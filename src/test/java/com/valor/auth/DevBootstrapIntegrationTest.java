package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties={
 "spring.datasource.url=jdbc:h2:mem:bootstrap_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
 "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.datasource.username=sa",
 "spring.datasource.password=",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
 "spring.jpa.hibernate.ddl-auto=validate",
 "spring.flyway.enabled=true",
 "dev.bootstrap.enabled=true",
 "dev.bootstrap.super-admin-email=stage1.bootstrap@example.test",
 "dev.bootstrap.super-admin-password=Stage1OnlyPassword!"
})
class DevBootstrapIntegrationTest {
 @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired DevBootstrap bootstrap;
 @Test void createsOneHashedSuperAdminAndIsIdempotent() throws Exception {
   int before=db.queryForObject("select count(*) from users where email=? and role='SUPER_ADMIN'",Integer.class,"stage1.bootstrap@example.test");
   assertEquals(1,before);
   String first=db.queryForObject("select password_hash from users where email=?",String.class,"stage1.bootstrap@example.test");
   assertNotEquals("Stage1OnlyPassword!",first); assertTrue(encoder.matches("Stage1OnlyPassword!",first));
   bootstrap.run(null);
   assertEquals(1,db.queryForObject("select count(*) from users where email=? and role='SUPER_ADMIN'",Integer.class,"stage1.bootstrap@example.test"));
   assertEquals(first,db.queryForObject("select password_hash from users where email=?",String.class,"stage1.bootstrap@example.test"));
 }
}
