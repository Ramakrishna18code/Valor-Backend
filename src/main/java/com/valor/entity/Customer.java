package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.valor.enums.CustomerStatus;
import com.valor.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String phone;

    private String alternatePhone;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoleName role = RoleName.CUSTOMER;

    private String companyName;

    @Embedded
    private Address address;

    @Builder.Default
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CustomerStatus accountStatus = CustomerStatus.ACTIVE;

    private Double rating;

    private LocalDate lastServiceDate;

    private LocalDate nextScheduledServiceDate;

    @Builder.Default
    private Integer totalPreviousServices = 0;

    @OneToMany(
            mappedBy = "customer",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<Lift> lifts = new ArrayList<>();
}