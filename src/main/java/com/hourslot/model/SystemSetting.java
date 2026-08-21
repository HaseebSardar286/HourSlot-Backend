package com.hourslot.model;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    private String key;

    private String value;

    private LocalDateTime updatedAt;

    private User updatedBy;

    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
