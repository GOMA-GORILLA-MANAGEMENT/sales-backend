package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.DebtAdjustment;

import java.time.LocalDateTime;
import java.util.List;

public interface DebtAdjustmentRepository extends JpaRepository<DebtAdjustment, Long> {

    @Query("select a from DebtAdjustment a where a.tenantId = :tenantId and a.debt.id = :debtId order by a.id")
    List<DebtAdjustment> findForDebt(Long tenantId, Long debtId);

    @Query("select a from DebtAdjustment a join fetch a.debt d where a.tenantId = :tenantId "
            + "and a.createdAt >= :from and a.createdAt < :to")
    List<DebtAdjustment> findInPeriod(Long tenantId, LocalDateTime from, LocalDateTime to);
}
