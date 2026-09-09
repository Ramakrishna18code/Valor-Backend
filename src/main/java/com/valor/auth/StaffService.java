package com.valor.auth;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class StaffService {
    private final UserRepo users; private final TechRepo technicians; private final AuthService auth;
    private final AssetIdentityAccess identities; private final PasswordEncoder encoder; private final EntityManager em;
    StaffService(UserRepo users, TechRepo technicians, AuthService auth, AssetIdentityAccess identities,
                 PasswordEncoder encoder, EntityManager em) {
        this.users=users; this.technicians=technicians; this.auth=auth; this.identities=identities; this.encoder=encoder; this.em=em;
    }
    private User actor() {
        User actor=identities.actor();
        if(actor.getRole()!=Role.SUPER_ADMIN) throw new AccessDeniedException("Access denied");
        return actor;
    }
    StaffController.View create(StaffController.Create input) {
        actor();
        if(input.role()!=Role.ADMIN && input.role()!=Role.TECHNICIAN) throw new IllegalArgumentException("Invalid staff role");
        String email=auth.normEmail(input.email());
        if(email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || input.password()==null
                || input.password().isBlank() || input.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Invalid staff identity");
        if(users.findByEmail(email).isPresent()) throw new IllegalArgumentException("Identity already exists");
        String availability=input.availabilityStatus()==null ? "AVAILABLE" : input.availabilityStatus();
        if(!Set.of("AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE").contains(availability)) throw new IllegalArgumentException("Invalid availability");
        if(input.role()==Role.ADMIN && (input.employeeId()!=null || input.assignedArea()!=null || input.specialization()!=null || input.availabilityStatus()!=null))
            throw new IllegalArgumentException("Admin has no technician profile");
        User user=new User(); user.setEmail(email); user.setPasswordHash(encoder.encode(input.password())); user.setRole(input.role());
        users.saveAndFlush(user);
        TechnicianProfile profile=null;
        if(input.role()==Role.TECHNICIAN) {
            profile=new TechnicianProfile(); profile.user=user; profile.employeeId=optional(input.employeeId());
            profile.assignedArea=optional(input.assignedArea()); profile.specialization=optional(input.specialization()); profile.availabilityStatus=availability;
            technicians.saveAndFlush(profile);
        }
        return view(user,profile);
    }
    StaffController.View deactivate(Long id) {
        User actor=actor();
        if(actor.getId().equals(id)) throw new IllegalArgumentException("Self deactivation is forbidden");
        User user=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(user==null) throw new StaffNotFoundException();
        if(user.getRole()!=Role.ADMIN && user.getRole()!=Role.TECHNICIAN) throw new IllegalArgumentException("Only staff may be deactivated");
        TechnicianProfile profile=technicians.findByUserId(id).orElse(null);
        user.setActive(false);
        if(user.getRole()==Role.TECHNICIAN) {
            if(profile==null) throw new IllegalStateException("Staff profile unavailable");
            profile.active=false;
        }
        em.flush();
        return view(user,profile);
    }
    private String optional(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    private StaffController.View view(User user, TechnicianProfile profile) {
        return new StaffController.View(user.getId(),user.getEmail(),user.getRole(),user.isActive(),profile==null?null:profile.id,
            profile==null?null:profile.employeeId,profile==null?null:profile.assignedArea,profile==null?null:profile.specialization,profile==null?null:profile.availabilityStatus);
    }
}
class StaffNotFoundException extends RuntimeException {}
