package vector.StockManagement.config;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class TenantFilterAspect {
    private static final Logger logger = LoggerFactory.getLogger(TenantFilterAspect.class);

    private final HibernateTenantFilterConfiguration config;

    // Whitelisted endpoints that don't require tenant filtering
    private static final List<String> REPOSITORY_WHITELIST = Arrays.asList(
            "/api/auth/",
            "/api/tenants/admin",
            "/error",
            "/v3/api-docs",
            "/swagger-ui",
            "/actuator"
    );

    public TenantFilterAspect(HibernateTenantFilterConfiguration config) {
        this.config = config;
    }

    // Enable filter before any repository method is executed
    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void applyTenantFilter() {
        try {
            // Check if the current request is whitelisted
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String requestUri = attributes.getRequest().getRequestURI();
                logger.debug("TenantFilterAspect before repository call for URI: {} with tenantId: {}", requestUri, TenantContext.getTenantId());
                
                if (isWhitelisted(requestUri)) {
                    logger.debug("Skipping tenant filter for whitelisted repository access: {}", requestUri);
                    return;
                }
                
                logger.debug("Applying tenant filter for repository access: {}", requestUri);
            } else {
                logger.debug("No request context available, applying tenant filter by default");
            }

            config.enableTenantFilter();
        } catch (Exception e) {
            logger.error("Error in tenant filter aspect: {}", e.getMessage(), e);
            // Don't rethrow to avoid breaking repository operations
        }
    }

    private boolean isWhitelisted(String requestUri) {
        return REPOSITORY_WHITELIST.stream().anyMatch(requestUri::startsWith);
    }
}
