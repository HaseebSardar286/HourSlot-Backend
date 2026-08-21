package com.hourslot.model;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    private Long id;

    private String ownerType;

    private Long ownerId;

    private String storageKey;

    private String url;

    private String mimeType;

    private Long bytes;

    @Builder.Default
    private int sortOrder = 0;

    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
