package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.Business;
import com.hourslot.repository.BusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {

    @Autowired
    private BusinessRepository businessRepository;

    @GetMapping("/businesses/unverified")
    public ResponseEntity<List<Business>> getUnverifiedBusinesses() {
        List<Business> unverified = businessRepository.findByVerified(false);
        return ResponseEntity.ok(unverified);
    }

    @PutMapping("/businesses/{id}/verify")
    public ResponseEntity<?> verifyBusiness(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Business not found with id: " + id));

        business.setVerified(true);
        businessRepository.save(business);

        return ResponseEntity.ok(new MessageResponse("Business " + business.getName() + " verified successfully!"));
    }
}
