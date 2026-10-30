package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_levels", uniqueConstraints = @UniqueConstraint(columnNames = {"outlet_id", "variant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class StockLevel extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    /** Quantity in the smallest unit. */
    @Column(nullable = false)
    private long quantity;

    /** Weighted average cost of one smallest unit. */
    @Column(name = "avg_unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgUnitCost = BigDecimal.ZERO;
}
