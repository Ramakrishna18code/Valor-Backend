package com.valor.auth;

import com.valor.assets.AmcContract;
import com.valor.assets.AmcStatus;
import com.valor.assets.Building;
import com.valor.assets.Lift;
import com.valor.assets.LiftStatus;
import com.valor.workflow.AssignmentStatus;
import com.valor.workflow.RequestPriority;
import com.valor.workflow.RequestStatus;
import com.valor.workflow.ServiceRequest;
import com.valor.workflow.TechnicianAssignment;
import com.valor.workflow.WorkflowServiceType;
import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
@Profile("dev")
class DevBootstrap implements ApplicationRunner {
  private final UserRepo users; private final PasswordEncoder encoder;
  private final EntityManager em;
  private final boolean enabled; private final String configuredEmail; private final String configuredPassword;
  private final boolean sampleDataEnabled; private final String samplePassword;
  DevBootstrap(UserRepo users, PasswordEncoder encoder, EntityManager em,
      @Value("${dev.bootstrap.enabled:false}") boolean enabled,
      @Value("${dev.bootstrap.super-admin-email:}") String configuredEmail,
      @Value("${dev.bootstrap.super-admin-password:}") String configuredPassword,
      @Value("${dev.seed.sample-data-enabled:false}") boolean sampleDataEnabled,
      @Value("${dev.seed.sample-password:}") String samplePassword){this.users=users;this.encoder=encoder;this.em=em;this.enabled=enabled;this.configuredEmail=configuredEmail;this.configuredPassword=configuredPassword;this.sampleDataEnabled=sampleDataEnabled;this.samplePassword=samplePassword;}
  @Transactional
  public void run(ApplicationArguments args){
    if (!enabled) return;
    String email=configuredEmail; String password=configuredPassword;
    if(email==null||email.isBlank()||password==null||password.isBlank()) throw new IllegalStateException("Development bootstrap requires configured environment credentials");
    email=email.trim().toLowerCase(Locale.ROOT); final String normalizedEmail=email; final String configuredPassword=password;
    User u=users.findByEmail(normalizedEmail).orElseGet(()->{User n=new User();n.setEmail(normalizedEmail);n.setRole(Role.SUPER_ADMIN);n.setPasswordHash(encoder.encode(configuredPassword));return users.save(n);});
    if(u.getPasswordHash()==null){u.setPasswordHash(encoder.encode(configuredPassword));users.save(u);}
    if (sampleDataEnabled) seedSampleData(u);
  }

  private void seedSampleData(User admin) {
    String password=samplePassword==null||samplePassword.isBlank()?configuredPassword:samplePassword;
    if(password==null||password.isBlank()) throw new IllegalStateException("Development sample seed requires a password");
    TechnicianProfile[] technicians = new TechnicianProfile[] {
        technician("tech.arjun@valor.local","VAL-T001","North Bengaluru","Traction lifts","AVAILABLE",password),
        technician("tech.meera@valor.local","VAL-T002","South Bengaluru","Hydraulic lifts","AVAILABLE",password),
        technician("tech.kabir@valor.local","VAL-T003","East Bengaluru","Door systems","BUSY",password),
        technician("tech.nisha@valor.local","VAL-T004","West Bengaluru","Controller diagnostics","AVAILABLE",password),
        technician("tech.rohan@valor.local","VAL-T005","Central Bengaluru","Emergency response","OFF_DUTY",password)
    };
    CustomerProfile[] customers = new CustomerProfile[] {
        customer("ananya.rao@example.com","+919810000001","Ananya Rao","Rao Residency","Indiranagar, Bengaluru",password),
        customer("vikram.menon@example.com","+919810000002","Vikram Menon","Menon Heights","Koramangala, Bengaluru",password),
        customer("priya.shah@example.com","+919810000003","Priya Shah","Shah Arcade","Whitefield, Bengaluru",password),
        customer("farhan.khan@example.com","+919810000004","Farhan Khan","Khan Towers","Jayanagar, Bengaluru",password),
        customer("neha.iyer@example.com","+919810000005","Neha Iyer","Iyer Enclave","Malleshwaram, Bengaluru",password)
    };
    for (int i=0;i<customers.length;i++) seedCustomerAssetsAndRequest(admin, customers[i], technicians[i], i+1);
  }

  private CustomerProfile customer(String email,String phone,String name,String company,String address,String password) {
    String normalized=email.trim().toLowerCase(Locale.ROOT);
    return users.findByEmail(normalized).map(user -> em.createQuery("select c from CustomerProfile c where c.user.id=:id", CustomerProfile.class)
        .setParameter("id", user.getId()).getSingleResult()).orElseGet(() -> {
          User user=new User();user.setEmail(normalized);user.setPhone(phone);user.setPasswordHash(encoder.encode(password));user.setRole(Role.CUSTOMER);em.persist(user);
          CustomerProfile profile=new CustomerProfile();profile.user=user;profile.fullName=name;profile.companyName=company;profile.address=address;profile.status="ACTIVE";profile.active=true;em.persist(profile);
          return profile;
        });
  }

  private TechnicianProfile technician(String email,String employeeId,String area,String specialization,String availability,String password) {
    String normalized=email.trim().toLowerCase(Locale.ROOT);
    return users.findByEmail(normalized).map(user -> em.createQuery("select t from TechnicianProfile t where t.user.id=:id", TechnicianProfile.class)
        .setParameter("id", user.getId()).getSingleResult()).orElseGet(() -> {
          User user=new User();user.setEmail(normalized);user.setPasswordHash(encoder.encode(password));user.setRole(Role.TECHNICIAN);em.persist(user);
          TechnicianProfile profile=new TechnicianProfile();profile.user=user;profile.employeeId=employeeId;profile.assignedArea=area;profile.specialization=specialization;profile.availabilityStatus=availability;profile.active=true;em.persist(profile);
          return profile;
        });
  }

  private void seedCustomerAssetsAndRequest(User admin, CustomerProfile customer, TechnicianProfile technician, int index) {
    String serviceId="DEV-SR-2026-%03d".formatted(index);
    if (exists("select count(r) from ServiceRequest r where r.serviceId=:value", serviceId)) return;
    Building building=new Building();building.setCustomer(customer);building.setBuildingName(customer.companyName);building.setBuildingType(index%2==0?"Commercial":"Residential");
    building.setAddress(customer.address);building.setCity("Bengaluru");building.setState("Karnataka");building.setPincode("5600%02d".formatted(index));
    building.setEmergencyContactName(customer.fullName);building.setEmergencyContactPhone(customer.getUser().getPhone());em.persist(building);
    Lift lift=new Lift();lift.setBuilding(building);lift.setName("Tower %d Main Lift".formatted(index));lift.setLiftNumber("L-%03d".formatted(index));
    lift.setManufacturer(index%2==0?"Otis":"KONE");lift.setModel("VX-%d00".formatted(index));lift.setCapacity(8+index);lift.setFloorCount(6+index);
    lift.setSerialNumber("VAL-LIFT-%03d".formatted(index));lift.setInstallationDate(LocalDate.now().minusYears(2).minusMonths(index));
    lift.setLocation("Lobby %d".formatted(index));lift.setCurrentStatus(index==3? LiftStatus.MAINTENANCE:LiftStatus.ACTIVE);lift.setWarrantyStatus("ACTIVE");
    lift.setWarrantyStartDate(LocalDate.now().minusYears(2));lift.setWarrantyEndDate(LocalDate.now().plusYears(1));lift.setLastMaintenanceDate(LocalDate.now().minusDays(30+index));
    lift.setNextMaintenanceDate(LocalDate.now().plusDays(20+index));lift.setHealthScore((byte)(88-index));lift.setMachineRoom("Machine room %d".formatted(index));em.persist(lift);
    AmcContract amc=new AmcContract();amc.setLift(lift);amc.setAmcNumber("AMC-DEV-2026-%03d".formatted(index));amc.setPlan(index%2==0?"Comprehensive":"Standard");
    amc.setCoverageDetails("Local development sample AMC coverage.");amc.setStartDate(LocalDate.now().minusMonths(6));amc.setEndDate(LocalDate.now().plusMonths(6+index));
    amc.setStatus(AmcStatus.ACTIVE);amc.setRenewalDate(amc.getEndDate().minusDays(30));amc.setRenewalCount(0);em.persist(amc);
    ServiceRequest request=new ServiceRequest();request.setCustomer(customer);request.setLift(lift);request.setServiceId(serviceId);request.setTitle(sampleTitle(index));
    request.setDescription("Development seed service request for UI validation.");request.setIssueCategory(index%2==0?"Preventive maintenance":"Door operation");
    request.setPriority(index==3? RequestPriority.HIGH:RequestPriority.MEDIUM);request.setStatus(sampleStatus(index));request.setServiceType(index%2==0? WorkflowServiceType.ROUTINE_MAINTENANCE:WorkflowServiceType.BREAKDOWN);
    request.setCustomerRemarks("Seeded customer note for local testing.");request.setServiceRequestedAt(LocalDateTime.now().minusDays(5L-index));
    request.setPreferredVisitDate(LocalDate.now().plusDays(index));request.setPreferredTimeSlot("10:00 AM - 12:00 PM");request.setEstimatedCompletionMinutes(90);em.persist(request);
    if (request.getStatus()!=RequestStatus.PENDING) {
      TechnicianAssignment assignment=new TechnicianAssignment();assignment.setRequest(request);assignment.setTechnician(technician);assignment.setAssignedBy(admin);
      assignment.setStatus(request.getStatus()==RequestStatus.COMPLETED?AssignmentStatus.COMPLETED:
          request.getStatus()==RequestStatus.ACCEPTED||request.getStatus()==RequestStatus.ON_THE_WAY?AssignmentStatus.ACCEPTED:AssignmentStatus.ASSIGNED);
      assignment.setAssignedAt(LocalDateTime.now().minusDays(2));if(assignment.getStatus()==AssignmentStatus.ACCEPTED) assignment.setAcceptedAt(LocalDateTime.now().minusDays(1));
      assignment.setNotes("Development seed assignment.");em.persist(assignment);
    }
  }

  private boolean exists(String query, String value) {
    return em.createQuery(query, Long.class).setParameter("value", value).getSingleResult() > 0;
  }

  private String sampleTitle(int index) {
    return switch(index) {
      case 1 -> "Door sensor intermittently failing";
      case 2 -> "Quarterly preventive maintenance";
      case 3 -> "Lift stops unevenly at third floor";
      case 4 -> "Cabin fan noise inspection";
      default -> "Routine safety inspection";
    };
  }

  private RequestStatus sampleStatus(int index) {
    return switch(index) {
      case 1 -> RequestStatus.PENDING;
      case 2 -> RequestStatus.ASSIGNED;
      case 3 -> RequestStatus.ACCEPTED;
      case 4 -> RequestStatus.ON_THE_WAY;
      default -> RequestStatus.COMPLETED;
    };
  }
}
