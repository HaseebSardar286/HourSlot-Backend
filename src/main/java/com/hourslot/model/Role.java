package com.hourslot.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    private Long id;

    private String scope;

    private Organization organization;

    private Business business;

    private String code;

    private String name;

    @Builder.Default
    private boolean system = false;

    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
