package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import vector.StockManagement.model.enums.MovementType;
import vector.StockManagement.model.enums.UnitType;

import java.math.BigDecimal;

/** Append-only ledger of every stock change; keeps both the entered and the converted quantity. */
@Entity
@Immutable
@Table(name = "stock_movements")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(name = "entered_quantity", nullable = false)
    private long enteredQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "entered_unit", nullable = false)
    private UnitType enteredUnit;

    @Column(name = "units_per_pack_used", nullable = false)
    private int unitsPerPackUsed = 1;

    /** Signed change in the smallest unit. */
    @Column(name = "base_quantity", nullable = false)
    private long baseQuantity;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    private String reason;
    private String reference;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;
}
