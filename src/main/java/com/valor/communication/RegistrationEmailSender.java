package com.valor.communication;

@org.springframework.stereotype.Component
public class RegistrationEmailSender {
 private final EmailProperties properties;
 private final LocalRegistrationInbox localInbox;
 private final EmailProvider emailProvider;
 public RegistrationEmailSender(EmailProperties properties, LocalRegistrationInbox localInbox, EmailProvider emailProvider){this.properties=properties;this.localInbox=localInbox;this.emailProvider=emailProvider;}
 public boolean send(String recipient,String code){
  if(localInbox.enabled()) return localInbox.deliver(recipient,code);
  if(!properties.enabled()) return false;
  String text="Your Valor verification code is valid for five minutes. Code: "+code;
  ProviderResult result=emailProvider.send(new ProviderRequest(recipient,properties.from(),properties.fromName(),
   properties.replyTo(),"Verify your Valor email",verificationHtml(code),text,"REGISTRATION_"+recipient,
   "CUSTOMER_EMAIL_VERIFICATION","email-registration","email-registration:"+recipient));
  return result.success();
 }
 private static String verificationHtml(String code){
  String safe=code==null?"":code.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
  return "<!doctype html><html><body style=\"margin:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 16px;background:#f4f7fb;\"><tr><td align=\"center\"><table role=\"presentation\" width=\"100%\" style=\"max-width:600px;background:#fff;border:1px solid #e4eaf2;border-radius:16px;overflow:hidden;\"><tr><td style=\"padding:24px 28px;background:#102a43;color:#fff;font-size:22px;font-weight:700;\">Valor</td></tr><tr><td style=\"padding:32px 28px;font-size:15px;line-height:1.7;\"><h1 style=\"margin:0 0 12px;font-size:24px;line-height:1.25;\">Verify your email</h1><p style=\"margin:0 0 24px;\">Your verification code is valid for five minutes.</p><div style=\"display:inline-block;padding:14px 22px;background:#e8f1fb;border-radius:10px;color:#102a43;font-size:28px;font-weight:700;letter-spacing:6px;\">"+safe+"</div></td></tr><tr><td style=\"padding:18px 28px;border-top:1px solid #e4eaf2;color:#667085;font-size:12px;\">If you did not request this code, you can safely ignore this email.</td></tr></table></td></tr></table></body></html>";
 }
}
