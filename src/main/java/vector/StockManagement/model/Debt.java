package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import vector.StockManagement.model.enums.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Debt extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Total of discounts, write-offs and corrections applied to this debt. */
    @Column(name = "adjusted_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status = DebtStatus.OPEN;

    @Column(name = "responsible_id")
    private Long responsibleId;

    @Column(name = "responsible_name")
    private String responsibleName;

    public BigDecimal getOutstanding() {
        return originalAmount.subtract(paidAmount).subtract(adjustedAmount);
    }

    public boolean isOverdue(LocalDate today) {
        return getOutstanding().signum() > 0 && dueDate.isBefore(today);
    }
}
