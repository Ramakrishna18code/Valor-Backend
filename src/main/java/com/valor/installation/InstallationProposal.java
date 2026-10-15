package com.valor.installation;

import com.valor.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

enum ProposalStatus { DRAFT, PRESENTED, ACCEPTED, DECLINED, EXPIRED }

@Entity @Table(name="installation_proposals") @Getter @Setter
class InstallationProposal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="installation_request_id") InstallationRequest request;
    @Column(nullable=false, precision=14, scale=2) BigDecimal amount;
    @Column(nullable=false, length=3) String currency="INR";
    @Column(columnDefinition="TEXT") String scope;
    @Column(columnDefinition="TEXT") String notes;
    @Column(columnDefinition="TEXT") String terms;
    LocalDateTime validUntil;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) ProposalStatus status=ProposalStatus.DRAFT;
    @Version Long version;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by_user_id") User createdBy;
    LocalDateTime createdAt; LocalDateTime updatedAt; LocalDateTime presentedAt; LocalDateTime decidedAt;
    @Column(length=2000) String decisionNotes;
    @PrePersist void created(){createdAt=LocalDateTime.now(); updatedAt=createdAt;}
    @PreUpdate void updated(){updatedAt=LocalDateTime.now();}
}
