package com.duemet.web.security;

import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.organization.services.RbacService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.duemet.identity.security.CustomUserDetails;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RbacService rbacService;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        user.setRole(rbacService.resolveAppRole(user.getId()));
        return CustomUserDetails.build(user);
    }
}
