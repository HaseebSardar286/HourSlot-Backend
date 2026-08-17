package com.hourslot.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "business_media")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(BusinessMedia.PK.class)
public class BusinessMedia {

    @Id
    @Column(name = "business_id")
    private Long businessId;

    @Id
    @Column(name = "media_asset_id")
    private Long mediaAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", insertable = false, updatable = false)
    private MediaAsset mediaAsset;

    @Column(nullable = false)
    @Builder.Default
    private String role = "gallery";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Long businessId;
        private Long mediaAssetId;
    }
}
