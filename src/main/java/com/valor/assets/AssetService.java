package com.valor.assets;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.AuditService;
import com.valor.auth.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.assets.AssetDtos.*;

@Service
@Transactional
public class AssetService {
    private final BuildingRepository buildings;
    private final LiftRepository lifts;
    private final AmcContractRepository contracts;
    private final AssetIdentityAccess identities;
    private final Clock clock;
    private final AssetCustomerRepository customerProfiles;
    private final AuditService audit;

    public AssetService(BuildingRepository buildings, LiftRepository lifts,
            AmcContractRepository contracts, AssetIdentityAccess identities, Clock clock, AssetCustomerRepository customerProfiles,
            AuditService audit) {
        this.buildings = buildings;
        this.lifts = lifts;
        this.contracts = contracts;
        this.identities = identities;
        this.clock = clock;this.customerProfiles=customerProfiles;this.audit=audit;
    }

    @Transactional(readOnly = true)
    public List<BuildingView> buildings() {
        identities.requireAdmin();
        Map<Long, Long> counts = lifts.activeCounts().stream()
                .collect(Collectors.toMap(LiftRepository.BuildingCount::getBuildingId, LiftRepository.BuildingCount::getTotal));
        return buildings.listAssets().stream().map(b -> buildingView(b, counts.getOrDefault(b.getId(), 0L))).toList();
    }

    public BuildingView createBuilding(BuildingWrite input) {
        identities.requireAdmin();
        Building building = new Building();
        building.setCustomer(identities.activeCustomer(input.customerProfileId()));
        apply(building, input);
        buildings.saveAndFlush(building);
        audit.record("BUILDING_CREATE", "BUILDING", building.getId(), "Created building customer=" + building.getCustomer().getId());
        return buildingView(building, 0);
    }

    public BuildingView updateBuilding(Long id, BuildingWrite input) {
        identities.requireAdmin();
        Building building = activeBuilding(id);
        if (!building.getCustomer().getId().equals(input.customerProfileId())) {
            throw new AssetException(400, "Building ownership cannot be changed");
        }
        String before = buildingSummary(building);
        apply(building, input);
        buildings.flush();
        audit.record("BUILDING_UPDATE", "BUILDING", building.getId(), "Updated building", before, buildingSummary(building), "SUCCESS");
        return buildingView(building, lifts.countByBuildingIdAndActiveTrue(id));
    }

    public void deactivateBuilding(Long id) {
        identities.requireAdmin();
        Building building = buildings.lockById(id).orElseThrow(() -> missing("Building"));
        String before = "active=" + building.isActive() + ",status=" + building.getStatus();
        building.setActive(false);
        audit.record("BUILDING_DEACTIVATE", "BUILDING", id, "Deactivated building",
                before, "active=" + building.isActive() + ",status=" + building.getStatus(), "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<LiftView> lifts() {
        identities.requireAdmin();
        LocalDate asOf = LocalDate.now(clock);
        Set<Long> covered = new HashSet<>(contracts.coveredLiftIds(asOf));
        return lifts.listAssets().stream().map(l -> liftView(l, covered.contains(l.getId()), asOf)).toList();
    }

    public LiftView createLift(LiftWrite input) {
        identities.requireAdmin();
        Building building = activeBuilding(input.buildingId());
        Lift lift = new Lift();
        lift.setBuilding(building);
        apply(lift, input);
        lifts.saveAndFlush(lift);
        audit.record("LIFT_CREATE", "LIFT", lift.getId(), "Created lift building=" + lift.getBuilding().getId());
        return liftView(lift, false, LocalDate.now(clock));
    }

    public LiftView updateLift(Long id, LiftWrite input) {
        identities.requireAdmin();
        Lift lift = activeLift(id);
        if (!lift.getBuilding().getId().equals(input.buildingId())) {
            throw new AssetException(400, "Lift ownership cannot be changed");
        }
        String before = liftSummary(lift);
        apply(lift, input);
        lifts.flush();
        audit.record("LIFT_UPDATE", "LIFT", lift.getId(), "Updated lift", before, liftSummary(lift), "SUCCESS");
        LocalDate asOf = LocalDate.now(clock);
        return liftView(lift, contracts.coveredLiftIds(asOf).contains(id), asOf);
    }

    public void deactivateLift(Long id) {
        identities.requireAdmin();
        Lift lift = lifts.lockById(id).orElseThrow(() -> missing("Lift"));
        String before = liftSummary(lift);
        lift.setActive(false);
        audit.record("LIFT_DEACTIVATE", "LIFT", id, "Deactivated lift",
                before, liftSummary(lift), "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<AmcView> contracts() {
        var actor = identities.actor();
        List<AmcContract> rows;
        if (actor.getRole() == Role.CUSTOMER) {
            identities.requireActiveCustomerUser(actor);
            rows = contracts.findOwned(actor.getId());
        } else {
            if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) {
                throw new AccessDeniedException("Access denied");
            }
            rows = contracts.listAssets();
        }
        LocalDate asOf = LocalDate.now(clock);
        return rows.stream().map(a -> amcView(a, asOf)).toList();
    }

    public AmcView createContract(AmcWrite input) {
        identities.requireAdmin();
        Lift lift = activeLift(input.liftId());
        dates(input.startDate(), input.endDate());
        AmcContract contract = new AmcContract();
        contract.setLift(lift);
        contract.setAmcNumber(input.amcNumber() == null || input.amcNumber().isBlank()
                ? generatedAmcNumber()
                : input.amcNumber().trim());
        contract.setPlan(input.plan().trim());
        contract.setCoverageDetails(input.coverageDetails());
        contract.setStartDate(input.startDate());
        contract.setEndDate(input.endDate());
        contract.setRenewalDate(input.renewalDate());
        contracts.saveAndFlush(contract);
        audit.record("AMC_CREATE", "AMC", contract.getId(), "Created AMC lift=" + lift.getId(),
                null, amcSummary(contract), "SUCCESS");
        return amcView(contract, LocalDate.now(clock));
    }

    public AmcView renewContract(Long id, AmcRenew input) {
        identities.requireAdmin();
        AmcContract existing = contracts.findById(id).orElseThrow(() -> missing("AMC contract"));
        activeLift(existing.getLift().getId());
        AmcContract contract = contracts.lockById(id).orElseThrow(() -> missing("AMC contract"));
        dates(input.startDate(), input.endDate());
        if (!input.startDate().isAfter(contract.getEndDate())) {
            throw new AssetException(400, "Renewal must start after the current contract ends");
        }
        if (contract.getStatus() == AmcStatus.CANCELLED || contract.getStatus() == AmcStatus.NON_AMC) {
            throw new AssetException(409, "AMC contract cannot be renewed");
        }
        LocalDate asOf = LocalDate.now(clock);
        String before = amcSummary(contract);
        contract.setStartDate(input.startDate());
        contract.setEndDate(input.endDate());
        contract.setPlan(input.plan().trim());
        contract.setCoverageDetails(input.coverageDetails());
        contract.setRenewalDate(input.renewalDate());
        contract.setRenewalCount(Math.addExact(contract.getRenewalCount(), 1));
        contract.setLastReminderSentAt(null);
        contract.setStatus(AmcStatus.ACTIVE);
        contracts.flush();
        audit.record("AMC_RENEW", "AMC", contract.getId(), "Renewed AMC lift=" + contract.getLift().getId(),
                before, amcSummary(contract), "SUCCESS");
        return amcView(contract, asOf);
    }

    private com.valor.auth.User customerActor() {
        var user=identities.actor();if(user.getRole()!=Role.CUSTOMER)throw new AccessDeniedException("Access denied");
        identities.requireActiveCustomerUser(user);return user;
    }
    public List<BuildingView> customerBuildings() {
        Long id=customerActor().getId();
        return buildings.findOwned(id).stream()
            .map(b->buildingView(b,lifts.countByBuildingIdAndActiveTrue(b.getId()))).toList();
    }
    public BuildingView customerBuilding(Long id) {
        Building building = ownedActiveBuilding(id, customerActor().getId());
        return buildingView(building, lifts.countByBuildingIdAndActiveTrue(id));
    }
    public List<LiftView> customerLifts() {
        Long id=customerActor().getId();LocalDate date=LocalDate.now(clock);Set<Long> covered=new HashSet<>(contracts.coveredLiftIds(date));
        return lifts.findOwned(id).stream()
            .map(l->liftView(l,covered.contains(l.getId()),date)).toList();
    }
    public BuildingView createCustomerBuilding(CustomerBuildingWrite input) {
        Long id=customerActor().getId();
        var owner=customerProfiles.findByUserId(id).orElseThrow();
        Building building=new Building();building.setCustomer(owner);
        apply(building,new BuildingWrite(owner.getId(),input.buildingName(),input.buildingType(),input.address(),input.city(),input.state(),input.pincode(),input.latitude(),input.longitude(),input.emergencyContactName(),input.emergencyContactPhone(),"ACTIVE"));
        buildings.saveAndFlush(building);
        audit.record("BUILDING_CREATE", "BUILDING", building.getId(), "Created building customer=" + owner.getId());
        return buildingView(building,0);
    }
    public BuildingView updateCustomerBuilding(Long id, CustomerBuildingWrite input) {
        Long actorId = customerActor().getId();
        Building building = ownedActiveBuilding(id, actorId);
        String before = buildingSummary(building);
        apply(building, new BuildingWrite(building.getCustomer().getId(), input.buildingName(), input.buildingType(), input.address(), input.city(), input.state(), input.pincode(), input.latitude(), input.longitude(), input.emergencyContactName(), input.emergencyContactPhone(), "ACTIVE"));
        buildings.flush();
        audit.record("BUILDING_UPDATE", "BUILDING", building.getId(), "Updated building", before, buildingSummary(building), "SUCCESS");
        return buildingView(building, lifts.countByBuildingIdAndActiveTrue(id));
    }
    public void deactivateCustomerBuilding(Long id) {
        Long actorId = customerActor().getId();
        Building building = ownedActiveBuilding(id, actorId);
        String before = buildingSummary(building);
        building.setActive(false);
        building.setStatus("INACTIVE");
        audit.record("BUILDING_DEACTIVATE", "BUILDING", id, "Deactivated building", before, buildingSummary(building), "SUCCESS");
    }
    public LiftView customerLift(Long id) {
        Lift lift = ownedActiveLift(id, customerActor().getId());
        LocalDate asOf = LocalDate.now(clock);
        return liftView(lift, contracts.coveredLiftIds(asOf).contains(id), asOf);
    }
    public LiftView createCustomerLift(LiftWrite input) {
        Long actorId = customerActor().getId();
        Building building = ownedActiveBuilding(input.buildingId(), actorId);
        Lift lift = new Lift();
        lift.setBuilding(building);
        apply(lift, input);
        lifts.saveAndFlush(lift);
        audit.record("LIFT_CREATE", "LIFT", lift.getId(), "Created lift building=" + lift.getBuilding().getId());
        return liftView(lift, false, LocalDate.now(clock));
    }
    public LiftView updateCustomerLift(Long id, LiftWrite input) {
        Long actorId = customerActor().getId();
        Lift lift = ownedActiveLift(id, actorId);
        if (!lift.getBuilding().getId().equals(input.buildingId())) {
            throw new AssetException(400, "Lift building cannot be changed");
        }
        String before = liftSummary(lift);
        apply(lift, input);
        lifts.flush();
        audit.record("LIFT_UPDATE", "LIFT", lift.getId(), "Updated lift", before, liftSummary(lift), "SUCCESS");
        LocalDate asOf = LocalDate.now(clock);
        return liftView(lift, contracts.coveredLiftIds(asOf).contains(id), asOf);
    }
    public void deactivateCustomerLift(Long id) {
        Lift lift = ownedActiveLift(id, customerActor().getId());
        String before = liftSummary(lift);
        lift.setActive(false);
        audit.record("LIFT_DEACTIVATE", "LIFT", id, "Deactivated lift", before, liftSummary(lift), "SUCCESS");
    }

    private Building activeBuilding(Long id) {
        Building building = buildings.lockById(id).orElseThrow(() -> missing("Building"));
        if (!building.isActive()) throw new AssetException(409, "Building is inactive");
        identities.activeCustomer(building.getCustomer().getId());
        return building;
    }

    private Lift activeLift(Long id) {
        Lift existing = lifts.findById(id).orElseThrow(() -> missing("Lift"));
        activeBuilding(existing.getBuilding().getId());
        Lift lift = lifts.lockById(id).orElseThrow(() -> missing("Lift"));
        if (!lift.isActive()) throw new AssetException(409, "Lift is inactive");
        return lift;
    }
    private Building ownedActiveBuilding(Long id, Long userId) {
        Building building = buildings.lockById(id).orElseThrow(() -> missing("Building"));
        if (!building.getCustomer().getUser().getId().equals(userId)) throw missing("Building");
        if (!building.isActive()) throw new AssetException(409, "Building is inactive");
        identities.requireActiveCustomerUser(building.getCustomer().getUser());
        return building;
    }
    private Lift ownedActiveLift(Long id, Long userId) {
        Lift lift = lifts.lockById(id).orElseThrow(() -> missing("Lift"));
        if (!lift.getBuilding().getCustomer().getUser().getId().equals(userId)) throw missing("Lift");
        if (!lift.getBuilding().isActive()) throw new AssetException(409, "Building is inactive");
        if (!lift.isActive()) throw new AssetException(409, "Lift is inactive");
        identities.requireActiveCustomerUser(lift.getBuilding().getCustomer().getUser());
        return lift;
    }

    private AssetException missing(String asset) { return new AssetException(404, asset + " not found"); }

    private void dates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new AssetException(400, "Invalid date range");
        }
    }

    private String generatedAmcNumber() {
        return "AMC-" + LocalDate.now(clock).getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void apply(Building entity, BuildingWrite input) {
        entity.setBuildingName(input.buildingName());
        entity.setBuildingType(input.buildingType());
        entity.setAddress(input.address());
        entity.setCity(input.city());
        entity.setState(input.state());
        entity.setPincode(input.pincode());
        entity.setLatitude(input.latitude());
        entity.setLongitude(input.longitude());
        entity.setEmergencyContactName(input.emergencyContactName());
        entity.setEmergencyContactPhone(input.emergencyContactPhone());
        if (input.status() != null && input.status().isBlank()) throw new AssetException(400, "Invalid building status");
        entity.setStatus(input.status() == null ? "ACTIVE" : input.status());
    }

    private void apply(Lift entity, LiftWrite input) {
        if (input.warrantyStartDate() != null && input.warrantyEndDate() != null) {
            dates(input.warrantyStartDate(), input.warrantyEndDate());
        }
        entity.setName(input.name());
        entity.setLiftNumber(input.liftNumber());
        entity.setModel(input.model());
        entity.setManufacturer(input.manufacturer());
        entity.setCapacity(input.capacity());
        entity.setFloorCount(input.floorCount());
        entity.setSerialNumber(input.serialNumber());
        entity.setInstallationDate(input.installationDate());
        entity.setLocation(input.location());
        entity.setCurrentStatus(input.currentStatus() == null ? LiftStatus.ACTIVE : input.currentStatus());
        entity.setWarrantyStatus(input.warrantyStatus());
        entity.setWarrantyStartDate(input.warrantyStartDate());
        entity.setWarrantyEndDate(input.warrantyEndDate());
        entity.setLastMaintenanceDate(input.lastMaintenanceDate());
        entity.setNextMaintenanceDate(input.nextMaintenanceDate());
        entity.setHealthScore(input.healthScore());
        entity.setMachineRoom(input.machineRoom());
        entity.setQrCode(input.qrCode());
        entity.setSpecifications(input.specifications());
    }

    private String buildingSummary(Building entity) {
        return "buildingName=" + entity.getBuildingName()
                + ",buildingType=" + entity.getBuildingType()
                + ",city=" + entity.getCity()
                + ",status=" + entity.getStatus()
                + ",active=" + entity.isActive();
    }

    private String liftSummary(Lift entity) {
        return "name=" + entity.getName()
                + ",liftNumber=" + entity.getLiftNumber()
                + ",currentStatus=" + entity.getCurrentStatus()
                + ",healthScore=" + entity.getHealthScore()
                + ",active=" + entity.isActive();
    }

    private String amcSummary(AmcContract entity) {
        return "lift=" + entity.getLift().getId()
                + ",plan=" + entity.getPlan()
                + ",startDate=" + entity.getStartDate()
                + ",endDate=" + entity.getEndDate()
                + ",status=" + entity.getStatus()
                + ",renewalCount=" + entity.getRenewalCount();
    }

    private BuildingView buildingView(Building entity, long count) {
        return new BuildingView(
                entity.getId(),
                entity.getCustomer().getId(),
                entity.getBuildingName(),
                entity.getBuildingType(),
                entity.getAddress(),
                entity.getCity(),
                entity.getState(),
                entity.getPincode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getEmergencyContactName(),
                entity.getEmergencyContactPhone(),
                entity.getStatus(),
                entity.isActive(),
                count,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private LiftView liftView(Lift entity, boolean covered, LocalDate asOf) {
        return new LiftView(
                entity.getId(),
                entity.getBuilding().getId(),
                entity.getName(),
                entity.getLiftNumber(),
                entity.getModel(),
                entity.getManufacturer(),
                entity.getCapacity(),
                entity.getFloorCount(),
                entity.getSerialNumber(),
                entity.getInstallationDate(),
                entity.getLocation(),
                entity.getCurrentStatus(),
                entity.getWarrantyStatus(),
                entity.getWarrantyStartDate(),
                entity.getWarrantyEndDate(),
                entity.getLastMaintenanceDate(),
                entity.getNextMaintenanceDate(),
                entity.getHealthScore(),
                entity.getMachineRoom(),
                entity.getQrCode(),
                entity.getSpecifications(),
                entity.isActive(),
                covered ? "ACTIVE" : "NON_AMC",
                asOf,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AmcView amcView(AmcContract entity, LocalDate asOf) {
        return new AmcView(
                entity.getId(),
                entity.getLift().getId(),
                entity.getAmcNumber(),
                entity.getPlan(),
                entity.getCoverageDetails(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getRenewalDate(),
                entity.getLastReminderSentAt(),
                entity.getRenewalCount(),
                entity.getStatus() == AmcStatus.ACTIVE && !asOf.isBefore(entity.getStartDate()) && !asOf.isAfter(entity.getEndDate()),
                asOf,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

}
