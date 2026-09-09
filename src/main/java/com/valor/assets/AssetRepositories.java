package com.valor.assets;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface BuildingRepository extends JpaRepository<Building, Long> {
    @Query("select b from Building b join fetch b.customer c join fetch c.user u where u.id=:userId order by b.id")
    List<Building> findOwned(@Param("userId") Long userId);

    @Query("select b from Building b join fetch b.customer c join fetch c.user order by b.id")
    List<Building> listAssets();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Building b where b.id = :id")
    Optional<Building> lockById(@Param("id") Long id);
}

interface LiftRepository extends JpaRepository<Lift, Long> {
    @Query("select l from Lift l join fetch l.building b join fetch b.customer c join fetch c.user u where u.id=:userId order by l.id")
    List<Lift> findOwned(@Param("userId") Long userId);

    @Query("select l from Lift l join fetch l.building b join fetch b.customer c join fetch c.user order by l.id")
    List<Lift> listAssets();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lift l where l.id = :id")
    Optional<Lift> lockById(@Param("id") Long id);

    interface BuildingCount { Long getBuildingId(); Long getTotal(); }
    @Query("select l.building.id as buildingId, count(l) as total from Lift l where l.active = true group by l.building.id")
    List<BuildingCount> activeCounts();

    long countByBuildingIdAndActiveTrue(Long buildingId);
}

interface AmcContractRepository extends JpaRepository<AmcContract, Long> {
    @Query("select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user order by a.id")
    List<AmcContract> listAssets();

    @Query("select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user u where u.id = :userId order by a.id")
    List<AmcContract> findOwned(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AmcContract a where a.id = :id")
    Optional<AmcContract> lockById(@Param("id") Long id);

    @Query("select distinct a.lift.id from AmcContract a where a.status = com.valor.assets.AmcStatus.ACTIVE and a.startDate <= :asOfDate and a.endDate >= :asOfDate")
    List<Long> coveredLiftIds(@Param("asOfDate") LocalDate asOfDate);
}
