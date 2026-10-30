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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Debts are never deleted. Payments are never edited: a mistake is cancelled by a reversal entry, and
 * discounts, write-offs and corrections are separate entries that need a reason.
 */
@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final DebtAdjustmentRepository adjustmentRepository;
    private final CustomerRepository customerRepository;
    private final SequenceService sequences;
    private final Access access;
    private final AuditService audit;

    public record Statement(Views.CustomerView customer, List<Views.DebtView> debts, List<Views.PaymentView> payments,
                            BigDecimal totalBilled, BigDecimal totalPaid, BigDecimal totalAdjusted,
                            BigDecimal outstanding, BigDecimal overdue) {}

    @Transactional(readOnly = true)
    public List<Views.DebtView> list(Long outletId, Long customerId, boolean openOnly) {
        access.require(Permission.DEBT_VIEW);
        Set<Long> scope = access.scopeOutlets(outletId);
        LocalDate today = LocalDate.now();
        return debtRepository.search(access.tenantId(), scope, customerId).stream()
                .filter(d -> !openOnly || d.getOutstanding().signum() > 0)
                .map(d -> Views.of(d, today)).toList();
    }

    @Transactional(readOnly = true)
    public Views.DebtDetail detail(Long id) {
        access.require(Permission.DEBT_VIEW);
        Debt debt = visibleDebt(id);
        Long tenantId = access.tenantId();
        List<Payment> payments = paymentRepository.findForDebt(tenantId, id);
        List<Views.PaymentView> paymentViews = payments.stream()
                .map(p -> Views.of(p, paymentRepository.existsByReversesPaymentIdAndTenantId(p.getId(), tenantId))).toList();
        List<Views.AdjustmentView> adjustments = adjustmentRepository.findForDebt(tenantId, id).stream()
                .map(a -> new Views.AdjustmentView(a.getId(), a.getType(), a.getAmount(), a.getReason(), a.getActorName(),
                        a.getCreatedAt())).toList();
        return new Views.DebtDetail(Views.of(debt, LocalDate.now()), paymentViews, adjustments);
    }

    @Transactional
    public Views.DebtDetail pay(Long id, Dtos.DebtPaymentRequest request) {
        access.require(Permission.PAYMENT_TAKE);
        User actor = access.user();
        Long tenantId = access.tenantId();
        debtRepository.findForUpdate(id, tenantId).orElseThrow(() -> ApiException.notFound("Debt not found"));
        Debt debt = visibleDebt(id);
        BigDecimal outstanding = debt.getOutstanding();
        if (outstanding.signum() <= 0) throw ApiException.conflict("This debt is already settled");
        if (request.method() == PaymentMethod.CREDIT) throw ApiException.badRequest("Choose how the customer paid");
        if (request.amount().compareTo(outstanding) > 0) {
            throw ApiException.badRequest("The customer only owes " + Money.rwf(outstanding));
        }
        Payment payment = new Payment();
        payment.setTenantId(tenantId);
        payment.setKind(PaymentKind.DEBT_PAYMENT);
        payment.setDebt(debt);
        payment.setCustomer(debt.getCustomer());
        payment.setOutlet(debt.getOutlet());
        payment.setOrder(debt.getOrder());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setReceiptNumber(sequences.next(tenantId, "RCP"));
        payment.setCreatedById(actor.getId());
        payment.setCreatedByName(actor.getFullName());
        payment = paymentRepository.save(payment);

        debt.setPaidAmount(debt.getPaidAmount().add(request.amount()));
        refreshStatus(debt, null);
        audit.log("debt.payment", "Payment", payment.getId(), payment.getReceiptNumber(), outstanding,
                debt.getOutstanding(), null, debt.getOutlet() == null ? null : debt.getOutlet().getId());
        return detail(id);
    }

    @Transactional
    public Views.DebtDetail reversePayment(Long paymentId, String reason) {
        access.require(Permission.DEBT_ADJUST);
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("A written reason is required");
        User actor = access.user();
        Long tenantId = access.tenantId();
        Payment original = paymentRepository.findByIdAndTenant(paymentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (original.getKind() != PaymentKind.DEBT_PAYMENT || original.getDebt() == null) {
            throw ApiException.badRequest("Only payments on a debt can be reversed");
        }
        Long debtId = original.getDebt().getId();
        debtRepository.findForUpdate(debtId, tenantId).orElseThrow(() -> ApiException.notFound("Debt not found"));
        Debt debt = visibleDebt(debtId);
        if (paymentRepository.existsByReversesPaymentIdAndTenantId(paymentId, tenantId)) {
            throw ApiException.conflict("This payment was already reversed");
        }
        Payment reversal = new Payment();
        reversal.setTenantId(tenantId);
        reversal.setKind(PaymentKind.REVERSAL);
        reversal.setDebt(debt);
        reversal.setCustomer(debt.getCustomer());
        reversal.setOutlet(debt.getOutlet());
        reversal.setOrder(debt.getOrder());
        reversal.setAmount(original.getAmount().negate());
        reversal.setMethod(original.getMethod());
        reversal.setReceiptNumber(sequences.next(tenantId, "REV"));
        reversal.setReversesPaymentId(paymentId);
        reversal.setReason(reason.trim());
        reversal.setCreatedById(actor.getId());
        reversal.setCreatedByName(actor.getFullName());
        reversal = paymentRepository.save(reversal);

        BigDecimal before = debt.getOutstanding();
        debt.setPaidAmount(debt.getPaidAmount().subtract(original.getAmount()));
        refreshStatus(debt, null);
        audit.log("debt.payment_reversed", "Payment", reversal.getId(), reversal.getReceiptNumber(), before,
                debt.getOutstanding(), reason.trim(), debt.getOutlet() == null ? null : debt.getOutlet().getId());
        return detail(debtId);
    }

    @Transactional
    public Views.DebtDetail adjust(Long id, Dtos.DebtAdjustRequest request) {
        access.require(Permission.DEBT_ADJUST);
        if (request.reason() == null || request.reason().isBlank()) throw ApiException.badRequest("A written reason is required");
        User actor = access.user();
        Long tenantId = access.tenantId();
        debtRepository.findForUpdate(id, tenantId).orElseThrow(() -> ApiException.notFound("Debt not found"));
        Debt debt = visibleDebt(id);
        BigDecimal outstanding = debt.getOutstanding();
        BigDecimal amount = request.amount();
        if (amount.signum() == 0) throw ApiException.badRequest("The amount cannot be zero");
        if (request.type() != AdjustmentType.CORRECTION && amount.signum() < 0) {
            throw ApiException.badRequest("Discounts and write-offs reduce the debt, so the amount must be positive");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw ApiException.badRequest("The customer only owes " + Money.rwf(outstanding));
        }
        DebtAdjustment adjustment = new DebtAdjustment();
        adjustment.setTenantId(tenantId);
        adjustment.setDebt(debt);
        adjustment.setType(request.type());
        adjustment.setAmount(amount);
        adjustment.setReason(request.reason().trim());
        adjustment.setActorId(actor.getId());
        adjustment.setActorName(actor.getFullName());
        adjustment = adjustmentRepository.save(adjustment);

        debt.setAdjustedAmount(debt.getAdjustedAmount().add(amount));
        refreshStatus(debt, request.type());
        audit.log("debt." + request.type().name().toLowerCase(), "Debt", id, "debt-" + id, outstanding,
                debt.getOutstanding(), request.reason().trim(), debt.getOutlet() == null ? null : debt.getOutlet().getId());
        return detail(id);
    }

    @Transactional(readOnly = true)
    public Statement statement(Long customerId) {
        access.require(Permission.DEBT_VIEW);
        Long tenantId = access.tenantId();
        Customer customer = customerRepository.findByIdAndTenant(customerId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        LocalDate today = LocalDate.now();
        List<Debt> debts = debtRepository.findForCustomer(tenantId, customerId);
        BigDecimal billed = BigDecimal.ZERO, paid = BigDecimal.ZERO, adjusted = BigDecimal.ZERO,
                outstanding = BigDecimal.ZERO, overdue = BigDecimal.ZERO;
        for (Debt d : debts) {
            billed = billed.add(d.getOriginalAmount());
            paid = paid.add(d.getPaidAmount());
            adjusted = adjusted.add(d.getAdjustedAmount());
            outstanding = outstanding.add(d.getOutstanding());
            if (d.isOverdue(today)) overdue = overdue.add(d.getOutstanding());
        }
        List<Views.PaymentView> payments = paymentRepository.findForCustomer(tenantId, customerId).stream()
                .map(p -> Views.of(p, paymentRepository.existsByReversesPaymentIdAndTenantId(p.getId(), tenantId))).toList();
        return new Statement(Views.of(customer, outstanding), debts.stream().map(d -> Views.of(d, today)).toList(),
                payments, billed, paid, adjusted, outstanding, overdue);
    }

    private Debt visibleDebt(Long id) {
        Debt debt = debtRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Debt not found"));
        if (debt.getOutlet() != null) access.outlet(debt.getOutlet().getId());
        return debt;
    }

    private void refreshStatus(Debt debt, AdjustmentType lastAdjustment) {
        BigDecimal outstanding = debt.getOutstanding();
        if (outstanding.signum() <= 0) {
            debt.setStatus(lastAdjustment == AdjustmentType.WRITE_OFF ? DebtStatus.WRITTEN_OFF : DebtStatus.SETTLED);
        } else if (debt.getPaidAmount().signum() > 0 || debt.getAdjustedAmount().signum() != 0) {
            debt.setStatus(DebtStatus.PARTIAL);
        } else {
            debt.setStatus(DebtStatus.OPEN);
        }
    }
}
