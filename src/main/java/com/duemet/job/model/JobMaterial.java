package com.duemet.job.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** A part or consumable the provider used on a job (cable, pipe fitting, ...), billed on top of labour. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMaterial {
    private Long id;
    private Long jobId;
    private String name;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private LocalDateTime createdAt;

    public BigDecimal getLineTotal() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
