package com.hourslot.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    private Long id;

    private String code;

    private String module;

    private String description;

    @Builder.Default
    private boolean active = true;
}
