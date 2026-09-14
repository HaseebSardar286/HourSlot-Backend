package com.hourslot.catalog.model;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hourslot.organization.model.Staff;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffService {

    private Long id;

    private Staff staff;

    private Service service;

    private Double priceOverride;
}
