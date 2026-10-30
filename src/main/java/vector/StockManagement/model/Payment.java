package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import vector.StockManagement.model.enums.PaymentKind;
import vector.StockManagement.model.enums.PaymentMethod;

import java.math.BigDecimal;

/** A recorded payment. Never edited: a mistake is cancelled by a REVERSAL entry. */
@Entity
@Immutable
@Table(name = "payments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id")
    private Debt debt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    /** Negative for a reversal. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "reverses_payment_id")
    private Long reversesPaymentId;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;
}
