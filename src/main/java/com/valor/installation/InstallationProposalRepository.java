package com.valor.installation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface InstallationProposalRepository extends JpaRepository<InstallationProposal, Long> {
    Optional<InstallationProposal> findTopByRequestIdOrderByCreatedAtDescIdDesc(Long requestId);
    Optional<InstallationProposal> findTopByRequestIdOrderByVersionDesc(Long requestId);
    List<InstallationProposal> findByRequestIdOrderByCreatedAtDescIdDesc(Long requestId);
    @Query("select p from InstallationProposal p where p.request.id = :requestId and p.status <> com.valor.installation.ProposalStatus.DRAFT order by p.version desc, p.id desc")
    List<InstallationProposal> findCustomerHistory(Long requestId);
    @Query("select coalesce(max(p.version), -1) from InstallationProposal p where p.request.id = :requestId")
    long maxVersion(Long requestId);
}
