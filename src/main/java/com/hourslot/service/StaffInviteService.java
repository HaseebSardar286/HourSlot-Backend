package com.hourslot.service;

import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationMember;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffInvite;
import com.hourslot.model.User;
import com.hourslot.model.UserRole;
import com.hourslot.repository.BranchRepository;
import com.hourslot.repository.OrganizationMemberRepository;
import com.hourslot.repository.StaffInviteRepository;
import com.hourslot.repository.StaffRepository;
import com.hourslot.repository.UserRepository;
import com.hourslot.util.TokenHashes;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class StaffInviteService {

    private final StaffInviteRepository staffInviteRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RbacService rbacService;
    private final EntitlementService entitlementService;
    private final PasswordEncoder passwordEncoder;

    public StaffInviteService(
            StaffInviteRepository staffInviteRepository,
            BranchRepository branchRepository,
            StaffRepository staffRepository,
            UserRepository userRepository,
            OrganizationMemberRepository organizationMemberRepository,
            RbacService rbacService,
            EntitlementService entitlementService,
            PasswordEncoder passwordEncoder) {
        this.staffInviteRepository = staffInviteRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.rbacService = rbacService;
        this.entitlementService = entitlementService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffInvite> list(Organization organization) {
        return staffInviteRepository.findByOrganizationOrderByCreatedAtDesc(organization);
    }

    @Transactional
    public Map<String, Object> invite(
            Organization organization,
            Business business,
            User inviter,
            Long branchId,
            String email,
            String displayName,
            String designation) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Staff name is required.");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        if (!branch.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Branch does not belong to this business.");
        }

        entitlementService.requireHeadroom(
                organization,
                EntitlementService.MAX_STAFF,
                entitlementService.countStaff(organization),
                "staff members");

        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        StaffInvite invite = staffInviteRepository.save(StaffInvite.builder()
                .organization(organization)
                .business(business)
                .branch(branch)
                .email(email.trim().toLowerCase(Locale.ROOT))
                .displayName(displayName.trim())
                .designation(designation)
                .tokenHash(TokenHashes.sha256(rawToken))
                .status(StaffInvite.PENDING)
                .invitedBy(inviter)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invite", toView(invite));
        payload.put("inviteToken", rawToken);
        payload.put("acceptPath", "/auth/accept-invite?token=" + rawToken);
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(String rawToken) {
        StaffInvite invite = requirePending(rawToken);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("email", invite.getEmail());
        view.put("displayName", invite.getDisplayName());
        view.put("designation", invite.getDesignation());
        view.put("businessName", invite.getBusiness().getName());
        view.put("branchName", invite.getBranch().getName());
        view.put("expiresAt", invite.getExpiresAt());
        return view;
    }

    @Transactional
    public Staff accept(String rawToken, String firstName, String lastName, String password, String phoneNumber) {
        StaffInvite invite = requirePending(rawToken);
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        entitlementService.requireHeadroom(
                invite.getOrganization(),
                EntitlementService.MAX_STAFF,
                entitlementService.countStaff(invite.getOrganization()),
                "staff members");

        User user = userRepository.findByEmail(invite.getEmail()).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(invite.getEmail())
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(firstName != null && !firstName.isBlank() ? firstName.trim() : invite.getDisplayName())
                    .lastName(lastName != null ? lastName.trim() : "")
                    .phoneNumber(phoneNumber)
                    .status("ACTIVE")
                    .build();
            user.setRole(UserRole.BUSINESS_STAFF);
            user = userRepository.save(user);
        } else {
            user.setPasswordHash(passwordEncoder.encode(password));
            if (firstName != null && !firstName.isBlank()) {
                user.setFirstName(firstName.trim());
            }
            if (lastName != null) {
                user.setLastName(lastName.trim());
            }
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                user.setPhoneNumber(phoneNumber.trim());
            }
            user.setActive(true);
            user = userRepository.save(user);
        }

        OrganizationMember member = organizationMemberRepository
                .findFirstByUserAndStatus(user, "ACTIVE")
                .orElse(null);
        if (member == null || !member.getOrganization().getId().equals(invite.getOrganization().getId())) {
            organizationMemberRepository.save(OrganizationMember.builder()
                    .organization(invite.getOrganization())
                    .user(user)
                    .status("ACTIVE")
                    .invitedBy(invite.getInvitedBy())
                    .joinedAt(LocalDateTime.now())
                    .build());
        }
        rbacService.grantSystemRole(
                user,
                "STAFF",
                invite.getOrganization(),
                invite.getBusiness(),
                invite.getBranch(),
                null);

        Staff staff = staffRepository.save(Staff.builder()
                .branch(invite.getBranch())
                .user(user)
                .displayName(invite.getDisplayName())
                .designation(invite.getDesignation())
                .active(true)
                .build());

        invite.setStatus(StaffInvite.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now());
        staffInviteRepository.save(invite);
        return staff;
    }

    @Transactional
    public void revoke(Organization organization, Long inviteId) {
        StaffInvite invite = staffInviteRepository.findByIdAndOrganization(inviteId, organization)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found."));
        if (StaffInvite.PENDING.equals(invite.getStatus())) {
            invite.setStatus(StaffInvite.REVOKED);
            staffInviteRepository.save(invite);
        }
    }

    public Map<String, Object> toView(StaffInvite invite) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", invite.getId());
        view.put("email", invite.getEmail());
        view.put("displayName", invite.getDisplayName());
        view.put("designation", invite.getDesignation());
        view.put("status", invite.getStatus());
        view.put("expiresAt", invite.getExpiresAt());
        view.put("createdAt", invite.getCreatedAt());
        view.put("branchId", invite.getBranch() != null ? invite.getBranch().getId() : null);
        view.put("branchName", invite.getBranch() != null ? invite.getBranch().getName() : null);
        return view;
    }

    private StaffInvite requirePending(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invite token is required.");
        }
        StaffInvite invite = staffInviteRepository.findByTokenHash(TokenHashes.sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invite is invalid."));
        if (!StaffInvite.PENDING.equals(invite.getStatus())) {
            throw new IllegalStateException("Invite is no longer pending.");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(StaffInvite.EXPIRED);
            staffInviteRepository.save(invite);
            throw new IllegalStateException("Invite has expired.");
        }
        return invite;
    }
}
