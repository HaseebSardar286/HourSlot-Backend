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
public class JobNote {
    private Long id;
    private Long jobId;
    private Long authorUserId;
    private String body;
    private LocalDateTime createdAt;
}
