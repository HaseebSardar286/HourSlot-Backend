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
public class JobStatusHistory {
    private Long id;
    private Long jobId;
    private String fromStatus;
    private String toStatus;
    private Long changedBy;
    private String reason;
    private LocalDateTime createdAt;
}
