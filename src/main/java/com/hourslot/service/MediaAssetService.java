package com.hourslot.service;

import com.hourslot.model.BusinessMedia;
import com.hourslot.model.MediaAsset;
import com.hourslot.repository.BusinessMediaRepository;
import com.hourslot.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final BusinessMediaRepository businessMediaRepository;

    public MediaAssetService(
            MediaAssetRepository mediaAssetRepository,
            BusinessMediaRepository businessMediaRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.businessMediaRepository = businessMediaRepository;
    }

    @Transactional
    public void replaceLogo(Long businessId, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        businessMediaRepository.findFirstByBusinessIdAndRole(businessId, "logo")
                .ifPresent(existing -> {
                    MediaAsset asset = existing.getMediaAsset();
                    if (asset != null) {
                        asset.setDeletedAt(LocalDateTime.now());
                        mediaAssetRepository.save(asset);
                    }
                    businessMediaRepository.delete(existing);
                });
        MediaAsset asset = mediaAssetRepository.save(MediaAsset.builder()
                .ownerType("BUSINESS")
                .ownerId(businessId)
                .storageKey("logo-" + businessId + "-" + System.currentTimeMillis())
                .url(url.trim())
                .mimeType("image/*")
                .sortOrder(0)
                .build());
        businessMediaRepository.save(BusinessMedia.builder()
                .businessId(businessId)
                .mediaAssetId(asset.getId())
                .role("logo")
                .build());
    }

    @Transactional
    public void replaceGallery(Long businessId, String csvUrls) {
        List<BusinessMedia> existing = businessMediaRepository.findWithAssetsByBusinessId(businessId);
        for (BusinessMedia link : existing) {
            if (!"gallery".equals(link.getRole())) {
                continue;
            }
            if (link.getMediaAsset() != null) {
                link.getMediaAsset().setDeletedAt(LocalDateTime.now());
                mediaAssetRepository.save(link.getMediaAsset());
            }
            businessMediaRepository.delete(link);
        }
        if (csvUrls == null || csvUrls.isBlank()) {
            return;
        }
        AtomicInteger order = new AtomicInteger(0);
        Arrays.stream(csvUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(url -> addGalleryUrl(businessId, url, order.getAndIncrement()));
    }

    @Transactional
    public String appendGalleryUrl(Long businessId, String url) {
        List<BusinessMedia> gallery = businessMediaRepository.findByBusinessIdAndRole(businessId, "gallery");
        addGalleryUrl(businessId, url, gallery.size());
        return currentGalleryCsv(businessId);
    }

    @Transactional
    public String removeGalleryUrl(Long businessId, String url) {
        for (BusinessMedia link : businessMediaRepository.findWithAssetsByBusinessId(businessId)) {
            if (!"gallery".equals(link.getRole()) || link.getMediaAsset() == null) {
                continue;
            }
            if (url.equals(link.getMediaAsset().getUrl())) {
                link.getMediaAsset().setDeletedAt(LocalDateTime.now());
                mediaAssetRepository.save(link.getMediaAsset());
                businessMediaRepository.delete(link);
            }
        }
        return currentGalleryCsv(businessId);
    }

    public String currentGalleryCsv(Long businessId) {
        List<String> urls = new ArrayList<>();
        for (BusinessMedia link : businessMediaRepository.findWithAssetsByBusinessId(businessId)) {
            if ("gallery".equals(link.getRole()) && link.getMediaAsset() != null && link.getMediaAsset().getDeletedAt() == null) {
                urls.add(link.getMediaAsset().getUrl());
            }
        }
        return String.join(",", urls);
    }

    private void addGalleryUrl(Long businessId, String url, int sortOrder) {
        MediaAsset asset = mediaAssetRepository.save(MediaAsset.builder()
                .ownerType("BUSINESS")
                .ownerId(businessId)
                .storageKey("gallery-" + businessId + "-" + System.currentTimeMillis() + "-" + sortOrder)
                .url(url)
                .mimeType("image/*")
                .sortOrder(sortOrder)
                .build());
        businessMediaRepository.save(BusinessMedia.builder()
                .businessId(businessId)
                .mediaAssetId(asset.getId())
                .role("gallery")
                .build());
    }
}
