package com.hourslot.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.services.TenancyService;
import com.hourslot.shared.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Blocks individual (solo) providers from /api/business/** so they must use /api/provider/**.
 */
@Component
public class BusinessWorkspaceFilter extends OncePerRequestFilter {

    private final TenancyService tenancyService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public BusinessWorkspaceFilter(
            TenancyService tenancyService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.tenancyService = tenancyService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/business")) {
            return true;
        }
        // Public slug lookup stays open
        return path.startsWith("/api/business/profile-by-slug");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> user = userRepository.findById(details.getId());
        if (user.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Business> business = tenancyService.findBusinessForUser(user.get());
        if (business.isPresent() && TenancyService.isIndividual(business.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                    "SOLO_WORKSPACE_REQUIRED",
                    "Individual providers must use the solo workspace (/api/provider). Business APIs are not available."));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
