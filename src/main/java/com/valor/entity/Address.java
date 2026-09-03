package com.valor.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class Address {

    private String doorNo;

    private String buildingName;

    private String street;

    private String area;

    private String city;

    private String district;

    private String state;

    private String country;

    private String pincode;

}