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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("dev")
class DevBootstrap implements ApplicationRunner {
  private final UserRepo users; private final PasswordEncoder encoder;
  private final EntityManager em;
  private final boolean enabled; private final String configuredEmail; private final String configuredPassword;
  private final boolean sampleDataEnabled; private final String samplePassword;
    private final boolean pruneUsers;
  DevBootstrap(UserRepo users, PasswordEncoder encoder, EntityManager em,
      @Value("${dev.bootstrap.enabled:false}") boolean enabled,
      @Value("${dev.bootstrap.super-admin-email:}") String configuredEmail,
      @Value("${dev.bootstrap.super-admin-password:}") String configuredPassword,
      @Value("${dev.seed.sample-data-enabled:false}") boolean sampleDataEnabled,
      @Value("${dev.seed.sample-password:}") String samplePassword,
      @Value("${dev.bootstrap.prune-users:false}") boolean pruneUsers){this.users=users;this.encoder=encoder;this.em=em;this.enabled=enabled;this.configuredEmail=configuredEmail;this.configuredPassword=configuredPassword;this.sampleDataEnabled=sampleDataEnabled;this.samplePassword=samplePassword;this.pruneUsers=pruneUsers;}
  @Transactional
  public void run(ApplicationArguments args){
    if (!enabled) return;
    String email=configuredEmail; String password=configuredPassword;
    if(email==null||email.isBlank()||password==null||password.isBlank()) throw new IllegalStateException("Development bootstrap requires configured environment credentials");
    email=email.trim().toLowerCase(Locale.ROOT); final String normalizedEmail=email; final String configuredPassword=password;
    if (pruneUsers) pruneUnexpectedDevData(normalizedEmail);
    User u=users.findByEmail(normalizedEmail).orElseGet(()->{User n=new User();n.setEmail(normalizedEmail);n.setRole(Role.SUPER_ADMIN);n.setPasswordHash(encoder.encode(configuredPassword));return users.save(n);});
    if(u.getPasswordHash()==null){u.setPasswordHash(encoder.encode(configuredPassword));users.save(u);}
    ensureSuperAdmin("rk4053727@gmail.com", configuredPassword);
    if (sampleDataEnabled) seedSampleData(u);
  }

  private void ensureSuperAdmin(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    User user = users.findByEmail(normalized).orElseGet(() -> {
      User created = new User(); created.setEmail(normalized); created.setRole(Role.SUPER_ADMIN); created.setPasswordHash(encoder.encode(password));
      return users.save(created);
    });
    if (user.getPasswordHash() == null) { user.setPasswordHash(encoder.encode(password)); users.save(user); }
  }

  private void pruneUnexpectedDevData(String configuredAdminEmail) {
    Set<String> allowedEmails = new HashSet<>();
    allowedEmails.add(configuredAdminEmail);
    allowedEmails.add("rk4053727@gmail.com");
    allowedEmails.add("mgopichakradhar@gmail.com");
    allowedEmails.addAll(Set.of(
        "tech.arjun@valor.local", "tech.meera@valor.local", "tech.kabir@valor.local", "tech.nisha@valor.local", "tech.rohan@valor.local",
        "ananya.rao@example.com", "vikram.menon@example.com", "priya.shah@example.com", "farhan.khan@example.com", "neha.iyer@example.com"));

    em.createNativeQuery("SET FOREIGN_KEY_CHECKS=0").executeUpdate();
    try {
      @SuppressWarnings("unchecked")
      var tables = em.createNativeQuery("select table_name from information_schema.tables where table_schema=database()").getResultList();
      Set<String> preservedTables = Set.of("flyway_schema_history", "users", "customer_profiles", "technician_profiles", "roles", "permissions", "role_permissions");
      for (Object table : tables) {
        String tableName = table.toString();
        if (!preservedTables.contains(tableName)) em.createNativeQuery("delete from `" + tableName + "`").executeUpdate();
      }
        em.createNativeQuery("delete from customer_profiles where user_id in (select id from users where email is null or lower(email) not in (:emails))")
          .setParameter("emails", allowedEmails).executeUpdate();
        em.createNativeQuery("delete from technician_profiles where user_id in (select id from users where email is null or lower(email) not in (:emails))")
          .setParameter("emails", allowedEmails).executeUpdate();
        em.createNativeQuery("delete from users where email is null or lower(email) not in (:emails)").setParameter("emails", allowedEmails).executeUpdate();
    } finally {
      em.createNativeQuery("SET FOREIGN_KEY_CHECKS=1").executeUpdate();
    }
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
    seedAnanyaCustomerDemo(admin, customers[0], technicians, password);
    for (int i=0;i<customers.length;i++) seedCustomerAssetsAndRequest(admin, customers[i], technicians[i], i+1);
    seedGopiDemoCustomer(admin, technicians, password);
    seedParts(technicians, customers);
  }

  private void seedParts(TechnicianProfile[] technicians, CustomerProfile[] customers) {
    String[][] categories = {
        {"Door and Safety", "Door locks, rollers, sensors, buffers and safety components."},
        {"Electrical and Control", "Controllers, relays, fuses, contactors and wiring."},
        {"Mechanical and Traction", "Ropes, bearings, sheaves, guides and fasteners."},
        {"Hydraulic Systems", "Hydraulic oil, seals, valves and power-unit parts."},
        {"Installation Consumables", "Site consumables used during lift service and installation."}
    };
    for (String[] category : categories) em.createNativeQuery("insert into part_categories(name,description,is_active,created_at,updated_at) values (?,?,true,now(6),now(6)) on duplicate key update description=values(description),is_active=true")
        .setParameter(1, category[0]).setParameter(2, category[1]).executeUpdate();

    String[][] parts = {
        {"Door and Safety", "Door interlock switch", "SAFE-DIS-001", "piece", "8", "2", "Lift door interlock replacement"},
        {"Door and Safety", "Door roller set", "SAFE-DRS-002", "set", "5", "2", "Compatible with common center-opening doors"},
        {"Door and Safety", "Light curtain sensor", "SAFE-LCS-003", "piece", "3", "1", "Infrared door protection sensor"},
        {"Door and Safety", "Door clutch assembly", "SAFE-DCA-004", "piece", "2", "1", "Automatic door operator clutch"},
        {"Door and Safety", "Safety brake switch", "SAFE-SBS-005", "piece", "6", "2", "Safety circuit brake contact"},
        {"Electrical and Control", "Lift controller relay", "ELEC-LCR-001", "piece", "12", "4", "Control panel replacement relay"},
        {"Electrical and Control", "Contactor 32A", "ELEC-CON-002", "piece", "10", "3", "Motor and brake contactor"},
        {"Electrical and Control", "Control fuse 2A", "ELEC-FUS-003", "box", "18", "6", "Control circuit fuse pack"},
        {"Electrical and Control", "Encoder coupling", "ELEC-ENC-004", "piece", "4", "1", "Encoder coupling for traction machines"},
        {"Electrical and Control", "Emergency battery 12V", "ELEC-BAT-005", "piece", "2", "1", "Emergency lowering battery"},
        {"Electrical and Control", "Landing push button", "ELEC-LPB-006", "piece", "7", "2", "Universal landing call button"},
        {"Mechanical and Traction", "Traction rope 10mm", "MECH-ROP-001", "meter", "120", "40", "Lift wire rope for traction systems"},
        {"Mechanical and Traction", "Guide shoe liner", "MECH-GSL-002", "pair", "8", "2", "Sliding guide shoe liner"},
        {"Mechanical and Traction", "Pulley bearing", "MECH-PLB-003", "piece", "4", "1", "Traction pulley bearing"},
        {"Mechanical and Traction", "Machine oil ISO  VG 220", "MECH-OIL-004", "liter", "24", "8", "Gearbox and machine lubrication oil"},
        {"Mechanical and Traction", "Anti-vibration pad", "MECH-AVP-005", "set", "6", "2", "Machine base vibration isolation"},
        {"Hydraulic Systems", "Hydraulic oil 46", "HYD-OIL-001", "liter", "40", "12", "Hydraulic lift power unit oil"},
        {"Hydraulic Systems", "Hydraulic cylinder seal kit", "HYD-SEA-002", "kit", "2", "1", "Cylinder seal replacement kit"},
        {"Hydraulic Systems", "Hydraulic hose 1/2 inch", "HYD-HOS-003", "meter", "18", "6", "High-pressure hydraulic hose"},
        {"Hydraulic Systems", "Lowering valve coil", "HYD-VLV-004", "piece", "3", "1", "Emergency lowering valve coil"},
        {"Installation Consumables", "Cable tie heavy duty", "CONS-CTH-001", "pack", "25", "8", "Electrical and shaft cable management"},
        {"Installation Consumables", "M10 anchor bolt", "CONS-ANC-002", "box", "30", "10", "Guide rail and bracket anchoring"},
        {"Installation Consumables", "Electrical insulation tape", "CONS-TAP-003", "roll", "40", "12", "Electrical maintenance consumable"},
        {"Installation Consumables", "Thread locker medium", "CONS-TLK-004", "bottle", "6", "2", "Mechanical fastener locking compound"},
        {"Installation Consumables", "Cleaning and contact spray", "CONS-SPR-005", "can", "4", "1", "Panel and contact maintenance spray"}
    };
    for (String[] part : parts) {
      em.createNativeQuery("insert into part_items(category_id,name,sku,description,unit,quantity_on_hand,reorder_threshold,is_active,compatibility_metadata,version,created_at,updated_at) select id,?,?,?,?,?, ?,true,?,0,now(6),now(6) from part_categories where name=? on duplicate key update description=values(description),reorder_threshold=values(reorder_threshold),is_active=true")
          .setParameter(1, part[1]).setParameter(2, part[2]).setParameter(3, part[6]).setParameter(4, part[3]).setParameter(5, Integer.parseInt(part[4])).setParameter(6, Integer.parseInt(part[5])).setParameter(7, part[6]).setParameter(8, part[0]).executeUpdate();
        em.createNativeQuery("update part_items p join part_categories c on c.name=? set p.category_id=c.id where p.sku=?").setParameter(1, part[0]).setParameter(2, part[2]).executeUpdate();
      Number itemId = (Number) em.createNativeQuery("select id from part_items where sku=?").setParameter(1, part[2]).getSingleResult();
      if (((Number) em.createNativeQuery("select count(*) from part_stock_movements where part_item_id=? and reason=?").setParameter(1, itemId.longValue()).setParameter(2, "DEV-SEED-" + part[2]).getSingleResult()).longValue() == 0) {
        em.createNativeQuery("insert into part_stock_movements(part_item_id,movement_type,quantity,reason,actor_user_id,created_at) values (?, 'RESTOCK', ?, ?, ?, now(6))")
            .setParameter(1, itemId.longValue()).setParameter(2, Integer.parseInt(part[4])).setParameter(3, "DEV-SEED-" + part[2]).setParameter(4, users.findByEmail("rk4053727@gmail.com").map(User::getId).orElse(null)).executeUpdate();
      }
    }
    seedPartRequest("ANANYA-SR-2026-001", technicians[0], customers[0], "DEV-SEED-ANANYA-SUBMITTED", "SUBMITTED", new String[][]{{"SAFE-DIS-001", "1"}, {"ELEC-LCR-001", "2"}});
    seedPartRequest("ANANYA-SR-2026-002", technicians[0], customers[0], "DEV-SEED-ANANYA-REJECTED", "REJECTED", new String[][]{{"MECH-ROP-001", "12"}});
  }

  private void seedPartRequest(String serviceId, TechnicianProfile technician, CustomerProfile customer, String seedNote, String status, String[][] requestedParts) {
    Number serviceRequestId = (Number) em.createNativeQuery("select id from service_requests where service_id=? limit 1").setParameter(1, serviceId).getResultStream().findFirst().orElse(null);
    if (serviceRequestId == null) serviceRequestId = (Number) em.createNativeQuery("select r.id from service_requests r join customer_profiles c on c.id=r.customer_id where c.id=? order by r.id limit 1").setParameter(1, customer.getId()).getResultStream().findFirst().orElse(null);
    if (serviceRequestId == null) return;
    if (((Number) em.createNativeQuery("select count(*) from service_request_part_requests where notes=?").setParameter(1, seedNote).getSingleResult()).longValue() > 0) return;
    em.createNativeQuery("insert into service_request_part_requests(service_request_id,technician_profile_id,status,notes,created_at,submitted_at,rejected_at) values (?,?,?,?,now(6),now(6),?)")
        .setParameter(1, serviceRequestId.longValue()).setParameter(2, technician.getId()).setParameter(3, status).setParameter(4, seedNote).setParameter(5, "REJECTED".equals(status) ? LocalDateTime.now() : null).executeUpdate();
    Number requestId = (Number) em.createNativeQuery("select id from service_request_part_requests where notes=?").setParameter(1, seedNote).getSingleResult();
    for (String[] requestedPart : requestedParts) {
      Number itemId = (Number) em.createNativeQuery("select id from part_items where sku=?").setParameter(1, requestedPart[0]).getSingleResult();
      em.createNativeQuery("insert into service_request_part_lines(request_id,part_item_id,requested_quantity) values (?,?,?)").setParameter(1, requestId.longValue()).setParameter(2, itemId.longValue()).setParameter(3, Integer.parseInt(requestedPart[1])).executeUpdate();
    }
    em.createNativeQuery("insert into part_request_events(request_id,previous_status,new_status,actor_user_id,reason,created_at) values (?,null,?,?,?,now(6))").setParameter(1, requestId.longValue()).setParameter(2, status).setParameter(3, technician.getUser().getId()).setParameter(4, "Development seed request for " + customer.fullName).executeUpdate();
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

  private void seedGopiDemoCustomer(User admin, TechnicianProfile[] technicians, String password) {
    CustomerProfile customer = customer("mgopichakradhar@gmail.com", "+919810009999", "Gopi Chakradhar", "Gopi Chakradhar", "Hyderabad, Telangana", password);
    customer.hasLift = true;
    if (customer.referralCode == null || customer.referralCode.isBlank()) customer.referralCode = "VAL-GOPI-2026";
    if (exists("select count(r) from ServiceRequest r where r.serviceId=:value", "GOPI-SR-2026-001")) return;
    String[] names = {"Gopi Heights", "Chakradhar Residency", "MG Orchid", "Valor Gopi Plaza"};
    String[] districts = {"Hyderabad", "Rangareddy", "Medchal", "Warangal"};
    RequestStatus[] statuses = {RequestStatus.PENDING, RequestStatus.ASSIGNED, RequestStatus.ON_THE_WAY, RequestStatus.REACHED_SITE, RequestStatus.COMPLETED, RequestStatus.CANCELLED};
    WorkflowServiceType[] serviceTypes = {WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.BREAKDOWN, WorkflowServiceType.EMERGENCY, WorkflowServiceType.INSPECTION, WorkflowServiceType.MODERNIZATION, WorkflowServiceType.INSTALLATION};
    int sequence = 1;
    for (int b = 0; b < names.length; b++) {
      Building building = new Building(); building.setCustomer(customer); building.setBuildingName(names[b]); building.setBuildingType(b == 3 ? "Commercial" : "Residential");
      building.setAddress("%s, %s, Telangana".formatted(names[b], districts[b])); building.setCity(districts[b]); building.setState("Telangana"); building.setPincode("5000%02d".formatted(b + 10));
      building.setEmergencyContactName(customer.fullName); building.setEmergencyContactPhone(customer.getUser().getPhone()); em.persist(building);
      for (int l = 1; l <= 3; l++) {
        Lift lift = new Lift(); lift.setBuilding(building); lift.setName("%s Lift %d".formatted(names[b], l)); lift.setLiftNumber("GC-%s-%02d".formatted(names[b].substring(0, 1).toUpperCase(Locale.ROOT), l + (b * 3)));
        lift.setManufacturer(l % 2 == 0 ? "KONE" : "Otis"); lift.setModel("Valor-%d%d".formatted(b + 1, l)); lift.setCapacity(8 + l); lift.setFloorCount(4 + b + l);
        lift.setSerialNumber("GOPI-LIFT-%02d".formatted(sequence)); lift.setInstallationDate(LocalDate.now().minusYears(1 + b).minusMonths(l));
        lift.setLocation(l == 1 ? "Main lobby" : "Block %d lobby".formatted(l)); lift.setCurrentStatus(l == 3 && b == 1 ? LiftStatus.MAINTENANCE : LiftStatus.ACTIVE);
        lift.setWarrantyStatus(l == 1 ? "ACTIVE" : "NO_WARRANTY"); lift.setWarrantyStartDate(LocalDate.now().minusYears(2)); lift.setWarrantyEndDate(LocalDate.now().plusMonths(10));
        lift.setLastMaintenanceDate(LocalDate.now().minusDays(20 + sequence)); lift.setNextMaintenanceDate(LocalDate.now().plusDays(10 + sequence)); lift.setHealthScore((byte) (94 - sequence)); lift.setMachineRoom(l % 2 == 0 ? "NO" : "YES"); em.persist(lift);
        AmcContract amc = new AmcContract(); amc.setLift(lift); amc.setAmcNumber("AMC-GOPI-2026-%03d".formatted(sequence)); amc.setPlan(l % 2 == 0 ? "Premium AMC" : "Standard AMC");
        amc.setCoverageDetails("Seeded full customer journey AMC coverage."); amc.setStartDate(LocalDate.now().minusMonths(8)); amc.setEndDate(LocalDate.now().plusMonths(4 + l));
        amc.setStatus(sequence % 5 == 0 ? AmcStatus.EXPIRED : AmcStatus.ACTIVE); amc.setRenewalDate(amc.getEndDate().minusDays(30)); amc.setRenewalCount(sequence % 3); em.persist(amc);
        if (sequence <= 6) seedGopiRequestBundle(admin, customer, lift, amc, technicians[(sequence - 1) % technicians.length], sequence, statuses[sequence - 1], serviceTypes[sequence - 1]);
        sequence++;
      }
    }
    seedGopiNotification(customer.getUser().getId(), "Welcome to Valor", "Your demo account has buildings, lifts, AMC, billing, visits and service history ready.");
    seedGopiNotification(customer.getUser().getId(), "AMC renewal due", "One of your seeded AMC contracts is ready for renewal review.");
  }

  private void seedGopiRequestBundle(User admin, CustomerProfile customer, Lift lift, AmcContract amc, TechnicianProfile technician, int sequence, RequestStatus status, WorkflowServiceType type) {
    ServiceRequest request = new ServiceRequest(); request.setCustomer(customer); request.setLift(lift); request.setServiceId("GOPI-SR-2026-%03d".formatted(sequence));
    request.setTitle(type == WorkflowServiceType.EMERGENCY ? "Emergency breakdown response" : type.name().replace('_', ' ')); request.setDescription("Seeded Gopi demo service request for full customer app validation.");
    request.setIssueCategory(type == WorkflowServiceType.INSTALLATION ? "Installation" : "Lift service"); request.setPriority(type == WorkflowServiceType.EMERGENCY ? RequestPriority.EMERGENCY : sequence % 2 == 0 ? RequestPriority.HIGH : RequestPriority.MEDIUM);
    request.setStatus(status); request.setServiceType(type); request.setCustomerRemarks("Customer demo data"); request.setTechnicianRemarks(status == RequestStatus.REACHED_SITE ? "Technician arrived at site." : null);
    request.setServiceRequestedAt(LocalDateTime.now().minusDays(8L - sequence)); request.setPreferredVisitDate(LocalDate.now().plusDays(sequence % 3)); request.setPreferredTimeSlot("10:00 AM - 12:00 PM"); request.setEstimatedCompletionMinutes(90);
    if (status == RequestStatus.COMPLETED) request.setCompletedAt(LocalDateTime.now().minusDays(1)); em.persist(request); em.flush();
    if (status != RequestStatus.PENDING && status != RequestStatus.CANCELLED) {
      TechnicianAssignment assignment = new TechnicianAssignment(); assignment.setRequest(request); assignment.setTechnician(technician); assignment.setAssignedBy(admin);
      assignment.setStatus(status == RequestStatus.COMPLETED ? AssignmentStatus.COMPLETED : AssignmentStatus.ACCEPTED); assignment.setAssignedAt(LocalDateTime.now().minusDays(2)); assignment.setAcceptedAt(LocalDateTime.now().minusDays(1)); assignment.setNotes("Gopi demo assignment."); em.persist(assignment);
      em.createNativeQuery("insert into service_visits(service_request_id,technician_profile_id,scheduled_date,start_time,end_time,status,notes) values (?,?,?,?,?,?,?)")
          .setParameter(1, request.getId()).setParameter(2, technician.getId()).setParameter(3, LocalDate.now().plusDays(sequence % 3)).setParameter(4, LocalTime.of(10, 0))
          .setParameter(5, LocalTime.of(12, 0)).setParameter(6, status == RequestStatus.COMPLETED ? "COMPLETED" : status == RequestStatus.REACHED_SITE ? "IN_PROGRESS" : "SCHEDULED").setParameter(7, "Gopi seeded visit").executeUpdate();
    }
    em.createNativeQuery("insert into invoices(invoice_number,customer_id,service_request_id,amc_contract_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,due_date) values (?,?,?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, "INV-GOPI-2026-%03d".formatted(sequence)).setParameter(2, customer.getId()).setParameter(3, request.getId()).setParameter(4, amc.getId()).setParameter(5, admin.getId())
        .setParameter(6, "Gopi demo invoice %03d".formatted(sequence)).setParameter(7, 10000 + sequence * 500).setParameter(8, 1800 + sequence * 90).setParameter(9, 11800 + sequence * 590)
        .setParameter(10, "INR").setParameter(11, sequence % 2 == 0 ? "PAID" : "ISSUED").setParameter(12, LocalDate.now().minusDays(sequence)).setParameter(13, LocalDate.now().plusDays(15 + sequence)).executeUpdate();
    Number invoiceId = (Number) em.createNativeQuery("select id from invoices where invoice_number=?").setParameter(1, "INV-GOPI-2026-%03d".formatted(sequence)).getSingleResult();
    em.createNativeQuery("insert into payment_records(customer_id,service_request_id,amc_contract_id,invoice_id,created_by_user_id,amount,currency,purpose,status,provider_reference) values (?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, customer.getId()).setParameter(2, request.getId()).setParameter(3, amc.getId()).setParameter(4, invoiceId.longValue()).setParameter(5, admin.getId())
        .setParameter(6, 11800 + sequence * 590).setParameter(7, "INR").setParameter(8, sequence % 2 == 0 ? "INVOICE" : "AMC_RENEWAL").setParameter(9, sequence % 2 == 0 ? "SUCCEEDED" : "PENDING").setParameter(10, "GOPI-DEMO-%03d".formatted(sequence)).executeUpdate();
    em.createNativeQuery("insert into amc_renewal_requests(amc_contract_id,customer_id,requested_by_user_id,status,requested_start_date,requested_end_date,quoted_amount,currency,customer_notes) values (?,?,?,?,?,?,?,?,?)")
        .setParameter(1, amc.getId()).setParameter(2, customer.getId()).setParameter(3, customer.getUser().getId()).setParameter(4, sequence % 2 == 0 ? "QUOTED" : "REQUESTED")
        .setParameter(5, amc.getEndDate().plusDays(1)).setParameter(6, amc.getEndDate().plusYears(1)).setParameter(7, 15000 + sequence * 750).setParameter(8, "INR").setParameter(9, "Please renew this seeded AMC.").executeUpdate();
    if (status == RequestStatus.COMPLETED) em.createNativeQuery("insert into service_request_feedback(service_request_id,customer_id,rating,comment) values (?,?,?,?)").setParameter(1, request.getId()).setParameter(2, customer.getId()).setParameter(3, 5).setParameter(4, "Service completed neatly in the demo flow.").executeUpdate();
    seedGopiNotification(customer.getUser().getId(), request.getTitle(), "Status: " + status.name().replace('_', ' '));
  }

  private void seedGopiNotification(Long userId, String title, String message) {
    em.createNativeQuery("insert into notifications(recipient_user_id,title,message,channel,status,sent_at) values (?,?,?,?,?,?)")
        .setParameter(1, userId).setParameter(2, title).setParameter(3, message).setParameter(4, "IN_APP").setParameter(5, "SENT").setParameter(6, LocalDateTime.now()).executeUpdate();
  }

  private void seedAnanyaCustomerDemo(User admin, CustomerProfile customer, TechnicianProfile[] technicians, String password) {
    if (exists("select count(r) from ServiceRequest r where r.serviceId=:value", "ANANYA-SR-2026-001")) return;
    customer.status = "ACTIVE"; customer.active = true; customer.hasLift = true;
    if (customer.referralCode == null || customer.referralCode.isBlank()) customer.referralCode = "VAL-ANANYA-2026";
    customer.getUser().setActive(true); customer.getUser().setLocked(false); customer.getUser().setLockedUntil(null);
    if (password != null && !password.isBlank()) customer.getUser().setPasswordHash(encoder.encode(password));

    String[][] buildingRows = {
        {"Rao Residency", "Residential", "Indiranagar, Bengaluru", "Bengaluru", "Karnataka", "560038", "MAIN"},
        {"Rao Heights", "Residential", "Whitefield, Bengaluru", "Bengaluru", "Karnataka", "560066", "SECONDARY"},
        {"Rao Business Centre", "Commercial", "Madhapur, Hyderabad", "Hyderabad", "Telangana", "500081", "OTHER"},
        {"Rao Orchid Annex", "Residential", "Vijayawada Road, Guntur", "Guntur", "Andhra Pradesh", "522001", "OTHER"}
    };
    Lift[][] liftsByBuilding = new Lift[buildingRows.length][];
    for (int b = 0; b < buildingRows.length; b++) {
      Building building = new Building(); building.setCustomer(customer); building.setBuildingName(buildingRows[b][0]); building.setBuildingType(buildingRows[b][1]);
      building.setAddress(buildingRows[b][2]); building.setCity(buildingRows[b][3]); building.setState(buildingRows[b][4]); building.setPincode(buildingRows[b][5]);
      building.setBuildingPreference(buildingRows[b][6]); building.setLatitude(BigDecimal.valueOf(12.9716 + b * 0.012)); building.setLongitude(BigDecimal.valueOf(77.5946 + b * 0.009));
      building.setEmergencyContactName(customer.fullName); building.setEmergencyContactPhone(customer.getUser().getPhone()); em.persist(building);
      liftsByBuilding[b] = new Lift[b == 0 ? 3 : 2];
      for (int l = 0; l < liftsByBuilding[b].length; l++) {
        int seq = b * 3 + l + 1;
        Lift lift = new Lift(); lift.setBuilding(building); lift.setName("%s %s Lift".formatted(buildingRows[b][0], l == 0 ? "Main" : l == 1 ? "Service" : "East Wing"));
        lift.setLiftNumber("ANANYA-L-%03d".formatted(seq)); lift.setLiftType(l == 1 ? "Service Lift" : "Passenger Lift"); lift.setManufacturer(seq % 2 == 0 ? "KONE" : "Otis");
        lift.setModel("Valor-A%d%d".formatted(b + 1, l + 1)); lift.setCapacity(8 + seq); lift.setFloorCount(5 + b + l); lift.setSerialNumber("ANANYA-LIFT-%03d".formatted(seq));
        lift.setInstallationDate(LocalDate.now().minusYears(1 + b).minusMonths(l * 3L)); lift.setLocation(l == 0 ? "Main lobby" : "Block " + (char)('A' + l));
        lift.setCurrentStatus(seq == 4 ? LiftStatus.MAINTENANCE : seq == 7 ? LiftStatus.DOWN : LiftStatus.ACTIVE); lift.setWarrantyStatus(seq <= 4 ? "ACTIVE" : "EXPIRED");
        lift.setWarrantyStartDate(LocalDate.now().minusYears(2)); lift.setWarrantyEndDate(seq <= 4 ? LocalDate.now().plusMonths(9) : LocalDate.now().minusMonths(2));
        lift.setLastMaintenanceDate(LocalDate.now().minusDays(12L + seq * 4L)); lift.setNextMaintenanceDate(seq % 3 == 0 ? LocalDate.now() : LocalDate.now().plusDays(seq));
        lift.setHealthScore((byte) Math.max(55, 96 - seq * 5)); lift.setMachineRoom(seq % 2 == 0 ? "MRL" : "Dedicated machine room"); em.persist(lift);
        liftsByBuilding[b][l] = lift;
      }
    }

    RequestStatus[] statuses = {RequestStatus.PENDING, RequestStatus.ASSIGNED, RequestStatus.ACCEPTED, RequestStatus.ON_THE_WAY, RequestStatus.REACHED_SITE, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.WAITING_FOR_PARTS, RequestStatus.TESTING, RequestStatus.COMPLETED, RequestStatus.CANCELLED, RequestStatus.COMPLETED, RequestStatus.COMPLETED};
    WorkflowServiceType[] types = {WorkflowServiceType.INSTALLATION, WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.INSPECTION, WorkflowServiceType.EMERGENCY, WorkflowServiceType.BREAKDOWN, WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.BREAKDOWN, WorkflowServiceType.MODERNIZATION, WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.INSPECTION, WorkflowServiceType.EMERGENCY, WorkflowServiceType.BREAKDOWN};
    LocalDate[] visitDates = {LocalDate.now().plusDays(7), LocalDate.now().plusDays(2), LocalDate.now().plusDays(1), LocalDate.now(), LocalDate.now(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(5), LocalDate.now().plusDays(14), LocalDate.now().minusDays(20), LocalDate.now().minusDays(45), LocalDate.now().minusMonths(8), LocalDate.now().minusYears(1).minusMonths(3)};
    for (int i = 0; i < statuses.length; i++) {
      Lift lift = liftsByBuilding[i % liftsByBuilding.length][i % liftsByBuilding[i % liftsByBuilding.length].length];
      AmcContract amc = seedAnanyaAmc(lift, i + 1);
      seedAnanyaRequestBundle(admin, customer, lift, amc, technicians[i % technicians.length], i + 1, statuses[i], types[i], visitDates[i]);
    }
    seedAnanyaNotification(customer.getUser().getId(), "Welcome back, Ananya", "Your demo account has buildings, lifts, service history, visits, invoices, dues, failed payments and AMC renewals ready.");
    seedAnanyaNotification(customer.getUser().getId(), "Today visit scheduled", "A technician visit is scheduled today for one of your active service requests.");
    seedAnanyaNotification(customer.getUser().getId(), "Payment failed", "One invoice payment failed and is available for retry in the payments screen.");
    seedAnanyaNotification(customer.getUser().getId(), "AMC renewal upcoming", "A seeded AMC contract is approaching renewal.");
  }

  private AmcContract seedAnanyaAmc(Lift lift, int sequence) {
    AmcContract amc = new AmcContract(); amc.setLift(lift); amc.setAmcNumber("AMC-ANANYA-2026-%03d".formatted(sequence));
    amc.setPlan(sequence % 3 == 0 ? "Premium AMC" : sequence % 3 == 1 ? "Comprehensive AMC" : "Standard AMC");
    amc.setCoverageDetails("Ananya demo coverage for app validation."); amc.setStartDate(LocalDate.now().minusMonths(10 + sequence));
    amc.setEndDate(sequence % 4 == 0 ? LocalDate.now().minusDays(sequence) : LocalDate.now().plusMonths(2 + sequence % 6));
    amc.setStatus(sequence % 4 == 0 ? AmcStatus.EXPIRED : AmcStatus.ACTIVE); amc.setRenewalDate(amc.getEndDate().minusDays(30)); amc.setRenewalCount(sequence % 3); em.persist(amc);
    return amc;
  }

  private void seedAnanyaRequestBundle(User admin, CustomerProfile customer, Lift lift, AmcContract amc, TechnicianProfile technician, int sequence, RequestStatus status, WorkflowServiceType type, LocalDate visitDate) {
    ServiceRequest request = new ServiceRequest(); request.setCustomer(customer); request.setLift(type == WorkflowServiceType.INSTALLATION ? null : lift); request.setServiceId("ANANYA-SR-2026-%03d".formatted(sequence));
    request.setTitle(switch (type) {
      case INSTALLATION -> "New lift installation consultation";
      case EMERGENCY -> "Emergency lift stoppage";
      case BREAKDOWN -> sequence % 2 == 0 ? "Door sensor breakdown" : "Lift vibration and noise";
      case INSPECTION -> "Safety inspection visit";
      case MODERNIZATION -> "Controller modernization review";
      default -> "Routine maintenance service";
    });
    request.setDescription("Ananya seeded customer journey request covering app UI states."); request.setIssueCategory(type.name().replace('_', ' '));
    request.setPriority(type == WorkflowServiceType.EMERGENCY ? RequestPriority.EMERGENCY : sequence % 4 == 0 ? RequestPriority.HIGH : RequestPriority.MEDIUM);
    request.setStatus(status); request.setServiceType(type); request.setCustomerRemarks("Seeded Ananya customer note for mobile testing.");
    request.setTechnicianRemarks(EnumSet.of(RequestStatus.REACHED_SITE, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING).contains(status) ? "Technician update is visible for this request." : null);
    request.setServiceRequestedAt(visitDate.atTime(9, 15).minusDays(sequence <= 2 ? 1 : 3)); request.setPreferredVisitDate(visitDate); request.setPreferredTimeSlot(sequence % 2 == 0 ? "01:00 PM - 03:00 PM" : "10:00 AM - 12:00 PM"); request.setEstimatedCompletionMinutes(60 + sequence * 10);
    if (status == RequestStatus.COMPLETED) request.setCompletedAt(visitDate.atTime(16, 30)); em.persist(request); em.flush();
    if (status != RequestStatus.PENDING) {
      TechnicianAssignment assignment = new TechnicianAssignment(); assignment.setRequest(request); assignment.setTechnician(technician); assignment.setAssignedBy(admin);
      assignment.setStatus(status == RequestStatus.COMPLETED ? AssignmentStatus.COMPLETED : status == RequestStatus.CANCELLED ? AssignmentStatus.RELEASED : status == RequestStatus.ASSIGNED ? AssignmentStatus.ASSIGNED : AssignmentStatus.ACCEPTED);
      assignment.setAssignedAt(request.getServiceRequestedAt().plusHours(3)); if (assignment.getStatus() == AssignmentStatus.ACCEPTED || assignment.getStatus() == AssignmentStatus.COMPLETED) assignment.setAcceptedAt(assignment.getAssignedAt().plusHours(2));
      assignment.setReleasedAt(status == RequestStatus.CANCELLED ? visitDate.atTime(15, 0) : null); assignment.setNotes("Ananya seeded assignment."); em.persist(assignment); em.flush();
      em.createNativeQuery("insert into service_visits(service_request_id,technician_profile_id,scheduled_date,start_time,end_time,status,notes) values (?,?,?,?,?,?,?)")
          .setParameter(1, request.getId()).setParameter(2, technician.getId()).setParameter(3, visitDate).setParameter(4, sequence % 2 == 0 ? LocalTime.of(13, 0) : LocalTime.of(10, 0))
          .setParameter(5, sequence % 2 == 0 ? LocalTime.of(15, 0) : LocalTime.of(12, 0)).setParameter(6, status == RequestStatus.COMPLETED ? "COMPLETED" : status == RequestStatus.CANCELLED ? "CANCELLED" : EnumSet.of(RequestStatus.REACHED_SITE, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING).contains(status) ? "IN_PROGRESS" : "SCHEDULED")
          .setParameter(7, "Ananya seeded visit for " + status.name()).executeUpdate();
      if (EnumSet.of(RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING, RequestStatus.COMPLETED).contains(status)) {
        em.createNativeQuery("insert into service_reports(service_request_id,assignment_id,reported_by_user_id,diagnosis,work_performed,testing_result,completion_notes) values (?,?,?,?,?,?,?)")
            .setParameter(1, request.getId()).setParameter(2, assignment.getId()).setParameter(3, technician.getUser().getId()).setParameter(4, "Seeded diagnosis for customer history.")
            .setParameter(5, "Checked controller, doors, safety circuit and ride quality.").setParameter(6, status == RequestStatus.COMPLETED ? "Passed final testing." : "Testing in progress.")
            .setParameter(7, status == RequestStatus.COMPLETED ? "Completed and handed over to customer." : "Pending final closure.").executeUpdate();
      }
    }
    String invoiceStatus = sequence == 3 ? "ISSUED" : sequence == 6 ? "ISSUED" : sequence == 10 ? "CANCELLED" : status == RequestStatus.COMPLETED ? "PAID" : "ISSUED";
    LocalDate dueDate = sequence == 3 ? LocalDate.now().minusDays(5) : sequence == 6 ? LocalDate.now() : sequence == 7 ? LocalDate.now().plusDays(1) : visitDate.plusDays(12);
    em.createNativeQuery("insert into invoices(invoice_number,customer_id,service_request_id,amc_contract_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,due_date) values (?,?,?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, "INV-ANANYA-2026-%03d".formatted(sequence)).setParameter(2, customer.getId()).setParameter(3, request.getId()).setParameter(4, amc.getId()).setParameter(5, admin.getId())
        .setParameter(6, "Ananya demo invoice %03d".formatted(sequence)).setParameter(7, 5500 + sequence * 600).setParameter(8, 990 + sequence * 108).setParameter(9, 6490 + sequence * 708)
        .setParameter(10, "INR").setParameter(11, invoiceStatus).setParameter(12, visitDate.minusDays(1)).setParameter(13, dueDate).executeUpdate();
    Number invoiceId = (Number) em.createNativeQuery("select id from invoices where invoice_number=?").setParameter(1, "INV-ANANYA-2026-%03d".formatted(sequence)).getSingleResult();
    String paymentStatus = sequence == 3 ? "FAILED" : sequence == 6 || sequence == 7 ? "PENDING" : invoiceStatus.equals("PAID") ? "SUCCEEDED" : "PROCESSING";
    em.createNativeQuery("insert into payment_records(customer_id,service_request_id,amc_contract_id,invoice_id,created_by_user_id,amount,currency,purpose,status,provider_reference,failure_reason) values (?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, customer.getId()).setParameter(2, request.getId()).setParameter(3, amc.getId()).setParameter(4, invoiceId.longValue()).setParameter(5, admin.getId())
        .setParameter(6, 6490 + sequence * 708).setParameter(7, "INR").setParameter(8, sequence % 3 == 0 ? "AMC_RENEWAL" : "SERVICE_REQUEST").setParameter(9, paymentStatus)
        .setParameter(10, "ANANYA-DEMO-%03d".formatted(sequence)).setParameter(11, paymentStatus.equals("FAILED") ? "Seeded failed payment for retry testing." : null).executeUpdate();
    em.createNativeQuery("insert into amc_renewal_requests(amc_contract_id,customer_id,requested_by_user_id,status,requested_start_date,requested_end_date,quoted_amount,currency,invoice_id,customer_notes) values (?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, amc.getId()).setParameter(2, customer.getId()).setParameter(3, customer.getUser().getId()).setParameter(4, sequence % 4 == 0 ? "PAYMENT_PENDING" : sequence % 3 == 0 ? "QUOTED" : "REQUESTED")
        .setParameter(5, amc.getEndDate().plusDays(1)).setParameter(6, amc.getEndDate().plusYears(1)).setParameter(7, 18000 + sequence * 900).setParameter(8, "INR").setParameter(9, invoiceId.longValue())
        .setParameter(10, "Seeded AMC renewal request for Ananya.").executeUpdate();
    if (status == RequestStatus.COMPLETED) em.createNativeQuery("insert into service_request_feedback(service_request_id,customer_id,rating,comment) values (?,?,?,?)")
        .setParameter(1, request.getId()).setParameter(2, customer.getId()).setParameter(3, sequence % 2 == 0 ? 4 : 5).setParameter(4, "Seeded feedback for completed Ananya history.").executeUpdate();
    seedAnanyaNotification(customer.getUser().getId(), request.getTitle(), "Status: " + status.name().replace('_', ' '));
  }

  private void seedAnanyaNotification(Long userId, String title, String message) {
    em.createNativeQuery("insert into notifications(recipient_user_id,title,message,channel,status,sent_at) values (?,?,?,?,?,?)")
        .setParameter(1, userId).setParameter(2, title).setParameter(3, message).setParameter(4, "IN_APP").setParameter(5, "SENT").setParameter(6, LocalDateTime.now()).executeUpdate();
  }

  private void seedArjunTechnicianDemo(User admin, TechnicianProfile technician, String password) {
    if (exists("select count(r) from ServiceRequest r where r.serviceId=:value", "ARJUN-SR-2026-001")) {
      refreshArjunCashOtps();
      return;
    }
    CustomerProfile customer = customer("arjun.demo.customer@valor.local", "+919810007001", "R. Ajeesh Varma", "Valor Technician Demo", "Hyderabad, Telangana", password);
    String[] buildings = {"Sun City Apartments", "Green Park Residency", "Lake View Towers", "Maple Residency", "Tech Park Building", "Apollo Heights", "Madhani Tower", "Ore Residency"};
    String[] areas = {"Banjara Hills", "Gachibowli", "Madhapur", "Kondapur", "HITEC City", "Jubilee Hills", "Hyderabad", "Hyderabad"};
    RequestStatus[] statuses = {RequestStatus.ASSIGNED, RequestStatus.ON_THE_WAY, RequestStatus.REACHED_SITE, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING, RequestStatus.COMPLETED, RequestStatus.CANCELLED, RequestStatus.ASSIGNED};
    WorkflowServiceType[] types = {WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.INSPECTION, WorkflowServiceType.BREAKDOWN, WorkflowServiceType.ROUTINE_MAINTENANCE, WorkflowServiceType.EMERGENCY, WorkflowServiceType.BREAKDOWN, WorkflowServiceType.INSPECTION, WorkflowServiceType.EMERGENCY};
    for (int i = 0; i < statuses.length; i++) {
      Building building = new Building(); building.setCustomer(customer); building.setBuildingName(buildings[i]); building.setBuildingType(i == 4 ? "Commercial" : "Residential");
      building.setAddress("%s, %s, Hyderabad - 5000%02d".formatted(buildings[i], areas[i], 30 + i)); building.setCity(areas[i]); building.setState("Telangana"); building.setPincode("5000%02d".formatted(30 + i));
      building.setEmergencyContactName(customer.fullName); building.setEmergencyContactPhone(customer.getUser().getPhone()); em.persist(building);
      Lift lift = new Lift(); lift.setBuilding(building); lift.setName("Lift - %d (Passenger Lift)".formatted((i % 4) + 1)); lift.setLiftNumber("ARJUN-L-%03d".formatted(i + 1));
      lift.setManufacturer(i % 2 == 0 ? "Schindler" : "KONE"); lift.setModel("VX-Arjun-%02d".formatted(i + 1)); lift.setCapacity(10 + i); lift.setFloorCount(5 + i);
      lift.setSerialNumber("ARJUN-LIFT-%03d".formatted(i + 1)); lift.setInstallationDate(LocalDate.now().minusYears(2).minusMonths(i)); lift.setLocation(i % 2 == 0 ? "Tower 1" : "Block A");
      lift.setCurrentStatus(i == 2 || i == 4 ? LiftStatus.MAINTENANCE : LiftStatus.ACTIVE); lift.setWarrantyStatus("ACTIVE"); lift.setWarrantyStartDate(LocalDate.now().minusYears(2)); lift.setWarrantyEndDate(LocalDate.now().plusMonths(8));
      lift.setLastMaintenanceDate(LocalDate.now().minusDays(15 + i)); lift.setNextMaintenanceDate(LocalDate.now().plusDays(20 + i)); lift.setHealthScore((byte) (92 - i)); lift.setMachineRoom("YES"); em.persist(lift);
      AmcContract amc = new AmcContract(); amc.setLift(lift); amc.setAmcNumber("AMC-ARJUN-2026-%03d".formatted(i + 1)); amc.setPlan(i % 2 == 0 ? "General Service" : "Premium AMC");
      amc.setCoverageDetails("Arjun technician demo AMC coverage."); amc.setStartDate(LocalDate.now().minusMonths(5)); amc.setEndDate(LocalDate.now().plusMonths(8)); amc.setStatus(AmcStatus.ACTIVE); amc.setRenewalDate(amc.getEndDate().minusDays(30)); amc.setRenewalCount(0); em.persist(amc);
      seedArjunRequest(admin, customer, lift, amc, technician, i + 1, statuses[i], types[i]);
    }
    seedTechnicianNotification(technician.getUser().getId(), "Emergency Request", "New emergency request received at Skyline Towers (Lift - 2).");
    seedTechnicianNotification(technician.getUser().getId(), "New Job Assigned", "You have been assigned a general service at Sun City Apartments.");
    seedTechnicianNotification(technician.getUser().getId(), "Job Status Updated", "Your job at Green Park Residency is now marked as On The Way.");
    seedTechnicianNotification(technician.getUser().getId(), "New Message", "You have a new message from your supervisor.");
    seedTechnicianNotification(technician.getUser().getId(), "Parts Update", "Requested part for Tech Park Building has been approved.");
  }

  private void seedArjunRequest(User admin, CustomerProfile customer, Lift lift, AmcContract amc, TechnicianProfile technician, int sequence, RequestStatus status, WorkflowServiceType type) {
    ServiceRequest request = new ServiceRequest(); request.setCustomer(customer); request.setLift(lift); request.setServiceId("ARJUN-SR-2026-%03d".formatted(sequence));
    request.setTitle(type == WorkflowServiceType.EMERGENCY ? "Emergency lift not responding" : sequence % 2 == 0 ? "Elevator not working" : "Routine maintenance visit");
    request.setDescription(sequence % 2 == 0 ? "Lift is making unusual noise during operation. Please check and service." : "Carry standard service kit. Check door sensors and lubrication.");
    request.setIssueCategory(type == WorkflowServiceType.INSPECTION ? "Inspection" : type == WorkflowServiceType.EMERGENCY ? "Emergency" : "Lift service");
    request.setPriority(type == WorkflowServiceType.EMERGENCY ? RequestPriority.EMERGENCY : sequence == 3 ? RequestPriority.HIGH : RequestPriority.MEDIUM);
    request.setStatus(status); request.setServiceType(type); request.setCustomerRemarks("Customer requested careful inspection and clean work area.");
    request.setTechnicianRemarks(status == RequestStatus.REACHED_SITE ? "Reached site and waiting for arrival OTP." : null);
    request.setServiceRequestedAt(LocalDateTime.now().minusDays(sequence)); request.setPreferredVisitDate(LocalDate.now().plusDays(sequence % 3));
    request.setPreferredTimeSlot(sequence % 2 == 0 ? "01:00 PM - 02:00 PM" : "10:00 AM - 11:00 AM"); request.setEstimatedCompletionMinutes(90);
    if (status == RequestStatus.COMPLETED) request.setCompletedAt(LocalDateTime.now().minusHours(6)); em.persist(request); em.flush();
    TechnicianAssignment assignment = new TechnicianAssignment(); assignment.setRequest(request); assignment.setTechnician(technician); assignment.setAssignedBy(admin);
    assignment.setStatus(status == RequestStatus.COMPLETED ? AssignmentStatus.COMPLETED : status == RequestStatus.CANCELLED ? AssignmentStatus.RELEASED : status == RequestStatus.ASSIGNED ? AssignmentStatus.ASSIGNED : AssignmentStatus.ACCEPTED);
    assignment.setAssignedAt(LocalDateTime.now().minusDays(2)); if (assignment.getStatus() == AssignmentStatus.ACCEPTED || assignment.getStatus() == AssignmentStatus.COMPLETED) assignment.setAcceptedAt(LocalDateTime.now().minusDays(1));
    assignment.setReleasedAt(status == RequestStatus.CANCELLED ? LocalDateTime.now().minusHours(3) : null); assignment.setNotes("Arjun technician demo assignment."); em.persist(assignment); em.flush();
    em.createNativeQuery("insert into service_visits(service_request_id,technician_profile_id,scheduled_date,start_time,end_time,status,notes) values (?,?,?,?,?,?,?)")
        .setParameter(1, request.getId()).setParameter(2, technician.getId()).setParameter(3, request.getPreferredVisitDate()).setParameter(4, LocalTime.of(10 + (sequence % 5), 0))
        .setParameter(5, LocalTime.of(11 + (sequence % 5), 0)).setParameter(6, status == RequestStatus.COMPLETED ? "COMPLETED" : EnumSet.of(RequestStatus.REACHED_SITE, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING).contains(status) ? "IN_PROGRESS" : "SCHEDULED")
        .setParameter(7, "Arjun seeded visit").executeUpdate();
    if (EnumSet.of(RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.TESTING, RequestStatus.COMPLETED).contains(status)) {
      em.createNativeQuery("insert into service_reports(service_request_id,assignment_id,reported_by_user_id,diagnosis,work_performed,testing_result,completion_notes) values (?,?,?,?,?,?,?)")
          .setParameter(1, request.getId()).setParameter(2, assignment.getId()).setParameter(3, technician.getUser().getId()).setParameter(4, "Door sensor and controller checks completed.")
          .setParameter(5, "Cleaned sensor track, checked alignment, verified cabin movement.").setParameter(6, "Lift passed operational test.").setParameter(7, status == RequestStatus.COMPLETED ? "Issue resolved. Elevator is working fine now." : "Work in progress.").executeUpdate();
    }
    em.createNativeQuery("insert into service_request_attachments(service_request_id,uploaded_by_user_id,original_filename,content_type,file_size,storage_key) values (?,?,?,?,?,?)")
        .setParameter(1, request.getId()).setParameter(2, customer.getUser().getId()).setParameter(3, "customer-site-photo-%03d.jpg".formatted(sequence)).setParameter(4, "image/jpeg").setParameter(5, 125000L)
        .setParameter(6, "seed/arjun/request-%03d/customer-site-photo.jpg".formatted(sequence)).executeUpdate();
    if (status == RequestStatus.ON_THE_WAY || status == RequestStatus.REACHED_SITE || status == RequestStatus.REPAIR_IN_PROGRESS || status == RequestStatus.TESTING) {
      em.createNativeQuery("insert into technician_latest_locations(technician_id,service_request_id,latitude,longitude,recorded_at) values (?,?,?,?,?)")
          .setParameter(1, technician.getId()).setParameter(2, request.getId()).setParameter(3, 17.4380 + sequence * 0.002).setParameter(4, 78.3820 + sequence * 0.002).setParameter(5, LocalDateTime.now()).executeUpdate();
    }
    em.createNativeQuery("insert into invoices(invoice_number,customer_id,service_request_id,amc_contract_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,due_date) values (?,?,?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, "INV-ARJUN-2026-%03d".formatted(sequence)).setParameter(2, customer.getId()).setParameter(3, request.getId()).setParameter(4, amc.getId()).setParameter(5, admin.getId())
        .setParameter(6, "Arjun technician demo invoice").setParameter(7, 4000 + sequence * 350).setParameter(8, 720 + sequence * 63).setParameter(9, 4720 + sequence * 413)
        .setParameter(10, "INR").setParameter(11, status == RequestStatus.COMPLETED ? "PAID" : "ISSUED").setParameter(12, LocalDate.now()).setParameter(13, LocalDate.now().plusDays(15)).executeUpdate();
    Number invoiceId = (Number) em.createNativeQuery("select id from invoices where invoice_number=?").setParameter(1, "INV-ARJUN-2026-%03d".formatted(sequence)).getSingleResult();
    em.createNativeQuery("insert into payment_records(customer_id,service_request_id,amc_contract_id,invoice_id,created_by_user_id,amount,currency,purpose,status,provider_reference) values (?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, customer.getId()).setParameter(2, request.getId()).setParameter(3, amc.getId()).setParameter(4, invoiceId.longValue()).setParameter(5, admin.getId())
        .setParameter(6, 4720 + sequence * 413).setParameter(7, "INR").setParameter(8, "SERVICE_REQUEST").setParameter(9, status == RequestStatus.COMPLETED ? "SUCCEEDED" : "PENDING").setParameter(10, sequence % 2 == 0 ? "CASH" : "UPI-DEMO").executeUpdate();
    if (sequence % 2 == 0) {
      Number paymentId = (Number) em.createNativeQuery("select id from payment_records where invoice_id=?").setParameter(1, invoiceId.longValue()).getSingleResult();
      em.createNativeQuery("insert into cash_payment_otps(payment_id,invoice_id,service_request_id,customer_profile_id,otp_hash,expires_at,customer_visible_code,customer_visible_until,created_at) values (?,?,?,?,?,?,?,?,?)")
          .setParameter(1, paymentId.longValue()).setParameter(2, invoiceId.longValue()).setParameter(3, request.getId()).setParameter(4, customer.getId()).setParameter(5, encoder.encode("1111"))
          .setParameter(6, LocalDateTime.now().plusMinutes(30)).setParameter(7, "1111").setParameter(8, LocalDateTime.now().plusMinutes(30)).setParameter(9, LocalDateTime.now()).executeUpdate();
    }
    seedTechnicianNotification(technician.getUser().getId(), request.getTitle(), "Status: " + status.name().replace('_', ' '));
  }

  private void refreshArjunCashOtps() {
    em.createNativeQuery("""
        update cash_payment_otps otp
        join service_requests request on request.id = otp.service_request_id
        set otp.otp_hash = ?, otp.expires_at = ?, otp.customer_visible_code = ?, otp.customer_visible_until = ?
        where request.service_id like 'ARJUN-SR-2026-%'
        """)
        .setParameter(1, encoder.encode("1111"))
        .setParameter(2, LocalDateTime.now().plusMinutes(30))
        .setParameter(3, "1111")
        .setParameter(4, LocalDateTime.now().plusMinutes(30))
        .executeUpdate();
  }

  private void seedTechnicianNotification(Long userId, String title, String message) {
    em.createNativeQuery("insert into notifications(recipient_user_id,title,message,channel,status,sent_at) values (?,?,?,?,?,?)")
        .setParameter(1, userId).setParameter(2, title).setParameter(3, message).setParameter(4, "IN_APP").setParameter(5, "SENT").setParameter(6, LocalDateTime.now()).executeUpdate();
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
