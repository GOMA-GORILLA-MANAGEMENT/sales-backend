package vector.StockManagement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantContext {
    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
        logger.debug("Set tenant context to ID: {}", tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        logger.debug("Retrieved tenant context ID: {}", tenantId);
        return tenantId;
    }

    public static void clear() {
        Long tenantId = CURRENT_TENANT.get();
        CURRENT_TENANT.remove();
        logger.debug("Cleared tenant context (was: {})", tenantId);
    }
}
