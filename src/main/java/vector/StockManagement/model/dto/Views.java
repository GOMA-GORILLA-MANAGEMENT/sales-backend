package vector.StockManagement.model.dto;

import vector.StockManagement.model.*;
import vector.StockManagement.model.enums.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes, so entities are never serialised directly. */
public final class Views {
    private Views() {}

    public record OutletView(Long id, String name, OutletType type, OutletStatus status, String address, String phone) {}

    public record StaffView(Long id, String firstName, String lastName, String fullName, String email, String phone,
                            Role role, UserStatus status, List<Long> outletIds, List<String> outletNames) {}

    public record MeView(Long id, String firstName, String lastName, String fullName, String email, Role role,
                         List<String> permissions, Long tenantId, String businessName, boolean approvalRequired,
                         boolean allowNegativeStock, List<OutletView> outlets) {}

    public record VariantView(Long id, String label, BigDecimal sellPrice, String baseUnitName, String packUnitName,
                              Integer unitsPerPack, BigDecimal packPrice, boolean tracked, Long lowStockThreshold,
                              boolean active) {}

    public record ProductView(Long id, String name, String category, String description, boolean hasSizes,
                              boolean active, List<VariantView> variants) {}

    public record StockRow(Long outletId, String outletName, Long variantId, String productName, String label,
                           long quantity, long packs, long remainder, String display, String baseUnitName,
                           String packUnitName, Integer unitsPerPack, BigDecimal avgUnitCost, BigDecimal value,
                           long lowStockThreshold, boolean low) {}

    public record MovementView(Long id, LocalDateTime at, String outletName, String itemName, MovementType type,
                               long enteredQuantity, UnitType enteredUnit, int unitsPerPackUsed, long baseQuantity,
                               long balanceAfter, BigDecimal unitCost, String reason, String reference,
                               String byName) {}

    public record CustomerView(Long id, String name, String phone, String email, String idNumber,
                               BigDecimal creditLimit, int creditDays, boolean active, BigDecimal outstanding) {}

    public record OrderLineView(Long id, Long variantId, String itemName, UnitType unitType, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal, long baseQuantity) {}

    public record HistoryView(OrderStatus from, OrderStatus to, String byName, String reason, LocalDateTime at) {}

    public record OrderView(Long id, String code, Long outletId, String outletName, Long customerId,
                            String customerName, OrderStatus status, PaymentStatus paymentStatus,
                            PaymentMethod paymentMethod, String tableLabel, BigDecimal totalAmount,
                            BigDecimal costOfGoods, BigDecimal amountPaid, String receiptNumber, Long createdById,
                            String createdByName, String decidedByName, String decisionReason,
                            String deliveredByName, LocalDateTime deliveredAt, LocalDateTime createdAt,
                            List<OrderLineView> lines, List<HistoryView> history) {}

    public record DebtView(Long id, Long customerId, String customerName, Long orderId, String orderCode,
                           Long outletId, String outletName, BigDecimal originalAmount, BigDecimal paidAmount,
                           BigDecimal adjustedAmount, BigDecimal outstanding, LocalDate dueDate, DebtStatus status,
                           boolean overdue, long daysOverdue, String responsibleName, LocalDateTime createdAt) {}

    public record PaymentView(Long id, PaymentKind kind, BigDecimal amount, PaymentMethod method, String receiptNumber,
                              Long debtId, Long orderId, String customerName, String outletName, Long reversesPaymentId,
                              boolean reversed, String reason, String byName, LocalDateTime at) {}

    public record AdjustmentView(Long id, AdjustmentType type, BigDecimal amount, String reason, String byName,
                                 LocalDateTime at) {}

    public record DebtDetail(DebtView debt, List<PaymentView> payments, List<AdjustmentView> adjustments) {}

    public record AuditView(Long id, long seq, LocalDateTime at, String actorEmail, String actorRole, Long outletId,
                            String action, String entityType, String entityId, String reference, String previousValue,
                            String newValue, String reason, String requestId, String hash) {}

    public record ExpenseView(Long id, Long outletId, String outletName, String category, String description,
                              BigDecimal amount, LocalDate expenseDate, String byName, boolean voided, String voidReason,
                              String voidedByName, LocalDateTime createdAt) {}

    public record ReportTable(String title, List<String> headers, List<List<Object>> rows, List<Object> totals) {}

    // ---- mapping helpers ----

    public static OutletView of(Outlet o) {
        return new OutletView(o.getId(), o.getName(), o.getType(), o.getStatus(), o.getAddress(), o.getPhone());
    }

    public static ExpenseView of(Expense e) {
        return new ExpenseView(e.getId(), e.getOutlet().getId(), e.getOutlet().getName(), e.getCategory(), e.getDescription(),
                e.getAmount(), e.getExpenseDate(), e.getCreatedByName(), e.isVoided(), e.getVoidReason(),
                e.getVoidedByName(), e.getCreatedAt());
    }

    public static VariantView of(ProductVariant v) {
        return new VariantView(v.getId(), v.getLabel(), v.getSellPrice(), v.getBaseUnitName(), v.getPackUnitName(),
                v.getUnitsPerPack(), v.getPackPrice(), v.isTracked(), v.getLowStockThreshold(), v.isActive());
    }

    public static ProductView of(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getCategory(), p.getDescription(), p.isHasSizes(),
                p.isActive(), p.getVariants().stream().map(Views::of).toList());
    }

    public static CustomerView of(Customer c, BigDecimal outstanding) {
        return new CustomerView(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getIdNumber(),
                c.getCreditLimit(), c.getCreditDays(), c.isActive(), outstanding);
    }

    public static String displayQuantity(long quantity, ProductVariant v) {
        String base = v.getBaseUnitName();
        if (!v.hasPack()) return quantity + " " + plural(base, quantity);
        long packs = quantity / v.getUnitsPerPack();
        long rest = quantity % v.getUnitsPerPack();
        if (packs == 0) return rest + " " + plural(base, rest);
        if (rest == 0) return packs + " " + plural(v.getPackUnitName(), packs);
        return packs + " " + plural(v.getPackUnitName(), packs) + " + " + rest + " " + plural(base, rest);
    }

    private static String plural(String word, long n) {
        if (n == 1 || word == null) return word;
        return word.endsWith("s") ? word : word + "s";
    }

    public static StockRow of(StockLevel s) {
        ProductVariant v = s.getVariant();
        long upp = v.hasPack() ? v.getUnitsPerPack() : 1;
        long packs = v.hasPack() ? s.getQuantity() / upp : 0;
        long rest = v.hasPack() ? s.getQuantity() % upp : s.getQuantity();
        long threshold = v.getLowStockThreshold() == null ? 0 : v.getLowStockThreshold();
        return new StockRow(s.getOutlet().getId(), s.getOutlet().getName(), v.getId(), v.getProduct().getName(),
                v.getProduct().isHasSizes() ? v.getLabel() : null, s.getQuantity(), packs, rest,
                displayQuantity(s.getQuantity(), v), v.getBaseUnitName(), v.getPackUnitName(), v.getUnitsPerPack(),
                s.getAvgUnitCost(), s.getAvgUnitCost().multiply(BigDecimal.valueOf(s.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP),
                threshold, v.isTracked() && s.getQuantity() <= threshold);
    }

    public static MovementView of(StockMovement m) {
        return new MovementView(m.getId(), m.getCreatedAt(), m.getOutlet().getName(), m.getVariant().displayName(),
                m.getType(), m.getEnteredQuantity(), m.getEnteredUnit(), m.getUnitsPerPackUsed(), m.getBaseQuantity(),
                m.getBalanceAfter(), m.getUnitCost(), m.getReason(), m.getReference(), m.getCreatedByName());
    }

    public static AuditView of(AuditLog a) {
        return new AuditView(a.getId(), a.getSeq(), a.getCreatedAt(), a.getActorEmail(), a.getActorRole(),
                a.getOutletId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getReference(),
                a.getPreviousValue(), a.getNewValue(), a.getReason(), a.getRequestId(), a.getHash());
    }

    public static OrderView of(Order o, List<OrderStatusHistory> history) {
        return new OrderView(o.getId(), o.getCode(), o.getOutlet().getId(), o.getOutlet().getName(),
                o.getCustomer() == null ? null : o.getCustomer().getId(),
                o.getCustomer() == null ? null : o.getCustomer().getName(), o.getStatus(), o.getPaymentStatus(),
                o.getPaymentMethod(), o.getTableLabel(), o.getTotalAmount(), o.getCostOfGoods(), o.getAmountPaid(),
                o.getReceiptNumber(), o.getCreatedById(), o.getCreatedByName(), o.getDecidedByName(),
                o.getDecisionReason(), o.getDeliveredByName(), o.getDeliveredAt(), o.getCreatedAt(),
                o.getLines().stream().map(l -> new OrderLineView(l.getId(), l.getVariant().getId(), l.getItemName(),
                        l.getUnitType(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal(), l.getBaseQuantity())).toList(),
                history == null ? List.of() : history.stream().map(h -> new HistoryView(h.getFromStatus(),
                        h.getToStatus(), h.getActorName(), h.getReason(), h.getAt())).toList());
    }

    public static DebtView of(Debt d, LocalDate today) {
        boolean overdue = d.isOverdue(today);
        long days = overdue ? java.time.temporal.ChronoUnit.DAYS.between(d.getDueDate(), today) : 0;
        return new DebtView(d.getId(), d.getCustomer().getId(), d.getCustomer().getName(),
                d.getOrder() == null ? null : d.getOrder().getId(), d.getOrder() == null ? null : d.getOrder().getCode(),
                d.getOutlet() == null ? null : d.getOutlet().getId(),
                d.getOutlet() == null ? null : d.getOutlet().getName(), d.getOriginalAmount(), d.getPaidAmount(),
                d.getAdjustedAmount(), d.getOutstanding(), d.getDueDate(), d.getStatus(), overdue, days,
                d.getResponsibleName(), d.getCreatedAt());
    }

    public static PaymentView of(Payment p, boolean reversed) {
        return new PaymentView(p.getId(), p.getKind(), p.getAmount(), p.getMethod(), p.getReceiptNumber(),
                p.getDebt() == null ? null : p.getDebt().getId(), p.getOrder() == null ? null : p.getOrder().getId(),
                p.getCustomer() == null ? null : p.getCustomer().getName(),
                p.getOutlet() == null ? null : p.getOutlet().getName(), p.getReversesPaymentId(), reversed,
                p.getReason(), p.getCreatedByName(), p.getCreatedAt());
    }
}
