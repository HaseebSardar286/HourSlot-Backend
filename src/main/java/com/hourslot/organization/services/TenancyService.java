package com.hourslot.organization.services;

import com.hourslot.booking.model.Booking;
import com.hourslot.organization.model.Branch;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.MemberRole;
import com.hourslot.organization.model.Organization;
import com.hourslot.organization.model.OrganizationMember;
import com.hourslot.organization.model.Staff;
import com.hourslot.identity.model.User;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.repository.MemberRoleRepository;
import com.hourslot.organization.repository.OrganizationMemberRepository;
import com.hourslot.organization.repository.OrganizationRepository;
import com.hourslot.organization.repository.StaffRepository;
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
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RbacService rbacService;
    private final EntitlementService entitlementService;
    private final SystemSettingService systemSettingService;

    public TenancyService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            StaffRepository staffRepository,
            MemberRoleRepository memberRoleRepository,
            RbacService rbacService,
            EntitlementService entitlementService,
            SystemSettingService systemSettingService) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.memberRoleRepository = memberRoleRepository;
        this.rbacService = rbacService;
        this.entitlementService = entitlementService;
        this.systemSettingService = systemSettingService;
    }

    @Transactional
    public Organization provisionOrganization(User owner, String name) {
        return provisionOrganization(owner, name, null, null, null, null, null);
    }

    @Transactional
    public Organization provisionOrganization(
            User owner,
            String name,
            String currency,
            String countryCode,
            String region,
            String city,
            String timezone) {
        String slug = uniqueOrgSlug(name, owner.getId());
        String resolvedCurrency = currency == null || currency.isBlank()
                ? systemSettingService.defaultCurrency()
                : currency.trim().toUpperCase(Locale.ROOT);
        Organization organization = Organization.builder()
                .name(name == null || name.isBlank() ? (owner.getFirstName() + "'s Organization") : name + " Org")
                .slug(slug)
                .billingEmail(owner.getEmail())
                .status("ACTIVE")
                .defaultCurrency(resolvedCurrency)
                .countryCode(blankToNull(countryCode) == null ? null : countryCode.trim().toUpperCase(Locale.ROOT))
                .region(blankToNull(region))
                .city(blankToNull(city))
                .timezone(blankToNull(timezone))
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
        return staffRepository.findByUser(user)
                .map(staff -> staff.getBranch() == null ? null : staff.getBranch().getBusiness());
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
    public boolean branchBelongsToBusiness(Branch branch, Business business) {
        Long expectedId = business == null ? null : business.getId();
        Long actualId = businessIdOf(branch);
        return expectedId != null && expectedId.equals(actualId);
    }

    @Transactional(readOnly = true)
    public boolean staffBelongsToBusiness(Staff staff, Business business) {
        if (staff == null) {
            return false;
        }
        if (staff.getBranch() != null && businessIdOf(staff.getBranch()) != null) {
            return branchBelongsToBusiness(staff.getBranch(), business);
        }
        if (staff.getId() == null) {
            return false;
        }
        return staffRepository.findById(staff.getId())
                .map(loaded -> branchBelongsToBusiness(loaded.getBranch(), business))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean bookingBelongsToBusiness(Booking booking, Business business) {
        Long expectedId = business == null ? null : business.getId();
        Long actualId = businessIdOf(booking);
        return expectedId != null && expectedId.equals(actualId);
    }

    @Transactional(readOnly = true)
    public Long businessIdOf(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getBusiness() != null && booking.getBusiness().getId() != null) {
            return booking.getBusiness().getId();
        }
        return businessIdOf(booking.getBranch());
    }

    @Transactional(readOnly = true)
    public Long businessIdOf(Branch branch) {
        if (branch == null) {
            return null;
        }
        if (branch.getBusiness() != null && branch.getBusiness().getId() != null) {
            return branch.getBusiness().getId();
        }
        if (branch.getId() == null) {
            return null;
        }
        return branchRepository.findById(branch.getId())
                .map(loaded -> loaded.getBusiness() == null ? null : loaded.getBusiness().getId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<User> findOwner(Business business) {
        if (business == null || business.getOrganization() == null) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
