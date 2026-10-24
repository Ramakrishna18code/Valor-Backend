package com.valor.assets;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

interface BuildingRepository extends JpaRepository<Building, Long> {
    @Query(value = "select b from Building b join fetch b.customer c join fetch c.user u where u.id=:userId order by b.id",
            countQuery = "select count(b) from Building b join b.customer c join c.user u where u.id=:userId")
    Page<Building> findOwned(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "select b from Building b join fetch b.customer c join fetch c.user where (:status is null or b.status=:status) order by b.id",
            countQuery = "select count(b) from Building b where (:status is null or b.status=:status)")
    Page<Building> listAssets(@Param("status") String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Building b where b.id = :id")
    Optional<Building> lockById(@Param("id") Long id);
}

interface LiftRepository extends JpaRepository<Lift, Long> {
    @Query(value = "select l from Lift l join fetch l.building b join fetch b.customer c join fetch c.user u where u.id=:userId order by l.id",
            countQuery = "select count(l) from Lift l join l.building b join b.customer c join c.user u where u.id=:userId")
    Page<Lift> findOwned(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "select l from Lift l join fetch l.building b join fetch b.customer c join fetch c.user where (:status is null or l.currentStatus=:status) order by l.id",
            countQuery = "select count(l) from Lift l where (:status is null or l.currentStatus=:status)")
    Page<Lift> listAssets(@Param("status") LiftStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lift l where l.id = :id")
    Optional<Lift> lockById(@Param("id") Long id);

    interface BuildingCount { Long getBuildingId(); Long getTotal(); }
    @Query("select l.building.id as buildingId, count(l) as total from Lift l where l.active = true group by l.building.id")
    List<BuildingCount> activeCounts();

    long countByBuildingIdAndActiveTrue(Long buildingId);
    boolean existsByLiftNumber(String liftNumber);
    boolean existsByLiftNumberAndIdNot(String liftNumber, Long id);
}

interface AmcContractRepository extends JpaRepository<AmcContract, Long> {
    @Query(value = "select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user where (:status is null or a.status=:status) order by a.id",
            countQuery = "select count(a) from AmcContract a where (:status is null or a.status=:status)")
    Page<AmcContract> listAssets(@Param("status") AmcStatus status, Pageable pageable);

    @Query(value = "select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user u where u.id = :userId and (:status is null or a.status=:status) order by a.id",
            countQuery = "select count(a) from AmcContract a join a.lift l join l.building b join b.customer c join c.user u where u.id = :userId and (:status is null or a.status=:status)")
    Page<AmcContract> findOwned(@Param("userId") Long userId, @Param("status") AmcStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AmcContract a where a.id = :id")
    Optional<AmcContract> lockById(@Param("id") Long id);

    @Query("select distinct a.lift.id from AmcContract a where a.status = com.valor.assets.AmcStatus.ACTIVE and a.startDate <= :asOfDate and a.endDate >= :asOfDate")
    List<Long> coveredLiftIds(@Param("asOfDate") LocalDate asOfDate);
}
