package com.valor.communication;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.*;
import java.util.Properties;

@org.springframework.stereotype.Component
public class RegistrationEmailSender {
 private final EmailProperties properties;
 public RegistrationEmailSender(EmailProperties properties){this.properties=properties;}
 public boolean send(String recipient,String code){
  if(!properties.enabled()||!"smtp".equalsIgnoreCase(properties.provider())||properties.host()==null||properties.host().isBlank()||properties.username()==null||properties.username().isBlank()||properties.password()==null||properties.password().isBlank()||properties.port()==null||properties.port()<1)return false;
  try { JavaMailSenderImpl sender=new JavaMailSenderImpl();sender.setHost(properties.host());sender.setPort(properties.port());sender.setUsername(properties.username());sender.setPassword(properties.password());Properties p=sender.getJavaMailProperties();p.put("mail.transport.protocol","smtp");p.put("mail.smtp.auth","true");p.put("mail.smtp.starttls.enable","true");p.put("mail.smtp.starttls.required","true");p.put("mail.smtp.connectiontimeout",String.valueOf(properties.connectionTimeoutMs()));p.put("mail.smtp.timeout",String.valueOf(properties.readTimeoutMs()));p.put("mail.smtp.writetimeout",String.valueOf(properties.writeTimeoutMs()));var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,"UTF-8");helper.setTo(recipient);helper.setFrom(new InternetAddress(properties.from(),properties.fromName()).toString());helper.setSubject("Verify your Valor email");helper.setText("Your Valor verification code is valid for five minutes. Code: "+code);sender.send(message);return true; }
  catch(MessagingException|java.io.UnsupportedEncodingException|RuntimeException ex){return false;}
 }
}
