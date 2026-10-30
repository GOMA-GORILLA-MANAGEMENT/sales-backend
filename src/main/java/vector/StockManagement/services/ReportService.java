package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.*;
import vector.StockManagement.model.dto.Views.ReportTable;
import vector.StockManagement.model.enums.*;
import vector.StockManagement.repositories.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Tabular reports. The same table feeds the on-screen view, the PDF and the spreadsheet export. */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final List<String> TYPES = List.of("sales", "products", "stock", "movements", "reconciliation",
            "debts", "aging", "payments", "expenses", "audit");

    private final OrderRepository orderRepository;
    private final OrderLineRepository lineRepository;
    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final StockMovementRepository movementRepository;
    private final StockLevelRepository levelRepository;
    private final AuditService auditService;
    private final ExpenseRepository expenseRepository;
    private final Access access;

    @Transactional(readOnly = true)
    public ReportTable build(String type, LocalDate from, LocalDate to, Long outletId) {
        access.require(Permission.REPORT_VIEW);
        Long tenantId = access.tenantId();
        Set<Long> scope = access.scopeOutlets(outletId);
        LocalDate start = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDateTime t0 = start.atStartOfDay();
        LocalDateTime t1 = end.plusDays(1).atStartOfDay();
        String period = start + " to " + end;

        return switch (type) {
            case "sales" -> sales(tenantId, scope, t0, t1, period);
            case "products" -> products(tenantId, scope, t0, t1, period);
            case "stock" -> stock(tenantId, scope);
            case "movements" -> movements(tenantId, scope, t0, t1, period);
            case "reconciliation" -> reconciliation(tenantId, scope, t0, t1, period);
            case "debts" -> debts(tenantId, scope);
            case "aging" -> aging(tenantId, scope);
            case "payments" -> payments(tenantId, scope, t0, t1, period);
            case "expenses" -> expenses(tenantId, scope, start, end, period);
            case "audit" -> audit(start, end);
            default -> throw ApiException.notFound("Unknown report");
        };
    }

    private ReportTable sales(Long tenantId, Set<Long> scope, LocalDateTime t0, LocalDateTime t1, String period) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, cogs = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        for (Order o : orderRepository.deliveredInPeriod(tenantId, scope, t0, t1)) {
            rows.add(row(o.getDeliveredAt().toLocalDate(), o.getCode(), o.getOutlet().getName(), o.getCreatedByName(),
                    o.getCustomer() == null ? "" : o.getCustomer().getName(), o.getPaymentMethod(), o.getTotalAmount(),
                    o.getCostOfGoods(), o.getAmountPaid(), o.getPaymentStatus()));
            total = total.add(o.getTotalAmount());
            cogs = cogs.add(o.getCostOfGoods());
            paid = paid.add(o.getAmountPaid());
        }
        return new ReportTable("Sales " + period, List.of("Date", "Order", "Outlet", "Waiter", "Customer", "Payment method",
                "Total", "Cost of goods", "Paid", "Payment status"), rows, row("Total", "", "", "", "", "", total, cogs, paid, ""));
    }

    private ReportTable products(Long tenantId, Set<Long> scope, LocalDateTime t0, LocalDateTime t1, String period) {
        Map<String, BigDecimal[]> agg = new TreeMap<>();
        for (OrderLine l : lineRepository.deliveredLines(tenantId, scope, t0, t1)) {
            BigDecimal[] a = agg.computeIfAbsent(l.getItemName(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(BigDecimal.valueOf(l.getQuantity()));
            a[1] = a[1].add(l.getLineTotal());
            if (l.getUnitCost() != null) a[2] = a[2].add(l.getUnitCost().multiply(BigDecimal.valueOf(l.getBaseQuantity())));
        }
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal qty = BigDecimal.ZERO, rev = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (var e : agg.entrySet()) {
            rows.add(row(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2].setScale(2, java.math.RoundingMode.HALF_UP)));
            qty = qty.add(e.getValue()[0]);
            rev = rev.add(e.getValue()[1]);
            cost = cost.add(e.getValue()[2]);
        }
        return new ReportTable("Product performance " + period, List.of("Item", "Quantity sold", "Revenue", "Cost of goods"),
                rows, row("Total", qty, rev, cost.setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    private ReportTable stock(Long tenantId, Set<Long> scope) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal value = BigDecimal.ZERO;
        for (StockLevel s : levelRepository.findAllFor(tenantId, scope)) {
            if (!s.getVariant().isTracked()) continue;
            BigDecimal v = s.getAvgUnitCost().multiply(BigDecimal.valueOf(s.getQuantity())).setScale(2, java.math.RoundingMode.HALF_UP);
            rows.add(row(s.getOutlet().getName(), s.getVariant().displayName(),
                    vector.StockManagement.model.dto.Views.displayQuantity(s.getQuantity(), s.getVariant()),
                    s.getQuantity(), s.getAvgUnitCost(), v,
                    s.getQuantity() <= (s.getVariant().getLowStockThreshold() == null ? 0 : s.getVariant().getLowStockThreshold()) ? "LOW" : ""));
            value = value.add(v);
        }
        return new ReportTable("Stock balances", List.of("Outlet", "Item", "In stock", "Smallest units", "Unit cost", "Value", "Alert"),
                rows, row("Total", "", "", "", "", value, ""));
    }

    private ReportTable movements(Long tenantId, Set<Long> scope, LocalDateTime t0, LocalDateTime t1, String period) {
        List<List<Object>> rows = new ArrayList<>();
        for (StockMovement m : movementRepository.findInPeriod(tenantId, scope, t0, t1)) {
            rows.add(row(m.getCreatedAt().toString().replace('T', ' '), m.getOutlet().getName(), m.getVariant().displayName(),
                    m.getType(), m.getEnteredQuantity() + " " + m.getEnteredUnit().name().toLowerCase(),
                    m.getUnitsPerPackUsed(), m.getBaseQuantity(), m.getBalanceAfter(),
                    m.getReason() == null ? "" : m.getReason(), m.getReference() == null ? "" : m.getReference(),
                    m.getCreatedByName() == null ? "" : m.getCreatedByName()));
        }
        return new ReportTable("Stock movements " + period, List.of("When", "Outlet", "Item", "Type", "Entered", "Units per pack",
                "Change (smallest unit)", "Balance after", "Reason", "Reference", "By"), rows, null);
    }

    private ReportTable reconciliation(Long tenantId, Set<Long> scope, LocalDateTime t0, LocalDateTime t1, String period) {
        Map<String, long[]> sums = new TreeMap<>();
        for (StockMovement m : movementRepository.findInPeriod(tenantId, scope, t0, t1)) {
            long[] a = sums.computeIfAbsent(m.getOutlet().getName() + "\u0000" + m.getVariant().displayName(), k -> new long[6]);
            switch (m.getType()) {
                case RECEIVE -> a[0] += m.getBaseQuantity();
                case SALE -> a[1] += m.getBaseQuantity();
                case TRANSFER_IN, TRANSFER_OUT -> a[2] += m.getBaseQuantity();
                case BREAKAGE, WASTE -> a[3] += m.getBaseQuantity();
                case RETURN -> a[4] += m.getBaseQuantity();
                case CORRECTION -> a[5] += m.getBaseQuantity();
            }
        }
        Map<String, Long> balances = new HashMap<>();
        levelRepository.findAllFor(tenantId, scope).forEach(s ->
                balances.put(s.getOutlet().getName() + "\u0000" + s.getVariant().displayName(), s.getQuantity()));
        List<List<Object>> rows = new ArrayList<>();
        for (var e : sums.entrySet()) {
            long[] a = e.getValue();
            String[] parts = e.getKey().split("\u0000");
            long net = a[0] + a[1] + a[2] + a[3] + a[4] + a[5];
            long closing = balances.getOrDefault(e.getKey(), 0L);
            rows.add(row(parts[0], parts[1], closing - net, a[0], a[1], a[2], a[3], a[4], a[5], closing));
        }
        return new ReportTable("Stock reconciliation " + period, List.of("Outlet", "Item", "Opening", "Received", "Sold",
                "Transfers (net)", "Breakage and waste", "Returns", "Corrections", "Closing"), rows, null);
    }

    private ReportTable debts(Long tenantId, Set<Long> scope) {
        LocalDate today = LocalDate.now();
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal original = BigDecimal.ZERO, paid = BigDecimal.ZERO, outstanding = BigDecimal.ZERO;
        for (Debt d : debtRepository.search(tenantId, scope, null)) {
            if (d.getOutstanding().signum() <= 0) continue;
            long late = d.isOverdue(today) ? ChronoUnit.DAYS.between(d.getDueDate(), today) : 0;
            rows.add(row(d.getCustomer().getName(), d.getOutlet() == null ? "" : d.getOutlet().getName(),
                    d.getOrder() == null ? "" : d.getOrder().getCode(), d.getOriginalAmount(), d.getPaidAmount(),
                    d.getAdjustedAmount(), d.getOutstanding(), d.getDueDate(), late, d.getResponsibleName()));
            original = original.add(d.getOriginalAmount());
            paid = paid.add(d.getPaidAmount());
            outstanding = outstanding.add(d.getOutstanding());
        }
        return new ReportTable("Customer debts", List.of("Customer", "Outlet", "Order", "Original", "Paid", "Adjusted",
                "Outstanding", "Due", "Days late", "Responsible"), rows, row("Total", "", "", original, paid, "", outstanding, "", "", ""));
    }

    private ReportTable aging(Long tenantId, Set<Long> scope) {
        List<Map<String, Object>> buckets = DashboardService.aging(debtRepository.search(tenantId, scope, null), LocalDate.now());
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var b : buckets) {
            rows.add(row(b.get("bucket"), b.get("debts"), b.get("amount")));
            total = total.add((BigDecimal) b.get("amount"));
        }
        return new ReportTable("Debt aging", List.of("Age", "Debts", "Amount"), rows, row("Total", "", total));
    }

    private ReportTable payments(Long tenantId, Set<Long> scope, LocalDateTime t0, LocalDateTime t1, String period) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : paymentRepository.findInPeriod(tenantId, scope, t0, t1)) {
            rows.add(row(p.getCreatedAt().toString().replace('T', ' '), p.getReceiptNumber(), p.getKind(),
                    p.getCustomer() == null ? "" : p.getCustomer().getName(), p.getOutlet() == null ? "" : p.getOutlet().getName(),
                    p.getMethod(), p.getAmount(), p.getCreatedByName()));
            total = total.add(p.getAmount());
        }
        return new ReportTable("Payments and receipts " + period, List.of("When", "Receipt", "Kind", "Customer", "Outlet",
                "Method", "Amount", "Recorded by"), rows, row("Net", "", "", "", "", "", total, ""));
    }

    private ReportTable expenses(Long tenantId, Set<Long> scope, LocalDate from, LocalDate to, String period) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : expenseRepository.findInPeriod(tenantId, scope, from, to)) {
            rows.add(row(e.getExpenseDate(), e.getOutlet().getName(), e.getCategory(), e.getDescription() == null ? "" : e.getDescription(),
                    e.isVoided() ? "VOIDED: " + e.getVoidReason() : "", e.getAmount(), e.getCreatedByName()));
            if (!e.isVoided()) total = total.add(e.getAmount());
        }
        return new ReportTable("Expenses " + period, List.of("Date", "Outlet", "Category", "Description", "Note", "Amount", "Recorded by"),
                rows, row("Total (not voided)", "", "", "", "", total, ""));
    }

    private ReportTable audit(LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        for (AuditLog a : auditService.search(from, to, null)) {
            rows.add(row(a.getSeq(), a.getCreatedAt().toString().replace('T', ' '), a.getActorEmail() == null ? "" : a.getActorEmail(),
                    a.getActorRole() == null ? "" : a.getActorRole(), a.getAction(), a.getReference() == null ? "" : a.getReference(),
                    a.getReason() == null ? "" : a.getReason(), a.getHash()));
        }
        return new ReportTable("Audit trail " + from + " to " + to, List.of("#", "When", "User", "Role", "Action",
                "Reference", "Reason", "Hash"), rows, null);
    }

    private static List<Object> row(Object... cells) {
        return new ArrayList<>(Arrays.asList(cells));
    }
}
