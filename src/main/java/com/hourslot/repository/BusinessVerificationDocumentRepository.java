package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.BusinessVerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessVerificationDocumentRepository extends JpaRepository<BusinessVerificationDocument, Long> {
    List<BusinessVerificationDocument> findByBusinessOrderByCreatedAtDesc(Business business);
    Optional<BusinessVerificationDocument> findByBusinessAndDocumentType(Business business, String documentType);
    long countByBusinessAndStatus(Business business, String status);
}
