package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import vector.StockManagement.model.enums.AdjustmentType;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "debt_adjustments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class DebtAdjustment extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id")
    private Debt debt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentType type;

    /** Positive reduces what is owed; negative raises it again (corrections only). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name")
    private String actorName;
}
