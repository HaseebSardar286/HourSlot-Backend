package com.hourslot.request.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRequestMedia {
    private Long id;
    private Long requestId;
    private Long mediaAssetId;
    private String storageKey;
    private String url;
    private String mimeType;
    @Builder.Default
    private int sortOrder = 0;
    private LocalDateTime createdAt;
}
