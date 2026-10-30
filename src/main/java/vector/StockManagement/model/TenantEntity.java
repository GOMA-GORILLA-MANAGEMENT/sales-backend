package vector.StockManagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import vector.StockManagement.config.TenantContext;

/** Base for every record that belongs to exactly one business (tenant). */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @PrePersist
    void assignTenant() {
        if (tenantId == null) {
            Long current = TenantContext.getTenantId();
            if (current == null || current == 0L) {
                throw new IllegalStateException("No business context for this record");
            }
            tenantId = current;
        }
    }
}
