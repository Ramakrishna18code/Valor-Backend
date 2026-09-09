package com.valor.auth;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="users") public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(length=254,unique=true) private String email; @Column(length=20,unique=true) private String phone;
 @Column(name="password_hash",length=255) private String passwordHash; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
 @Column(name="is_active",nullable=false) private boolean active=true; @Column(name="is_locked",nullable=false) private boolean locked=false;
 @Column(name="failed_login_attempts",nullable=false) private int failedLoginAttempts; @Column(name="locked_until") private LocalDateTime lockedUntil; @Column(name="last_login_at") private LocalDateTime lastLoginAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt; @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt; @Column(name="deleted_at") private LocalDateTime deletedAt;
 @PrePersist void pre(){createdAt=LocalDateTime.now();updatedAt=createdAt;} @PreUpdate void upd(){updatedAt=LocalDateTime.now();}
 public Long getId(){return id;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public Role getRole(){return role;} public void setRole(Role v){role=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public boolean isLocked(){return locked;} public void setLocked(boolean v){locked=v;} public int getFailedLoginAttempts(){return failedLoginAttempts;} public void setFailedLoginAttempts(int v){failedLoginAttempts=v;} public LocalDateTime getLockedUntil(){return lockedUntil;} public void setLockedUntil(LocalDateTime v){lockedUntil=v;} public LocalDateTime getLastLoginAt(){return lastLoginAt;} public void setLastLoginAt(LocalDateTime v){lastLoginAt=v;}
}
