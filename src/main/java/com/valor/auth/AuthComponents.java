package com.valor.auth;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import org.springframework.stereotype.*; import org.springframework.transaction.annotation.Transactional; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.beans.factory.annotation.Value; import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.*; import java.util.*;
interface UserRepo extends JpaRepository<User,Long>{@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.email=:email") Optional<User> lockEmail(@Param("email") String email);@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.phone=:phone") Optional<User> lockPhone(@Param("phone") String phone);Optional<User> findByEmail(String e); Optional<User> findByPhone(String p);}
interface CustomerRepo extends JpaRepository<CustomerProfile,Long>{Optional<CustomerProfile> findByUserId(Long id);}
interface TechRepo extends JpaRepository<TechnicianProfile,Long>{Optional<TechnicianProfile> findByUserId(Long id);}
interface OtpRepo extends JpaRepository<OtpVerification,Long>{@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select o from OtpVerification o where o.id=:id") Optional<OtpVerification> lockRequest(@Param("id") Long id);Optional<OtpVerification> findTopByPhoneAndVerifiedAtIsNullOrderByCreatedAtDesc(String p);}
interface TokenRepo extends JpaRepository<RefreshToken,Long>{@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select t from RefreshToken t where t.tokenHash=:hash") Optional<RefreshToken> lockHash(@Param("hash") String hash);Optional<RefreshToken> findByTokenHash(String h);}
@Service class AuthService {
 private final UserRepo users; private final CustomerRepo customers; private final OtpRepo otps; private final TokenRepo tokens; private final PasswordEncoder encoder; private final JwtService jwt;
 AuthService(UserRepo u,CustomerRepo c,OtpRepo o,TokenRepo t,PasswordEncoder e,JwtService j){users=u;customers=c;otps=o;tokens=t;encoder=e;jwt=j;}
 String normEmail(String s){return s==null?null:s.trim().toLowerCase(Locale.ROOT);} String normPhone(String s){if(s==null)return null; String p=s.trim().replaceAll("[\\s\\-()]",""); if(!p.matches("\\+[1-9]\\d{7,14}")) throw new IllegalArgumentException("Invalid phone"); return p;}
 @Transactional User register(String email,String phone,String password,String name){email=normEmail(email);phone=normPhone(phone); if(email!=null&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Invalid email"); if(password!=null&&(password.isBlank()||password.getBytes(StandardCharsets.UTF_8).length>72))throw new IllegalArgumentException("Invalid password"); if(email==null&&phone==null)throw new IllegalArgumentException("Email or phone required"); if(phone==null&&password==null)throw new IllegalArgumentException("Password required"); if(email!=null&&users.findByEmail(email).isPresent()||phone!=null&&users.findByPhone(phone).isPresent())throw new IllegalArgumentException("Registration unavailable"); User u=new User();u.setEmail(email);u.setPhone(phone);u.setPasswordHash(password==null?null:encoder.encode(password));u.setRole(Role.CUSTOMER);users.save(u);CustomerProfile p=new CustomerProfile();p.setUser(u);p.setFullName(name);customers.save(p);return u;}
 @Transactional User registerProfile(AuthDtos.Registration r) {
  User user=register(r.email(),r.phone(),r.password(),r.fullName());
  CustomerProfile profile=customers.findByUserId(user.getId()).orElseThrow();
  profile.alternatePhone=r.alternatePhone();profile.companyName=r.companyName();profile.address=r.address();return user;
 }
 @Transactional(noRollbackFor=IllegalArgumentException.class) User login(String identity,String password,Set<Role> allowed) {
  String key=normEmail(identity);
  User u=key!=null&&key.contains("@")?users.lockEmail(key).orElse(null):users.lockPhone(normPhone(identity)).orElse(null);
  if(u==null)throw new IllegalArgumentException("Authentication failed");
  var now=LocalDateTime.now();
  if(!allowed.contains(u.getRole())||!u.isActive()||u.isLocked()||u.getLockedUntil()!=null&&u.getLockedUntil().isAfter(now))throw new IllegalArgumentException("Authentication failed");
  if(u.getPasswordHash()==null||!encoder.matches(password,u.getPasswordHash())) {
   int attempts=u.getFailedLoginAttempts()+1;u.setFailedLoginAttempts(attempts);
   if(attempts>=5)u.setLockedUntil(now.plusMinutes(15));users.saveAndFlush(u);throw new IllegalArgumentException("Authentication failed");
  }
  u.setFailedLoginAttempts(0);u.setLockedUntil(null);u.setLastLoginAt(now);users.save(u);return u;
 }
 @Transactional String[] session(User u){if(!u.isActive()||u.isLocked()||(u.getLockedUntil()!=null&&u.getLockedUntil().isAfter(LocalDateTime.now())))throw new IllegalArgumentException("Authentication failed");String access=jwt.issue(u);String raw=UUID.randomUUID().toString()+UUID.randomUUID().toString();RefreshToken t=new RefreshToken();t.setUser(u);t.setTokenHash(hash(raw));t.setExpiresAt(LocalDateTime.now().plusDays(7));tokens.save(t);return new String[]{access,raw};}
 @Transactional String[] refresh(String raw){RefreshToken t=tokens.lockHash(hash(raw)).orElseThrow(()->new IllegalArgumentException("Invalid refresh token"));if(t.getRevokedAt()!=null||t.getExpiresAt().isBefore(LocalDateTime.now()))throw new IllegalArgumentException("Invalid refresh token");t.setRevokedAt(LocalDateTime.now());tokens.save(t);return session(t.getUser());}

 @Transactional AuthDtos.OtpSent sendOtpRequest(String phone) {
  phone=normPhone(phone);var now=LocalDateTime.now();
  var recent=otps.findTopByPhoneAndVerifiedAtIsNullOrderByCreatedAtDesc(phone);
  if(recent.isPresent() && (recent.get().createdAt.isAfter(now.minusSeconds(60)) || recent.get().lockedUntil!=null&&recent.get().lockedUntil.isAfter(now)))throw new IllegalArgumentException("OTP unavailable");
  String code=String.valueOf(100000+new java.security.SecureRandom().nextInt(900000));
  OtpVerification o=new OtpVerification();o.phone=phone;o.otpHash=encoder.encode(code);o.expiresAt=now.plusMinutes(5);otps.saveAndFlush(o);
  return new AuthDtos.OtpSent(o.id,o.expiresAt,true,code);
 }
 @Transactional(noRollbackFor=IllegalArgumentException.class) User verifyOtpRequest(String phone,String code,Long requestId) {
  OtpVerification o=otps.lockRequest(requestId).orElseThrow(()->new IllegalArgumentException("OTP invalid"));
  var now=LocalDateTime.now();
  if(!o.phone.equals(normPhone(phone))||o.verifiedAt!=null||!o.expiresAt.isAfter(now)||o.attemptsRemaining<=0||o.lockedUntil!=null&&o.lockedUntil.isAfter(now))throw new IllegalArgumentException("OTP invalid");
  if(!encoder.matches(code,o.otpHash)){o.attemptsRemaining--;if(o.attemptsRemaining<=0)o.lockedUntil=now.plusMinutes(15);otps.saveAndFlush(o);throw new IllegalArgumentException("OTP invalid");}
  User u=users.findByPhone(o.phone).orElseThrow(()->new IllegalArgumentException("OTP invalid"));
  if(u.getRole()!=Role.CUSTOMER||!u.isActive()||u.isLocked()||u.getLockedUntil()!=null&&u.getLockedUntil().isAfter(now))throw new IllegalArgumentException("OTP invalid");
  CustomerProfile profile=customers.findByUserId(u.getId()).orElseThrow(()->new IllegalArgumentException("OTP invalid"));
  if(!profile.active||!"ACTIVE".equals(profile.status))throw new IllegalArgumentException("OTP invalid");
  o.verifiedAt=now;o.user=u;otps.save(o);return u;
 }
 @Transactional void logoutOwned(String raw,Long userId) {
  tokens.findByTokenHash(hash(raw)).ifPresent(t->{if(!t.getUser().getId().equals(userId))throw new org.springframework.security.access.AccessDeniedException("Access denied");t.setRevokedAt(LocalDateTime.now());});
 }
 static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
@Component class JwtService {private final javax.crypto.SecretKey key; @Value("${jwt.expiration:86400000}") long exp; JwtService(@Value("${jwt.secret}") String s){if(s==null||s.length()<32)throw new IllegalStateException("JWT_SECRET must be configured");key=Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));} String issue(User u){return Jwts.builder().subject(String.valueOf(u.getId())).claim("role",u.getRole().name()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+exp)).signWith(key).compact();} Long subject(String token){return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());}}
