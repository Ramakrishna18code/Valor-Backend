package com.valor.workflow;

import com.valor.assets.AssetRecord;
import com.valor.auth.CustomerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "ServiceRequestFeedback")
@Table(name = "service_request_feedback")
@Getter @Setter
public class ServiceRequestFeedback extends AssetRecord {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false, unique = true)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @Column(nullable = false)
    private Integer rating;
    @Column(length = 2000)
    private String comment;
}
