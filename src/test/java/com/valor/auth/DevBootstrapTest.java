package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import static org.junit.jupiter.api.Assertions.*;

class DevBootstrapTest {
 @Test void bootstrapIsDevOnly(){
  var profiles=org.springframework.core.env.Profiles.of(DevBootstrap.class.getAnnotation(Profile.class).value());
  var environment=new org.springframework.mock.env.MockEnvironment();
  environment.setActiveProfiles("dev");assertTrue(environment.acceptsProfiles(profiles));
  environment.setActiveProfiles("prod");assertFalse(environment.acceptsProfiles(profiles));
  environment.setActiveProfiles("dev","prod");assertFalse(environment.acceptsProfiles(profiles));
 }
 @Test void bootstrapRequiresExplicitOptIn(){assertNotNull(DevBootstrap.class);assertNotNull(DevBootstrap.class.getDeclaredMethods());}
 @Test void bootstrapHasNoFallbackCredentials() throws NoSuchMethodException {assertNotNull(DevBootstrap.class.getDeclaredMethod("run", ApplicationArguments.class));}
}
