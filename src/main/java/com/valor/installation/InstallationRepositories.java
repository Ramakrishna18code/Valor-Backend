package com.valor.installation;

import com.valor.assets.Building;
import com.valor.auth.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

interface InstallationRequestRepository extends JpaRepository<InstallationRequest, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InstallationRequest r where r.id=:id")
    Optional<InstallationRequest> lockById(@Param("id") Long id);
    Optional<InstallationRequest> findByIdAndCustomerId(Long id, Long customerId);
    Optional<InstallationRequest> findByCustomerIdAndClientRequestKey(Long customerId, String key);
    Optional<InstallationRequest> findByCustomerIdAndSubmissionKey(Long customerId, String key);
    Page<InstallationRequest> findByCustomerId(Long customerId, Pageable pageable);
    @Query("select r from InstallationRequest r where r.status <> com.valor.installation.InstallationStatus.DRAFT and (:status is null or r.status = :status) and (:q is null or lower(coalesce(r.propertyType,'')) like lower(concat('%',:q,'%')) or lower(coalesce(r.siteCity,'')) like lower(concat('%',:q,'%')) or lower(coalesce(r.siteAddressLine1,'')) like lower(concat('%',:q,'%')) or cast(r.id as string) like concat('%',:q,'%')) order by r.createdAt desc, r.id desc")
    Page<InstallationRequest> adminSearch(@Param("status") InstallationStatus status, @Param("q") String q, Pageable pageable);
}
interface InstallationHistoryRepository extends JpaRepository<InstallationRequestHistory, Long> {
    List<InstallationRequestHistory> findByRequestIdOrderByChangedAtAscIdAsc(Long requestId);
}
interface InstallationDocumentRepository extends JpaRepository<InstallationRequestDocument, Long> {
    List<InstallationRequestDocument> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
}
interface InstallationAuthorizationRepository extends JpaRepository<PropertyAuthorization, Long> {}
interface InstallationBuildingRepository extends JpaRepository<Building, Long> {
    @Query("select b from Building b where b.id=:id and b.customer.id=:customerId and b.active=true")
    Optional<Building> findOwnedActive(@Param("id") Long id, @Param("customerId") Long customerId);
}
