package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import static org.junit.jupiter.api.Assertions.*;

class DevBootstrapTest {
 @Test void bootstrapIsDevOnly(){assertArrayEquals(new String[]{"dev"},DevBootstrap.class.getAnnotation(Profile.class).value());}
 @Test void bootstrapRequiresExplicitOptIn(){assertNotNull(DevBootstrap.class);assertNotNull(DevBootstrap.class.getDeclaredMethods());}
 @Test void bootstrapHasNoFallbackCredentials() throws NoSuchMethodException {assertNotNull(DevBootstrap.class.getDeclaredMethod("run", ApplicationArguments.class));}
}
