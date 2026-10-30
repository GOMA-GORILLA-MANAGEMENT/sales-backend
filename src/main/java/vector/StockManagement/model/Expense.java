package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money spent running an outlet (rent, salaries, electricity...). Never deleted: a mistake is voided with a reason. */
@Entity
@Table(name = "expenses")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Expense extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @Column(nullable = false)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(nullable = false)
    private boolean voided;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by_name")
    private String voidedByName;
}
