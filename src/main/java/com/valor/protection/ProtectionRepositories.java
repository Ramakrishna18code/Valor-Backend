package com.valor.protection;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface InsuranceProviderRepository extends JpaRepository<InsuranceProvider, Long> {
    Page<InsuranceProvider> findByActiveTrueAndNameContainingIgnoreCase(String search, Pageable pageable);
    Page<InsuranceProvider> findByNameContainingIgnoreCase(String search, Pageable pageable);
    Page<InsuranceProvider> findByActiveTrue(Pageable pageable);
}

interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {
    @Query(value = "select p from InsuranceProduct p join fetch p.provider where (:activeOnly = false or (p.active = true and p.provider.active = true)) and (:assetType is null or p.insuredAssetType = :assetType) and (:providerId is null or p.provider.id = :providerId) and (:search is null or lower(p.name) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')))",
            countQuery = "select count(p) from InsuranceProduct p where (:activeOnly = false or (p.active = true and p.provider.active = true)) and (:assetType is null or p.insuredAssetType = :assetType) and (:providerId is null or p.provider.id = :providerId) and (:search is null or lower(p.name) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')))")
    Page<InsuranceProduct> catalog(@Param("activeOnly") boolean activeOnly, @Param("assetType") InsuredAssetType assetType,
            @Param("providerId") Long providerId, @Param("search") String search, Pageable pageable);
}

interface TechnicianInsurancePolicyRepository extends JpaRepository<TechnicianInsurancePolicy, Long> {
    @Query(value = "select p from TechnicianInsurancePolicy p join fetch p.technician t join fetch t.user join fetch p.provider left join fetch p.product where (:ownerId is null or t.user.id = :ownerId) and (:status is null or p.status = :status) and (:search is null or lower(p.insurerPolicyNumber) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')))",
            countQuery = "select count(p) from TechnicianInsurancePolicy p join p.technician t where (:ownerId is null or t.user.id = :ownerId) and (:status is null or p.status = :status) and (:search is null or lower(p.insurerPolicyNumber) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')))")
    Page<TechnicianInsurancePolicy> list(@Param("ownerId") Long ownerId, @Param("status") PolicyStatus status,
            @Param("search") String search, Pageable pageable);
    @Query("select p from TechnicianInsurancePolicy p join fetch p.technician t join fetch t.user join fetch p.provider left join fetch p.product where p.id=:id")
    Optional<TechnicianInsurancePolicy> detailed(@Param("id") Long id);
    @Query("select p from TechnicianInsurancePolicy p join fetch p.technician t join fetch t.user where p.coverageEndDate=:date")
    List<TechnicianInsurancePolicy> expiringOn(@Param("date") LocalDate date);
}

interface LiftInsurancePolicyRepository extends JpaRepository<LiftInsurancePolicy, Long> {
    @Query(value = "select p from LiftInsurancePolicy p join fetch p.lift l join fetch l.building b join fetch b.customer c join fetch c.user join fetch p.provider left join fetch p.product where (:ownerId is null or c.user.id = :ownerId) and (:status is null or p.status = :status) and (:search is null or lower(p.insurerPolicyNumber) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')) or lower(l.name) like lower(concat('%', :search, '%')))",
            countQuery = "select count(p) from LiftInsurancePolicy p join p.lift l join l.building b join b.customer c where (:ownerId is null or c.user.id = :ownerId) and (:status is null or p.status = :status) and (:search is null or lower(p.insurerPolicyNumber) like lower(concat('%', :search, '%')) or lower(p.provider.name) like lower(concat('%', :search, '%')) or lower(l.name) like lower(concat('%', :search, '%')))")
    Page<LiftInsurancePolicy> list(@Param("ownerId") Long ownerId, @Param("status") PolicyStatus status,
            @Param("search") String search, Pageable pageable);
    @Query("select p from LiftInsurancePolicy p join fetch p.lift l join fetch l.building b join fetch b.customer c join fetch c.user join fetch p.provider left join fetch p.product where p.id=:id")
    Optional<LiftInsurancePolicy> detailed(@Param("id") Long id);
    @Query("select p from LiftInsurancePolicy p join fetch p.lift l join fetch l.building b join fetch b.customer c join fetch c.user where p.coverageEndDate=:date")
    List<LiftInsurancePolicy> expiringOn(@Param("date") LocalDate date);
}

interface LiftInsuranceApplicationRepository extends JpaRepository<LiftInsuranceApplication, Long> {
    @Query(value = "select a from LiftInsuranceApplication a join fetch a.customer c join fetch c.user join fetch a.lift l join fetch l.building join fetch a.provider left join fetch a.product where (:ownerId is null or c.user.id = :ownerId) and (:status is null or a.status = :status) and (:search is null or lower(l.name) like lower(concat('%', :search, '%')) or lower(a.provider.name) like lower(concat('%', :search, '%')))",
            countQuery = "select count(a) from LiftInsuranceApplication a join a.customer c join a.lift l where (:ownerId is null or c.user.id = :ownerId) and (:status is null or a.status = :status) and (:search is null or lower(l.name) like lower(concat('%', :search, '%')) or lower(a.provider.name) like lower(concat('%', :search, '%')))")
    Page<LiftInsuranceApplication> list(@Param("ownerId") Long ownerId, @Param("status") ApplicationStatus status,
            @Param("search") String search, Pageable pageable);
    @Query("select a from LiftInsuranceApplication a join fetch a.customer c join fetch c.user join fetch a.lift l join fetch l.building join fetch a.provider left join fetch a.product where a.id=:id")
    Optional<LiftInsuranceApplication> detailed(@Param("id") Long id);
}

interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, Long> {
    @Query(value = "select distinct c from InsuranceClaim c left join fetch c.technicianPolicy tp left join fetch tp.provider left join fetch c.liftPolicy lp left join fetch lp.provider join fetch c.claimant where (:ownerId is null or c.claimant.id = :ownerId) and (:status is null or c.status = :status) and (:search is null or lower(c.claimReference) like lower(concat('%', :search, '%')) or lower(c.insurerClaimReference) like lower(concat('%', :search, '%')))",
            countQuery = "select count(c) from InsuranceClaim c where (:ownerId is null or c.claimant.id = :ownerId) and (:status is null or c.status = :status) and (:search is null or lower(c.claimReference) like lower(concat('%', :search, '%')) or lower(c.insurerClaimReference) like lower(concat('%', :search, '%')))")
    Page<InsuranceClaim> list(@Param("ownerId") Long ownerId, @Param("status") ClaimStatus status,
            @Param("search") String search, Pageable pageable);
    @Query("select distinct c from InsuranceClaim c left join fetch c.technicianPolicy tp left join fetch tp.provider left join fetch c.liftPolicy lp left join fetch lp.provider join fetch c.claimant where c.id=:id")
    Optional<InsuranceClaim> detailed(@Param("id") Long id);
    long countByStatusIn(List<ClaimStatus> statuses);
    long countByClaimantIdAndStatusIn(Long claimantId, List<ClaimStatus> statuses);
}

interface InsuranceRenewalRepository extends JpaRepository<InsuranceRenewalRequest, Long> {
    @Query(value = "select distinct r from InsuranceRenewalRequest r left join fetch r.technicianPolicy tp left join fetch tp.provider left join fetch r.liftPolicy lp left join fetch lp.provider join fetch r.requestedBy where (:ownerId is null or r.requestedBy.id = :ownerId) and (:status is null or r.status = :status)",
            countQuery = "select count(r) from InsuranceRenewalRequest r where (:ownerId is null or r.requestedBy.id = :ownerId) and (:status is null or r.status = :status)")
    Page<InsuranceRenewalRequest> list(@Param("ownerId") Long ownerId, @Param("status") RenewalStatus status, Pageable pageable);
    @Query("select distinct r from InsuranceRenewalRequest r left join fetch r.technicianPolicy tp left join fetch tp.provider left join fetch r.liftPolicy lp left join fetch lp.provider join fetch r.requestedBy where r.id=:id")
    Optional<InsuranceRenewalRequest> detailed(@Param("id") Long id);
    long countByRequestedByIdAndStatusIn(Long userId, List<RenewalStatus> statuses);
}

interface InsuranceDocumentRepository extends JpaRepository<InsuranceDocument, Long> {
    List<InsuranceDocument> findByClaimIdOrderByCreatedAtAsc(Long id);
    List<InsuranceDocument> findByApplicationIdOrderByCreatedAtAsc(Long id);
    List<InsuranceDocument> findByTechnicianPolicyIdOrderByCreatedAtAsc(Long id);
    List<InsuranceDocument> findByLiftPolicyIdOrderByCreatedAtAsc(Long id);
}

interface InsuranceExpiryNoticeRepository extends JpaRepository<InsuranceExpiryNotice, Long> {
    boolean existsByPolicyTypeAndPolicyIdAndDaysBeforeExpiry(InsuredAssetType type, Long policyId, short days);
    Page<InsuranceExpiryNotice> findByRecipientId(Long recipientId, Pageable pageable);
    Optional<InsuranceExpiryNotice> findByIdAndRecipientId(Long id, Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}

interface InsuranceActivityRepository extends JpaRepository<InsuranceActivity, Long> {
    Page<InsuranceActivity> findByVisibleToId(Long userId, Pageable pageable);
    Page<InsuranceActivity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
