package com.valor.installation;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface InstallationScheduleRepository extends JpaRepository<InstallationSchedule,Long> {
    List<InstallationSchedule> findByRequestIdOrderByVersionNoDesc(Long requestId);
    Optional<InstallationSchedule> findByIdAndRequestId(Long id, Long requestId);
    Optional<InstallationSchedule> findTopByScheduleKeyOrderByVersionNoDesc(String scheduleKey);
}
interface InstallationScheduleApprovalRepository extends JpaRepository<InstallationScheduleApproval,Long> {
    Optional<InstallationScheduleApproval> findByScheduleIdAndApprovalType(Long scheduleId, InstallationScheduleApprovalType type);
    boolean existsByScheduleIdAndApprovalType(Long scheduleId, InstallationScheduleApprovalType type);
}
