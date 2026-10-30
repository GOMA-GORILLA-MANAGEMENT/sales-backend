package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.*;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.*;
import vector.StockManagement.repositories.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final TenantRepository tenantRepository;
    private final StockService stockService;
    private final SequenceService sequences;
    private final Access access;
    private final AuditService audit;

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<Views.OrderView> list(Long outletId, OrderStatus status) {
        User user = access.user();
        Set<Long> scope = access.scopeOutlets(outletId);
        Long onlyMine = null;
        if (!access.can(Permission.ORDER_VIEW_ALL)) {
            if (!access.can(Permission.ORDER_CREATE)) {
                throw ApiException.forbidden("You do not have permission to do this");
            }
            onlyMine = user.getId();
        }
        return orderRepository.search(access.tenantId(), scope, status, onlyMine).stream()
                .map(o -> Views.of(o, null)).toList();
    }

    @Transactional(readOnly = true)
    public Views.OrderView get(Long id) {
        Order order = load(id);
        return Views.of(order, historyRepository.findForOrder(access.tenantId(), id));
    }

    // ---------- create ----------

    @Transactional
    public Views.OrderView create(Dtos.OrderRequest request) {
        access.require(Permission.ORDER_CREATE);
        Long tenantId = access.tenantId();
        User actor = access.user();
        Outlet outlet = access.outlet(request.outletId());
        if (outlet.getStatus() == OutletStatus.PAUSED) {
            throw ApiException.conflict(outlet.getName() + " is paused and is not taking orders");
        }
        Customer customer = request.customerId() == null ? null : customer(request.customerId(), tenantId);
        if (request.paymentMethod() == PaymentMethod.CREDIT && customer == null) {
            throw ApiException.badRequest("Choose the customer who will pay on credit");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        Order order = new Order();
        order.setTenantId(tenantId);
        order.setOutlet(outlet);
        order.setCustomer(customer);
        order.setPaymentMethod(request.paymentMethod());
        order.setTableLabel(request.tableLabel());
        order.setCreatedById(actor.getId());
        order.setCreatedByName(actor.getFullName());

        BigDecimal total = BigDecimal.ZERO;
        for (Dtos.OrderLineRequest l : request.lines()) {
            ProductVariant variant = variantRepository.findByIdAndTenant(l.variantId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Menu item not found"));
            if (!variant.isActive() || !variant.getProduct().isActive()) {
                throw ApiException.conflict(variant.displayName() + " is not on the menu right now");
            }
            int upp = stockService.unitsPer(variant, l.unit(), null);
            BigDecimal unitPrice = l.unit() == UnitType.PACK
                    ? (variant.getPackPrice() != null ? variant.getPackPrice()
                        : variant.getSellPrice().multiply(BigDecimal.valueOf(upp)))
                    : variant.getSellPrice();
            OrderLine line = new OrderLine();
            line.setTenantId(tenantId);
            line.setVariant(variant);
            line.setItemName(variant.displayName() + (l.unit() == UnitType.PACK ? " (" + variant.getPackUnitName() + ")" : ""));
            line.setUnitType(l.unit());
            line.setQuantity(l.quantity());
            line.setUnitPrice(unitPrice);
            line.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP));
            line.setBaseQuantity((long) l.quantity() * upp);
            order.addLine(line);
            total = total.add(line.getLineTotal());
        }
        order.setTotalAmount(total);
        order.setCode(sequences.next(tenantId, "ORD"));
        boolean needsApproval = tenant.isApprovalRequired();
        order.setStatus(needsApproval ? OrderStatus.PENDING_APPROVAL : OrderStatus.APPROVED);
        order = orderRepository.save(order);

        record(order, null, OrderStatus.PENDING_APPROVAL, actor, "Order sent");
        if (!needsApproval) {
            record(order, OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED, actor, "Approval is switched off for this business");
        }
        audit.log("order.created", "Order", order.getId(), order.getCode(), null, summary(order), null, outlet.getId());
        return Views.of(order, historyRepository.findForOrder(tenantId, order.getId()));
    }

    // ---------- approval ----------

    @Transactional
    public Views.OrderView approve(Long id) {
        access.require(Permission.ORDER_APPROVE);
        User actor = access.user();
        Order order = lockAndLoad(id);
        access.outlet(order.getOutlet().getId());
        requireStatus(order, OrderStatus.PENDING_APPROVAL, "approved");
        if (order.getCreatedById().equals(actor.getId())) {
            throw ApiException.forbidden("You cannot approve an order you created yourself");
        }
        order.setStatus(OrderStatus.APPROVED);
        order.setDecidedById(actor.getId());
        order.setDecidedByName(actor.getFullName());
        record(order, OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED, actor, null);
        audit.log("order.approved", "Order", id, order.getCode(), OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED,
                null, order.getOutlet().getId());
        return get(id);
    }

    @Transactional
    public Views.OrderView reject(Long id, String reason) {
        access.require(Permission.ORDER_APPROVE);
        requireReason(reason, "Say why the order is rejected");
        User actor = access.user();
        Order order = lockAndLoad(id);
        access.outlet(order.getOutlet().getId());
        requireStatus(order, OrderStatus.PENDING_APPROVAL, "rejected");
        if (order.getCreatedById().equals(actor.getId())) {
            throw ApiException.forbidden("You cannot reject an order you created yourself");
        }
        order.setStatus(OrderStatus.REJECTED);
        order.setDecidedById(actor.getId());
        order.setDecidedByName(actor.getFullName());
        order.setDecisionReason(reason.trim());
        record(order, OrderStatus.PENDING_APPROVAL, OrderStatus.REJECTED, actor, reason.trim());
        audit.log("order.rejected", "Order", id, order.getCode(), OrderStatus.PENDING_APPROVAL, OrderStatus.REJECTED,
                reason.trim(), order.getOutlet().getId());
        return get(id);
    }

    @Transactional
    public Views.OrderView cancel(Long id, String reason) {
        requireReason(reason, "Say why the order is cancelled");
        User actor = access.user();
        Order order = lockAndLoad(id);
        boolean any = access.can(Permission.ORDER_CANCEL_ANY);
        boolean own = access.can(Permission.ORDER_CANCEL_OWN) && order.getCreatedById().equals(actor.getId());
        if (!any && !own) throw ApiException.forbidden("You can only cancel orders you created");
        access.outlet(order.getOutlet().getId());
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL && order.getStatus() != OrderStatus.APPROVED) {
            throw ApiException.conflict("This order is already " + order.getStatus().name().toLowerCase().replace('_', ' '));
        }
        OrderStatus before = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setDecisionReason(reason.trim());
        record(order, before, OrderStatus.CANCELLED, actor, reason.trim());
        audit.log("order.cancelled", "Order", id, order.getCode(), before, OrderStatus.CANCELLED, reason.trim(),
                order.getOutlet().getId());
        return get(id);
    }

    // ---------- delivery ----------

    /**
     * Records that the order was served. In one transaction: stock is taken off the shelf, the cost of goods is
     * worked out, the payment is recorded and, if money is still owed, a debt is opened. Any refusal (not enough
     * stock, credit limit) rolls everything back and the order stays approved.
     */
    @Transactional
    public Views.OrderView deliver(Long id, Dtos.DeliverRequest request) {
        access.require(Permission.ORDER_DELIVER);
        User actor = access.user();
        Long tenantId = access.tenantId();
        Order order = lockAndLoad(id);
        access.outlet(order.getOutlet().getId());
        requireStatus(order, OrderStatus.APPROVED, "delivered");

        BigDecimal total = order.getTotalAmount();
        BigDecimal paid = request.amountPaid().setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(total) > 0) {
            throw ApiException.badRequest("The amount paid is more than the bill of " + Money.rwf(total));
        }
        if (paid.signum() > 0) access.require(Permission.PAYMENT_TAKE);
        BigDecimal unpaid = total.subtract(paid);

        // Work out the debt first, so a credit-limit refusal happens before anything is changed.
        Customer customer = request.customerId() != null ? customer(request.customerId(), tenantId) : order.getCustomer();
        String overrideUsed = null;
        if (unpaid.signum() > 0) {
            if (customer == null) {
                throw ApiException.badRequest("Choose which customer owes the remaining " + Money.rwf(unpaid));
            }
            BigDecimal owedNow = debtRepository.outstandingForCustomer(tenantId, customer.getId());
            BigDecimal afterwards = owedNow.add(unpaid);
            if (afterwards.compareTo(customer.getCreditLimit()) > 0) {
                boolean mayOverride = access.can(Permission.CREDIT_OVERRIDE);
                boolean hasReason = request.overrideReason() != null && !request.overrideReason().isBlank();
                if (!(mayOverride && hasReason)) {
                    throw ApiException.conflict(customer.getName() + " would owe " + Money.rwf(afterwards) + ", above the credit limit of "
                            + Money.rwf(customer.getCreditLimit()) + (mayOverride
                            ? ". Give a written reason to override the limit."
                            : ". An owner or overall manager must override the limit."));
                }
                overrideUsed = request.overrideReason().trim();
            }
        }

        // Take stock off the shelf and add up what the goods cost.
        BigDecimal cogs = BigDecimal.ZERO;
        for (OrderLine line : order.getLines()) {
            ProductVariant variant = line.getVariant();
            if (!variant.isTracked()) continue;
            int upp = line.getUnitType() == UnitType.PACK ? variant.getUnitsPerPack() : 1;
            StockService.Sold sold = stockService.sell(order.getOutlet(), variant, line.getUnitType(), line.getQuantity(),
                    upp, line.getBaseQuantity(), order.getCode());
            line.setUnitCost(sold.unitCost());
            cogs = cogs.add(sold.totalCost());
        }

        PaymentMethod method = request.paymentMethod() != null ? request.paymentMethod() : order.getPaymentMethod();
        if (method == PaymentMethod.CREDIT) method = PaymentMethod.CASH;
        String receipt = null;
        if (paid.signum() > 0) {
            Payment payment = new Payment();
            payment.setTenantId(tenantId);
            payment.setKind(PaymentKind.ORDER_PAYMENT);
            payment.setOrder(order);
            payment.setCustomer(customer);
            payment.setOutlet(order.getOutlet());
            payment.setAmount(paid);
            payment.setMethod(method);
            payment.setReceiptNumber(sequences.next(tenantId, "RCP"));
            payment.setCreatedById(actor.getId());
            payment.setCreatedByName(actor.getFullName());
            payment = paymentRepository.save(payment);
            receipt = payment.getReceiptNumber();
            audit.log("payment.recorded", "Payment", payment.getId(), receipt, null,
                    Map.of("amount", paid, "method", method.name(), "order", order.getCode()), null, order.getOutlet().getId());
        }

        Debt debt = null;
        if (unpaid.signum() > 0) {
            debt = new Debt();
            debt.setTenantId(tenantId);
            debt.setCustomer(customer);
            debt.setOrder(order);
            debt.setOutlet(order.getOutlet());
            debt.setOriginalAmount(unpaid);
            debt.setDueDate(LocalDate.now().plusDays(customer.getCreditDays()));
            debt.setResponsibleId(actor.getId());
            debt.setResponsibleName(actor.getFullName());
            debt = debtRepository.save(debt);
            audit.log("debt.opened", "Debt", debt.getId(), order.getCode(), null,
                    Map.of("customer", customer.getName(), "amount", unpaid, "dueDate", debt.getDueDate().toString()),
                    overrideUsed, order.getOutlet().getId());
        }

        if (customer != null && order.getCustomer() == null) order.setCustomer(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setCostOfGoods(cogs);
        order.setAmountPaid(paid);
        order.setPaymentMethod(unpaid.signum() > 0 && paid.signum() == 0 ? PaymentMethod.CREDIT : method);
        order.setPaymentStatus(unpaid.signum() == 0 ? PaymentStatus.PAID
                : paid.signum() == 0 ? PaymentStatus.UNPAID : PaymentStatus.PARTIAL);
        order.setReceiptNumber(unpaid.signum() == 0 ? receipt : null);
        order.setDeliveredById(actor.getId());
        order.setDeliveredByName(actor.getFullName());
        order.setDeliveredAt(LocalDateTime.now());
        record(order, OrderStatus.APPROVED, OrderStatus.DELIVERED, actor,
                overrideUsed == null ? null : "Credit limit overridden: " + overrideUsed);
        audit.log("order.delivered", "Order", id, order.getCode(), OrderStatus.APPROVED,
                Map.of("status", "DELIVERED", "total", total, "paid", paid, "costOfGoods", cogs,
                        "paymentStatus", order.getPaymentStatus().name()),
                overrideUsed, order.getOutlet().getId());
        return get(id);
    }

    // ---------- helpers ----------

    private Order load(Long id) {
        Order order = orderRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        User user = access.user();
        if (!access.can(Permission.ORDER_VIEW_ALL)) {
            if (!order.getCreatedById().equals(user.getId())) throw ApiException.forbidden("This is not your order");
        } else {
            access.outlet(order.getOutlet().getId());
        }
        return order;
    }

    private Order lockAndLoad(Long id) {
        orderRepository.findForUpdate(id, access.tenantId()).orElseThrow(() -> ApiException.notFound("Order not found"));
        return orderRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private Customer customer(Long id, Long tenantId) {
        Customer c = customerRepository.findByIdAndTenant(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        if (!c.isActive()) throw ApiException.conflict(c.getName() + " is not active");
        return c;
    }

    private void requireStatus(Order order, OrderStatus expected, String verb) {
        if (order.getStatus() != expected) {
            throw ApiException.conflict("This order cannot be " + verb + " because it is "
                    + order.getStatus().name().toLowerCase().replace('_', ' '));
        }
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest(message);
    }

    private void record(Order order, OrderStatus from, OrderStatus to, User actor, String reason) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setTenantId(order.getTenantId());
        h.setOrder(order);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actor.getId());
        h.setActorName(actor.getFullName());
        h.setReason(reason);
        historyRepository.save(h);
    }

    private Object summary(Order o) {
        return Map.of("code", o.getCode(), "total", o.getTotalAmount(), "lines", o.getLines().size(),
                "status", o.getStatus().name());
    }
}
