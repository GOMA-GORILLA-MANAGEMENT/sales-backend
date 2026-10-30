package vector.StockManagement.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Debt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    @Query("select d from Debt d join fetch d.customer left join fetch d.outlet left join fetch d.order "
            + "where d.id = :id and d.tenantId = :tenantId")
    Optional<Debt> findByIdAndTenant(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Debt d where d.id = :id and d.tenantId = :tenantId")
    Optional<Debt> findForUpdate(Long id, Long tenantId);

    @Query("select d from Debt d join fetch d.customer left join fetch d.outlet left join fetch d.order "
            + "where d.tenantId = :tenantId and (d.outlet is null or d.outlet.id in :outletIds) "
            + "and (:customerId is null or d.customer.id = :customerId) order by d.dueDate, d.id")
    List<Debt> search(Long tenantId, Collection<Long> outletIds, Long customerId);

    @Query("select d from Debt d join fetch d.customer left join fetch d.outlet "
            + "where d.tenantId = :tenantId and d.customer.id = :customerId order by d.id")
    List<Debt> findForCustomer(Long tenantId, Long customerId);

    @Query("select coalesce(sum(d.originalAmount - d.paidAmount - d.adjustedAmount), 0) from Debt d "
            + "where d.tenantId = :tenantId and d.customer.id = :customerId")
    java.math.BigDecimal outstandingForCustomer(Long tenantId, Long customerId);

    @Query("select d.customer.id, coalesce(sum(d.originalAmount - d.paidAmount - d.adjustedAmount), 0) from Debt d "
            + "where d.tenantId = :tenantId group by d.customer.id")
    List<Object[]> outstandingByCustomer(Long tenantId);
}
