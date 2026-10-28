package com.duemet.job.repository;

import com.duemet.job.model.JobMaterial;
import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JobMaterialRepository {
    private static final String SELECT = """
            SELECT id, job_id, name, quantity, unit_price, created_at
            FROM job_materials
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<JobMaterial> mapper = (rs, i) -> JobMaterial.builder()
            .id(rs.getLong("id"))
            .jobId(rs.getLong("job_id"))
            .name(rs.getString("name"))
            .quantity(rs.getBigDecimal("quantity"))
            .unitPrice(rs.getBigDecimal("unit_price"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public JobMaterialRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public JobMaterial save(JobMaterial material) {
        material.setId(jdbc.insert("""
                INSERT INTO job_materials (job_id, name, quantity, unit_price, created_at)
                VALUES (:jobId, :name, :quantity, :unitPrice, NOW())
                """, jdbc.params()
                .addValue("jobId", material.getJobId())
                .addValue("name", material.getName())
                .addValue("quantity", material.getQuantity())
                .addValue("unitPrice", material.getUnitPrice())));
        return material;
    }

    public List<JobMaterial> findByJob(Long jobId) {
        return jdbc.findList(SELECT + " WHERE job_id = :jobId ORDER BY id",
                jdbc.params().addValue("jobId", jobId), mapper);
    }

    public BigDecimal totalForJob(Long jobId) {
        return findByJob(jobId).stream()
                .map(JobMaterial::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int delete(Long jobId, Long materialId) {
        return jdbc.update("DELETE FROM job_materials WHERE id = :id AND job_id = :jobId",
                jdbc.params().addValue("id", materialId).addValue("jobId", jobId));
    }
}
