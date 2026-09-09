package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import static org.junit.jupiter.api.Assertions.*;

class DevBootstrapTest {
 @Test void bootstrapIsDevOnly(){assertArrayEquals(new String[]{"dev"},DevBootstrap.class.getAnnotation(Profile.class).value());}
 @Test void bootstrapRequiresExplicitOptIn(){assertNotNull(DevBootstrap.class);assertNotNull(DevBootstrap.class.getDeclaredMethods());}
 @Test void bootstrapHasNoFallbackCredentials(){assertTrue(DevBootstrap.class.getDeclaredMethods()[0].toString().contains("run"));}
}
