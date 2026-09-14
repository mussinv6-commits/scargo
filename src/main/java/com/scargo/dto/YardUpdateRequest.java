package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class YardUpdateRequest {

    private String yardName;
    private String yardType;
    private String status;
    private Boolean isAvailable;
    private Double latitude;   
    private Double longitude; 

}