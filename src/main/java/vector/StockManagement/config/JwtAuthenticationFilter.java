package vector.StockManagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.Role;
import vector.StockManagement.repositories.TenantRepository;
import vector.StockManagement.repositories.TokenRepository;

import java.io.IOException;

/**
 * Authenticates the Bearer token and sets the business (tenant) for the request.
 * A token stops working as soon as it is revoked, the person is disabled, or their business is suspended.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenRepository tokenRepository;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.equals("Bearer undefined")) return;
        String jwt = header.substring(7);
        try {
            String email = jwtService.extractUsername(jwt);
            if (email == null || SecurityContextHolder.getContext().getAuthentication() != null) return;
            if (!(userDetailsService.loadUserByUsername(email) instanceof User user)) return;
            if (!jwtService.isTokenValid(jwt, user) || !user.isEnabled()) return;
            boolean storedOk = tokenRepository.findByToken(jwt).map(t -> !t.isExpired() && !t.isRevoked()).orElse(false);
            if (!storedOk) return;

            if (user.getRole() == Role.SUPER_ADMIN) {
                TenantContext.setTenantId(0L);
            } else {
                if (user.getTenant() == null) return;
                Long tenantId = user.getTenant().getId();
                if (!Boolean.TRUE.equals(tenantRepository.isActive(tenantId))) return;
                TenantContext.setTenantId(tenantId);
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            logger.debug("Token rejected: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
