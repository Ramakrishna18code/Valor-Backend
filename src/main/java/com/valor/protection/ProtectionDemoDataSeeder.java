package com.valor.protection;

import com.valor.assets.Building;
import com.valor.assets.Lift;
import com.valor.auth.CustomerProfile;
import com.valor.auth.Role;
import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "valor.protection.demo-seed.enabled", havingValue = "true")
class ProtectionDemoDataSeeder implements ApplicationRunner {
    private static final String PROVIDER_NAME = "DEMO ONLY - NOT A REAL INSURER";
    private static final Logger log = LoggerFactory.getLogger(ProtectionDemoDataSeeder.class);
    private final EntityManager em;

    ProtectionDemoDataSeeder(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (em.createQuery("select count(p) from InsuranceProvider p where p.name=:name", Long.class)
                .setParameter("name", PROVIDER_NAME).getSingleResult() > 0) {
            log.info("Protection demo data already present: {}", demoRecordCounts());
            return;
        }

        User admin = em.createQuery("select u from User u where u.role in :roles order by u.id", User.class)
                .setParameter("roles", List.of(Role.ADMIN, Role.SUPER_ADMIN))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Protection demo seed requires an existing admin account"));
        User technicianUser = createDemoUser("technician", Role.TECHNICIAN);
        User customerUser = createDemoUser("customer", Role.CUSTOMER);
        em.flush();

        em.createNativeQuery("""
                insert into technician_profiles
                    (user_id, employee_id, assigned_area, specialization, availability_status, is_active, created_at, updated_at)
                values (:userId, 'DEMO-PROTECTION-TECH', 'Demo area', 'Demo lift maintenance', 'AVAILABLE', true, now(6), now(6))
                """)
                .setParameter("userId", technicianUser.getId())
                .executeUpdate();
        TechnicianProfile technician = em.createQuery(
                "select t from TechnicianProfile t where t.user.id=:userId", TechnicianProfile.class)
                .setParameter("userId", technicianUser.getId())
                .getSingleResult();

        CustomerProfile customer = new CustomerProfile();
        customer.setUser(customerUser);
        customer.setFullName("Demo Customer (sample only)");
        customer.setHasLift(true);
        em.persist(customer);

        Building building = new Building();
        building.setCustomer(customer);
        building.setBuildingName("DEMO ONLY - Sample Building");
        building.setBuildingType("Residential");
        building.setAddress("Local development sample address");
        building.setCity("Bengaluru");
        building.setState("Karnataka");
        building.setPincode("560001");
        building.setLatitude(new BigDecimal("12.971600"));
        building.setLongitude(new BigDecimal("77.594600"));
        building.setEmergencyContactName("Demo Customer");
        em.persist(building);

        Lift lift = new Lift();
        lift.setBuilding(building);
        lift.setName("DEMO ONLY - Sample Lift");
        lift.setLiftNumber("DEMO-LIFT-001");
        lift.setManufacturer("Sample Manufacturer");
        lift.setModel("DEMO-MODEL");
        lift.setCapacity(8);
        lift.setFloorCount(10);
        lift.setSerialNumber("DEMO-NOT-A-REAL-LIFT");
        lift.setLocation("Sample building lobby");
        em.persist(lift);

        InsuranceProvider provider = new InsuranceProvider();
        provider.name = PROVIDER_NAME;
        provider.description = "Synthetic local UI data only. This is not an insurer or an offer of coverage.";
        provider.active = false;
        provider.createdBy = admin;
        em.persist(provider);

        InsuranceProduct technicianProduct = product(provider, "DEMO ONLY - Technician sample product",
                InsuredAssetType.TECHNICIAN, "Synthetic sample coverage; not a real insurance product.");
        InsuranceProduct liftProduct = product(provider, "DEMO ONLY - Lift sample product",
                InsuredAssetType.LIFT, "Synthetic sample coverage; not a real insurance product.");
        em.persist(technicianProduct);
        em.persist(liftProduct);
        em.flush();

        LocalDate today = LocalDate.now();
        TechnicianInsurancePolicy technicianPolicy = new TechnicianInsurancePolicy();
        technicianPolicy.technician = technician;
        technicianPolicy.provider = provider;
        technicianPolicy.product = technicianProduct;
        technicianPolicy.insurerPolicyNumber = "DEMO-NOT-A-REAL-POLICY-TECH";
        technicianPolicy.coverageStartDate = today;
        technicianPolicy.coverageEndDate = today.plusYears(1);
        technicianPolicy.premiumAmount = new BigDecimal("999.00");
        technicianPolicy.currency = "INR";
        technicianPolicy.status = PolicyStatus.DECLARED;
        technicianPolicy.notes = "DEMO ONLY - synthetic record; not an insurer-issued policy.";
        em.persist(technicianPolicy);

        LiftInsurancePolicy liftPolicy = new LiftInsurancePolicy();
        liftPolicy.lift = lift;
        liftPolicy.provider = provider;
        liftPolicy.product = liftProduct;
        liftPolicy.insurerPolicyNumber = "DEMO-NOT-A-REAL-POLICY-LIFT";
        liftPolicy.coverageStartDate = today;
        liftPolicy.coverageEndDate = today.plusYears(1);
        liftPolicy.premiumAmount = new BigDecimal("4999.00");
        liftPolicy.insuredValue = new BigDecimal("500000.00");
        liftPolicy.currency = "INR";
        liftPolicy.status = PolicyStatus.DECLARED;
        liftPolicy.notes = "DEMO ONLY - synthetic record; no coverage is in force.";
        em.persist(liftPolicy);

        LiftInsuranceApplication application = new LiftInsuranceApplication();
        application.customer = customer;
        application.lift = lift;
        application.provider = provider;
        application.product = liftProduct;
        application.coverageRequested = "DEMO ONLY - sample quote request; do not contact an insurer.";
        application.insuredValue = new BigDecimal("500000.00");
        application.status = ApplicationStatus.SUBMITTED;
        em.persist(application);

        InsuranceClaim claim = new InsuranceClaim();
        claim.claimReference = "DEMO-CLAIM-001";
        claim.liftPolicy = liftPolicy;
        claim.claimant = customerUser;
        claim.status = ClaimStatus.SUBMITTED;
        claim.incidentDate = today.minusDays(2);
        claim.description = "DEMO ONLY - synthetic claim example; no real incident or insurer decision.";
        claim.claimedAmount = new BigDecimal("12000.00");
        claim.currency = "INR";
        em.persist(claim);

        InsuranceRenewalRequest renewal = new InsuranceRenewalRequest();
        renewal.liftPolicy = liftPolicy;
        renewal.requestedBy = customerUser;
        renewal.status = RenewalStatus.REQUESTED;
        renewal.requestedStartDate = today.plusYears(1);
        renewal.note = "DEMO ONLY - synthetic renewal request; no insurer contacted.";
        em.persist(renewal);
        em.flush();
        log.info("Protection demo data seeded: {}", demoRecordCounts());
    }

    private String demoRecordCounts() {
        return "providers=%d, products=%d, technicianPolicies=%d, liftPolicies=%d, applications=%d, claims=%d, renewals=%d"
                .formatted(count("select count(p) from InsuranceProvider p where p.name=:name"),
                        count("select count(p) from InsuranceProduct p where p.provider.name=:name"),
                        count("select count(p) from TechnicianInsurancePolicy p where p.provider.name=:name"),
                        count("select count(p) from LiftInsurancePolicy p where p.provider.name=:name"),
                        count("select count(a) from LiftInsuranceApplication a where a.provider.name=:name"),
                        count("select count(c) from InsuranceClaim c where c.liftPolicy.provider.name=:name"),
                        count("select count(r) from InsuranceRenewalRequest r where r.liftPolicy.provider.name=:name"));
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).setParameter("name", PROVIDER_NAME).getSingleResult();
    }

    private User createDemoUser(String kind, Role role) {
        String email = "protection-demo-" + kind + "@example.test";
        return em.createQuery("select u from User u where u.email=:email", User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setRole(role);
                    user.setPasswordHash(null);
                    em.persist(user);
                    return user;
                });
    }

    private InsuranceProduct product(InsuranceProvider provider, String name, InsuredAssetType type, String summary) {
        InsuranceProduct product = new InsuranceProduct();
        product.provider = provider;
        product.name = name;
        product.insuredAssetType = type;
        product.coverageSummary = summary;
        product.minimumPremium = new BigDecimal("999.00");
        product.maximumPremium = new BigDecimal("4999.00");
        product.currency = "INR";
        product.active = false;
        return product;
    }
}
