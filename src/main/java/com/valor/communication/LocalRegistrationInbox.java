package com.valor.communication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
@Component
class LocalRegistrationInbox {
 private final Environment environment;
 private final ObjectMapper mapper;
 private final boolean optedIn;
 private final Path directory;
 LocalRegistrationInbox(Environment environment, ObjectMapper mapper,
  @Value("${registration.local-inbox.enabled:false}") boolean optedIn,
  @Value("${registration.local-inbox.directory:.local-mailbox}") String directory) {
  this.environment=environment; this.mapper=mapper; this.optedIn=optedIn;
  this.directory=Path.of(directory).toAbsolutePath().normalize();
 }
 boolean enabled(){return optedIn && environment.acceptsProfiles(Profiles.of("dev & !prod"));}
 boolean deliver(String recipient,String code){
  if(!enabled()) return false;
  Path temporary=null;
  try {
   Files.createDirectories(directory);
   temporary=Files.createTempFile(directory,"message-",".tmp");
   mapper.writeValue(temporary.toFile(),Map.of("to",recipient,"subject","Verify your Valor email",
    "body","Your Valor verification code is "+code+". It expires in five minutes.","sentAt",Instant.now().toString()));
   Files.move(temporary,directory.resolve(UUID.randomUUID()+".json"),StandardCopyOption.ATOMIC_MOVE);
   return true;
  } catch(IOException ex) {
   if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}
   return false;
  }
 }
}
