package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Expense;
import vector.StockManagement.model.Outlet;
import vector.StockManagement.model.User;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.repositories.ExpenseRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final Access access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<Views.ExpenseView> list(Long outletId, LocalDate from, LocalDate to) {
        access.require(Permission.EXPENSE_MANAGE);
        Set<Long> scope = access.scopeOutlets(outletId);
        return expenseRepository.findInPeriod(access.tenantId(), scope, from == null ? LocalDate.now().minusDays(29) : from,
                to == null ? LocalDate.now() : to).stream().map(Views::of).toList();
    }

    @Transactional
    public Views.ExpenseView create(Dtos.ExpenseRequest request) {
        access.require(Permission.EXPENSE_MANAGE);
        User actor = access.user();
        Outlet outlet = access.outlet(request.outletId());
        LocalDate date = request.expenseDate() == null ? LocalDate.now() : request.expenseDate();
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("An expense cannot be dated in the future");
        Expense e = new Expense();
        e.setTenantId(access.tenantId());
        e.setOutlet(outlet);
        e.setCategory(request.category().trim());
        e.setDescription(request.description());
        e.setAmount(request.amount());
        e.setExpenseDate(date);
        e.setCreatedById(actor.getId());
        e.setCreatedByName(actor.getFullName());
        e = expenseRepository.save(e);
        audit.log("expense.recorded", "Expense", e.getId(), e.getCategory(), null,
                Map.of("amount", e.getAmount(), "date", date.toString(), "outlet", outlet.getName()), null, outlet.getId());
        return Views.of(e);
    }

    @Transactional
    public Views.ExpenseView voidExpense(Long id, String reason) {
        access.require(Permission.EXPENSE_VOID);
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("A written reason is required");
        User actor = access.user();
        Expense e = expenseRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
        access.outlet(e.getOutlet().getId());
        if (e.isVoided()) throw ApiException.conflict("This expense is already voided");
        e.setVoided(true);
        e.setVoidReason(reason.trim());
        e.setVoidedByName(actor.getFullName());
        audit.log("expense.voided", "Expense", id, e.getCategory(), e.getAmount(), "voided", reason.trim(), e.getOutlet().getId());
        return Views.of(e);
    }
}
