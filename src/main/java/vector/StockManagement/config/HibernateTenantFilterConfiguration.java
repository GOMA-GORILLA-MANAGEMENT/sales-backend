package vector.StockManagement.config;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantFilterConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(HibernateTenantFilterConfiguration.class);

    private final EntityManager entityManager;

    public HibernateTenantFilterConfiguration(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();
        logger.debug("Attempting to enable tenant filter with tenantId={}", tenantId);
        
        if (tenantId == null) {
            logger.debug("No business context on this request; tenant filter not applied");
            return; // Skip enabling when unknown; controllers/services should ensure it's set
        }
        // For SUPER_ADMIN/global access, use 0L to indicate no tenant restriction
        if (tenantId == 0L) {
            logger.debug("Tenant ID is 0 (SUPER_ADMIN/global), skipping tenant filter for global access");
            return;
        }

        try {
            Session session = entityManager.unwrap(Session.class);
            
            // Check if filter is already enabled to avoid duplicate enabling
            if (session.getEnabledFilter("tenantFilter") == null) {
                var filter = session.enableFilter("tenantFilter");
                filter.setParameter("tenantId", tenantId);
                logger.info("Successfully enabled tenant filter with tenantId: {}", tenantId);
            } else {
                logger.debug("Tenant filter already enabled for tenantId: {}", tenantId);
            }
        } catch (Exception e) {
            logger.error("TENANT FILTER CRITICAL ERROR - Failed to enable tenant filter for tenantId: {} | Error: {}", 
                        tenantId, e.getMessage(), e);
            // Don't throw exception to prevent breaking the application, but log as critical
        }
    }
    
    public void disableTenantFilter() {
        try {
            Session session = entityManager.unwrap(Session.class);
            session.disableFilter("tenantFilter");
            logger.debug("Disabled tenant filter");
        } catch (Exception e) {
            logger.error("Failed to disable tenant filter: {}", e.getMessage(), e);
        }
    }
}
