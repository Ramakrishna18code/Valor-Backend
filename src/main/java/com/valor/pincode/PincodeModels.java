package com.valor.pincode;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name = "verified_pincodes")
class VerifiedPincode {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
 @Column(nullable=false,length=6) String pincode; @Column(nullable=false,length=120) String state;
 @Column(nullable=false,length=120) String district; @Column(name="post_office",nullable=false,length=160) String postOffice;
 @Column(length=160) String locality; @Column(name="source_name",nullable=false,length=160) String sourceName;
 @Column(name="source_version",nullable=false,length=120) String sourceVersion; @Column(name="source_updated_at") LocalDate sourceUpdatedAt;
 @Column(name="verified_at",nullable=false) LocalDateTime verifiedAt; @Column(nullable=false) boolean active;
}
interface VerifiedPincodeRepository extends org.springframework.data.jpa.repository.JpaRepository<VerifiedPincode,Long> {
 java.util.List<VerifiedPincode> findByPincodeAndActiveTrueOrderByPostOfficeAsc(String pincode);
}
