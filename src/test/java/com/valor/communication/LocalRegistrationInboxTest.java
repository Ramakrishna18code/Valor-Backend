package com.valor.communication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalRegistrationInboxTest {
 @TempDir Path folder;
 @Test void explicitlyEnabledDevInboxStoresEmail() throws Exception {
  var env=new MockEnvironment();env.setActiveProfiles("dev");
  assertTrue(new LocalRegistrationInbox(env,new ObjectMapper(),true,folder.toString()).deliver("test@example.test","123456"));
  try(var files=Files.list(folder)){var body=Files.readString(files.findFirst().orElseThrow());assertTrue(body.contains("123456"));assertTrue(body.contains("test@example.test"));}
 }
 @Test void disabledInboxWritesNothing() throws Exception {
  var env=new MockEnvironment();env.setActiveProfiles("dev");
  assertFalse(new LocalRegistrationInbox(env,new ObjectMapper(),false,folder.toString()).deliver("test@example.test","123456"));
  try(var files=Files.list(folder)){assertEquals(0,files.count());}
 }
 @Test void productionAndDefaultProfilesCannotCaptureCodes() throws Exception {
  for(String[] profiles:new String[][]{{"prod"},{"dev","prod"},{}}){
   var env=new MockEnvironment();env.setActiveProfiles(profiles);
   assertFalse(new LocalRegistrationInbox(env,new ObjectMapper(),true,folder.toString()).deliver("test@example.test","123456"));
  }
  try(var files=Files.list(folder)){assertEquals(0,files.count());}
 }
}
