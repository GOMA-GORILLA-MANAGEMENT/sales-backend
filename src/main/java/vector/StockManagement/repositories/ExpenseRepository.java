package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Expense;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("select e from Expense e join fetch e.outlet o where e.tenantId = :tenantId and o.id in :outletIds "
            + "and e.expenseDate >= :from and e.expenseDate <= :to order by e.expenseDate desc, e.id desc")
    List<Expense> findInPeriod(Long tenantId, Collection<Long> outletIds, LocalDate from, LocalDate to);

    @Query("select e from Expense e join fetch e.outlet where e.id = :id and e.tenantId = :tenantId")
    Optional<Expense> findByIdAndTenant(Long id, Long tenantId);
}
