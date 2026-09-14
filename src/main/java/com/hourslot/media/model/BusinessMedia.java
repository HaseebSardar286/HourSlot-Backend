package com.hourslot.media.model;

import lombok.*;

import java.io.Serializable;
import com.hourslot.organization.model.Business;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessMedia {

    private Long businessId;

    private Long mediaAssetId;

    private Business business;

    private MediaAsset mediaAsset;

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
