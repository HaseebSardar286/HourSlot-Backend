package com.hourslot.service;

import com.hourslot.model.Business;
import com.hourslot.model.MemberRole;
import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationMember;
import com.hourslot.model.Staff;
import com.hourslot.model.User;
import com.hourslot.repository.BusinessRepository;
import com.hourslot.repository.MemberRoleRepository;
import com.hourslot.repository.OrganizationMemberRepository;
import com.hourslot.repository.OrganizationRepository;
import com.hourslot.repository.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenancyService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final BusinessRepository businessRepository;
    private final StaffRepository staffRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RbacService rbacService;
    private final EntitlementService entitlementService;

    public TenancyService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            BusinessRepository businessRepository,
            StaffRepository staffRepository,
            MemberRoleRepository memberRoleRepository,
            RbacService rbacService,
            EntitlementService entitlementService) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.businessRepository = businessRepository;
        this.staffRepository = staffRepository;
        this.memberRoleRepository = memberRoleRepository;
        this.rbacService = rbacService;
        this.entitlementService = entitlementService;
    }

    @Transactional
    public Organization provisionOrganization(User owner, String name) {
        String slug = uniqueOrgSlug(name, owner.getId());
        Organization organization = Organization.builder()
                .name(name == null || name.isBlank() ? (owner.getFirstName() + "'s Organization") : name + " Org")
                .slug(slug)
                .billingEmail(owner.getEmail())
                .status("ACTIVE")
                .defaultCurrency("USD")
                .build();
        organization = organizationRepository.save(organization);

        OrganizationMember member = OrganizationMember.builder()
                .organization(organization)
                .user(owner)
                .status("ACTIVE")
                .build();
        organizationMemberRepository.save(member);
        rbacService.grantSystemRole(owner, "ORG_OWNER", organization, null, null, null);
        entitlementService.ensureStarter(organization);
        return organization;
    }

    @Transactional(readOnly = true)
    public Optional<Organization> findOrganizationForUser(User user) {
        List<OrganizationMember> members = organizationMemberRepository.findActiveWithOrgByUserId(user.getId());
        if (!members.isEmpty()) {
            return Optional.of(members.get(0).getOrganization());
        }
        return findBusinessForUser(user).map(Business::getOrganization);
    }

    @Transactional(readOnly = true)
    public Organization requireOrganizationForUser(User user) {
        return findOrganizationForUser(user)
                .orElseThrow(() -> new RuntimeException("Organization not found."));
    }

    @Transactional(readOnly = true)
    public Optional<Business> findBusinessForUser(User user) {
        Optional<Business> owned = businessRepository.findFirstByMemberUserId(user.getId());
        if (owned.isPresent()) {
            return owned;
        }
        return staffRepository.findByUser(user).map(staff -> staff.getBranch().getBusiness());
    }

    @Transactional(readOnly = true)
    public Business requireBusinessForUser(User user) {
        return findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));
    }

    @Transactional(readOnly = true)
    public void attachOwner(Business business) {
        if (business == null) {
            return;
        }
        findOwner(business).ifPresent(business::setOwner);
    }

    @Transactional(readOnly = true)
    public void attachOwners(java.util.Collection<Business> businesses) {
        if (businesses == null || businesses.isEmpty()) {
            return;
        }
        businesses.forEach(this::attachOwner);
    }

    @Transactional(readOnly = true)
    public Optional<User> findOwner(Business business) {
        if (business.getOrganization() == null) {
            return Optional.empty();
        }
        List<OrganizationMember> members = organizationMemberRepository
                .findByOrganizationAndStatus(business.getOrganization(), "ACTIVE");
        if (members.isEmpty()) {
            return Optional.empty();
        }
        List<MemberRole> roles = memberRoleRepository.findActiveByUserIdIn(
                members.stream().map(om -> om.getUser().getId()).toList()
        );
        return roles.stream()
                .filter(mr -> "ORG_OWNER".equals(mr.getRole().getCode()))
                .map(MemberRole::getUser)
                .findFirst();
    }

    private String uniqueOrgSlug(String name, Long userId) {
        String base = slugify(name);
        if (base.isBlank()) {
            base = "org";
        }
        String slug = base + "-" + userId;
        if (!organizationRepository.existsBySlug(slug)) {
            return slug;
        }
        return slug + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String slugify(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
