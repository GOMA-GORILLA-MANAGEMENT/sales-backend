package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.model.*;
import vector.StockManagement.model.enums.*;
import vector.StockManagement.repositories.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository lineRepository;
    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final DebtAdjustmentRepository adjustmentRepository;
    private final StockMovementRepository movementRepository;
    private final StockLevelRepository levelRepository;
    private final ExpenseRepository expenseRepository;
    private final Access access;

    /** Business-wide numbers for a period, for all outlets the caller may see or just one. */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(LocalDate from, LocalDate to, Long outletId) {
        access.require(Permission.DASHBOARD_VIEW);
        Long tenantId = access.tenantId();
        Set<Long> scope = access.scopeOutlets(outletId);
        LocalDate start = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDateTime t0 = start.atStartOfDay();
        LocalDateTime t1 = end.plusDays(1).atStartOfDay();
        LocalDate today = LocalDate.now();

        List<Order> delivered = orderRepository.deliveredInPeriod(tenantId, scope, t0, t1);
        List<Order> created = orderRepository.createdInPeriod(tenantId, scope, t0, t1);
        List<OrderLine> lines = lineRepository.deliveredLines(tenantId, scope, t0, t1);
        List<Payment> payments = paymentRepository.findInPeriod(tenantId, scope, t0, t1);
        List<Debt> debts = debtRepository.search(tenantId, scope, null);
        List<DebtAdjustment> adjustments = adjustmentRepository.findInPeriod(tenantId, t0, t1).stream()
                .filter(a -> a.getDebt().getOutlet() == null || scope.contains(a.getDebt().getOutlet().getId())).toList();
        List<StockMovement> movements = movementRepository.findInPeriod(tenantId, scope, t0, t1);
        List<StockLevel> levels = levelRepository.findAllFor(tenantId, scope);
        List<Expense> spent = expenseRepository.findInPeriod(tenantId, scope, start, end).stream().filter(e -> !e.isVoided()).toList();

        BigDecimal revenue = sum(delivered, Order::getTotalAmount);
        BigDecimal cogs = sum(delivered, Order::getCostOfGoods);
        BigDecimal collections = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debtOutstanding = BigDecimal.ZERO, debtOverdue = BigDecimal.ZERO;
        for (Debt d : debts) {
            debtOutstanding = debtOutstanding.add(d.getOutstanding());
            if (d.isOverdue(today)) debtOverdue = debtOverdue.add(d.getOutstanding());
        }
        BigDecimal stockValue = levels.stream().filter(s -> s.getVariant().isTracked())
                .map(s -> s.getAvgUnitCost().multiply(BigDecimal.valueOf(Math.max(0, s.getQuantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        long lowStock = levels.stream().filter(s -> s.getVariant().isTracked()
                && s.getQuantity() <= (s.getVariant().getLowStockThreshold() == null ? 0 : s.getVariant().getLowStockThreshold())).count();
        BigDecimal discounts = adjustments.stream().filter(a -> a.getType() == AdjustmentType.DISCOUNT)
                .map(DebtAdjustment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal writeOffs = adjustments.stream().filter(a -> a.getType() == AdjustmentType.WRITE_OFF)
                .map(DebtAdjustment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Order> voided = created.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.REJECTED).toList();
        BigDecimal lossValue = movements.stream()
                .filter(m -> m.getType() == MovementType.BREAKAGE || m.getType() == MovementType.WASTE)
                .map(m -> (m.getUnitCost() == null ? BigDecimal.ZERO : m.getUnitCost()).multiply(BigDecimal.valueOf(Math.abs(m.getBaseQuantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start);
        out.put("to", end);
        out.put("outletId", outletId);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("revenue", revenue);
        kpis.put("costOfGoodsSold", cogs);
        kpis.put("orders", delivered.size());
        kpis.put("averageOrder", delivered.isEmpty() ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(delivered.size()), 2, RoundingMode.HALF_UP));
        kpis.put("collections", collections);
        kpis.put("expenses", sum(spent, Expense::getAmount));
        kpis.put("stockValue", stockValue);
        kpis.put("lowStockItems", lowStock);
        kpis.put("debtOutstanding", debtOutstanding);
        kpis.put("debtOverdue", debtOverdue);
        kpis.put("discountsGiven", discounts);
        kpis.put("writeOffs", writeOffs);
        kpis.put("voidedOrders", voided.size());
        kpis.put("voidedValue", sum(voided, Order::getTotalAmount));
        kpis.put("breakageAndWasteValue", lossValue);
        out.put("kpis", kpis);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), 0L);
        created.forEach(o -> byStatus.merge(o.getStatus().name(), 1L, Long::sum));
        out.put("ordersByStatus", byStatus);

        Map<String, Map<String, Object>> byMethod = new LinkedHashMap<>();
        for (Order o : delivered) {
            Map<String, Object> m = byMethod.computeIfAbsent(o.getPaymentMethod().name(),
                    k -> new LinkedHashMap<>(Map.of("method", k, "orders", 0L, "amount", BigDecimal.ZERO)));
            m.put("orders", (Long) m.get("orders") + 1);
            m.put("amount", ((BigDecimal) m.get("amount")).add(o.getTotalAmount()));
        }
        out.put("ordersByPaymentMethod", byMethod.values());

        Map<Long, Map<String, Object>> byOutlet = new LinkedHashMap<>();
        for (Order o : delivered) {
            Map<String, Object> m = byOutlet.computeIfAbsent(o.getOutlet().getId(), k -> outletRow(o.getOutlet()));
            m.put("orders", (Long) m.get("orders") + 1);
            m.put("revenue", ((BigDecimal) m.get("revenue")).add(o.getTotalAmount()));
            m.put("costOfGoods", ((BigDecimal) m.get("costOfGoods")).add(o.getCostOfGoods()));
        }
        for (Debt d : debts) {
            if (d.getOutlet() == null || d.getOutstanding().signum() <= 0) continue;
            Map<String, Object> m = byOutlet.computeIfAbsent(d.getOutlet().getId(), k -> outletRow(d.getOutlet()));
            m.put("debt", ((BigDecimal) m.get("debt")).add(d.getOutstanding()));
        }
        out.put("outlets", byOutlet.values());

        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        Map<LocalDate, Long> dailyCount = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            daily.put(d, BigDecimal.ZERO);
            dailyCount.put(d, 0L);
        }
        for (Order o : delivered) {
            LocalDate day = o.getDeliveredAt().toLocalDate();
            daily.merge(day, o.getTotalAmount(), BigDecimal::add);
            dailyCount.merge(day, 1L, Long::sum);
        }
        out.put("daily", daily.entrySet().stream().map(e -> Map.of("date", e.getKey().toString(),
                "revenue", e.getValue(), "orders", dailyCount.get(e.getKey()))).toList());

        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        for (OrderLine l : lines) {
            Map<String, Object> m = products.computeIfAbsent(l.getItemName(),
                    k -> new LinkedHashMap<>(Map.of("name", k, "quantity", 0L, "revenue", BigDecimal.ZERO)));
            m.put("quantity", (Long) m.get("quantity") + l.getQuantity());
            m.put("revenue", ((BigDecimal) m.get("revenue")).add(l.getLineTotal()));
        }
        out.put("bestSellers", products.values().stream()
                .sorted((a, b) -> ((BigDecimal) b.get("revenue")).compareTo((BigDecimal) a.get("revenue"))).limit(10).toList());

        Map<String, Map<String, Object>> staff = new LinkedHashMap<>();
        for (Order o : created) {
            Map<String, Object> m = staff.computeIfAbsent(o.getCreatedByName(),
                    k -> new LinkedHashMap<>(Map.of("name", k, "ordersTaken", 0L, "revenue", BigDecimal.ZERO)));
            m.put("ordersTaken", (Long) m.get("ordersTaken") + 1);
            if (o.getStatus() == OrderStatus.DELIVERED) m.put("revenue", ((BigDecimal) m.get("revenue")).add(o.getTotalAmount()));
        }
        out.put("employeeActivity", staff.values());

        Map<String, Map<String, Object>> approvals = new LinkedHashMap<>();
        for (Order o : created) {
            if (o.getDecidedByName() == null) continue;
            Map<String, Object> m = approvals.computeIfAbsent(o.getDecidedByName(),
                    k -> new LinkedHashMap<>(Map.of("name", k, "approved", 0L, "rejected", 0L)));
            String key = o.getStatus() == OrderStatus.REJECTED ? "rejected" : "approved";
            m.put(key, (Long) m.get(key) + 1);
        }
        out.put("approvalHistory", approvals.values());
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        spent.forEach(e -> byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add));
        out.put("expensesByCategory", byCategory.entrySet().stream()
                .map(e -> Map.of("category", e.getKey(), "amount", e.getValue())).toList());
        out.put("debtAging", aging(debts, today));
        return out;
    }

    /** Open debt grouped by how late it is. */
    public static List<Map<String, Object>> aging(List<Debt> debts, LocalDate today) {
        String[] labels = {"Not yet due", "1-30 days late", "31-60 days late", "61-90 days late", "Over 90 days late"};
        BigDecimal[] sums = new BigDecimal[5];
        long[] counts = new long[5];
        Arrays.fill(sums, BigDecimal.ZERO);
        for (Debt d : debts) {
            if (d.getOutstanding().signum() <= 0) continue;
            long late = d.isOverdue(today) ? ChronoUnit.DAYS.between(d.getDueDate(), today) : 0;
            int b = late == 0 ? 0 : late <= 30 ? 1 : late <= 60 ? 2 : late <= 90 ? 3 : 4;
            sums[b] = sums[b].add(d.getOutstanding());
            counts[b]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(Map.of("bucket", labels[i], "debts", counts[i], "amount", sums[i]));
        return rows;
    }

    private Map<String, Object> outletRow(Outlet outlet) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outletId", outlet.getId());
        m.put("outlet", outlet.getName());
        m.put("orders", 0L);
        m.put("revenue", BigDecimal.ZERO);
        m.put("costOfGoods", BigDecimal.ZERO);
        m.put("debt", BigDecimal.ZERO);
        return m;
    }

    private <T> BigDecimal sum(Collection<T> items, java.util.function.Function<T, BigDecimal> f) {
        return items.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
