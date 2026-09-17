package com.hourslot.job.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMedia {
    private Long id;
    private Long jobId;
    private Long mediaAssetId;
    private String url;
    private String storageKey;
    private String mimeType;
    @Builder.Default
    private Integer sortOrder = 0;
    private LocalDateTime createdAt;
}
