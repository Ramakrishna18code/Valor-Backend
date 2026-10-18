package com.valor.installation;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

interface InstallationMilestoneRepository extends JpaRepository<InstallationMilestone,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from InstallationMilestone m join fetch m.request r join fetch r.customer where m.id=:id")
    Optional<InstallationMilestone> lockById(Long id);
}
