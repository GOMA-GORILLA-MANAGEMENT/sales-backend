package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import vector.StockManagement.model.enums.OrderStatus;
import vector.StockManagement.model.enums.PaymentMethod;
import vector.StockManagement.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Order extends TenantEntity {

    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "table_label")
    private String tableLabel;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "cost_of_goods", nullable = false, precision = 19, scale = 2)
    private BigDecimal costOfGoods = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "created_by_id", nullable = false)
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_by_name")
    private String decidedByName;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "delivered_by_id")
    private Long deliveredById;

    @Column(name = "delivered_by_name")
    private String deliveredByName;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();

    public void addLine(OrderLine line) {
        line.setOrder(this);
        lines.add(line);
    }
}
