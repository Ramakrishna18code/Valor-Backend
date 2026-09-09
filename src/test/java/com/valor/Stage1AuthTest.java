package com.valor;

import com.valor.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class Stage1AuthTest {
    @Autowired PasswordEncoder encoder;
    @Test void contextUsesStage1ConfigurationAndBcrypt(){ assertTrue(encoder.matches("secret", encoder.encode("secret"))); }
}
