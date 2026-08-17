package com.hourslot.service;

import com.hourslot.model.MemberRole;
import com.hourslot.model.Role;
import com.hourslot.model.User;
import com.hourslot.model.UserRole;
import com.hourslot.repository.MemberRoleRepository;
import com.hourslot.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RbacService {

    private static final List<String> PRIORITY = List.of(
            "SUPER_ADMIN",
            "PLATFORM_ADMIN",
            "ORG_OWNER",
            "ORG_MANAGER",
            "BUSINESS_MANAGER",
            "BRANCH_MANAGER",
            "STAFF",
            "CUSTOMER"
    );

    private final MemberRoleRepository memberRoleRepository;
    private final RoleRepository roleRepository;

    public RbacService(MemberRoleRepository memberRoleRepository, RoleRepository roleRepository) {
        this.memberRoleRepository = memberRoleRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public UserRole resolveAppRole(Long userId) {
        return toAppRole(memberRoleRepository.findActiveByUserId(userId).stream()
                .map(mr -> mr.getRole().getCode())
                .toList());
    }

    public void attachAppRoles(Collection<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, List<MemberRole>> byUser = memberRoleRepository.findActiveByUserIdIn(ids).stream()
                .collect(Collectors.groupingBy(mr -> mr.getUser().getId()));
        for (User user : users) {
            List<String> codes = byUser.getOrDefault(user.getId(), List.of()).stream()
                    .map(mr -> mr.getRole().getCode())
                    .toList();
            user.setRole(toAppRole(codes));
        }
    }

    @Transactional
    public MemberRole grantSystemRole(User user, String roleCode,
                                      com.hourslot.model.Organization organization,
                                      com.hourslot.model.Business business,
                                      com.hourslot.model.Branch branch,
                                      com.hourslot.model.Staff staff) {
        Role role = roleRepository.findByCodeAndSystemTrue(roleCode)
                .orElseThrow(() -> new IllegalStateException("System role not seeded: " + roleCode));
        MemberRole assignment = MemberRole.builder()
                .user(user)
                .role(role)
                .organization(organization)
                .business(business)
                .branch(branch)
                .staff(staff)
                .build();
        return memberRoleRepository.save(assignment);
    }

    public static UserRole toAppRole(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return UserRole.CUSTOMER;
        }
        String highest = codes.stream()
                .min(Comparator.comparingInt(code -> {
                    int idx = PRIORITY.indexOf(code);
                    return idx < 0 ? PRIORITY.size() : idx;
                }))
                .orElse("CUSTOMER");
        return switch (highest) {
            case "SUPER_ADMIN" -> UserRole.SUPER_ADMIN;
            case "PLATFORM_ADMIN" -> UserRole.ADMIN;
            case "ORG_OWNER", "ORG_MANAGER", "BUSINESS_MANAGER", "BRANCH_MANAGER" -> UserRole.BUSINESS_OWNER;
            case "STAFF" -> UserRole.BUSINESS_STAFF;
            default -> UserRole.CUSTOMER;
        };
    }
}
