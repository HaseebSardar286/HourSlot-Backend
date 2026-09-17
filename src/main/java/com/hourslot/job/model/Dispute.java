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
public class Dispute {
    private Long id;
    private Long jobId;
    private Long openedByUserId;
    private String reason;
    @Builder.Default
    private String status = "OPEN";
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "OPEN";
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
