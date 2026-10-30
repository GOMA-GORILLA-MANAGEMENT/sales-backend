package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p left join fetch p.debt left join fetch p.customer where p.id = :id and p.tenantId = :tenantId")
    Optional<Payment> findByIdAndTenant(Long id, Long tenantId);

    boolean existsByReversesPaymentIdAndTenantId(Long reversesPaymentId, Long tenantId);

    @Query("select p from Payment p left join fetch p.customer left join fetch p.outlet "
            + "where p.tenantId = :tenantId and p.debt.id = :debtId order by p.id")
    List<Payment> findForDebt(Long tenantId, Long debtId);

    @Query("select p from Payment p left join fetch p.customer left join fetch p.outlet "
            + "where p.tenantId = :tenantId and p.customer.id = :customerId order by p.id")
    List<Payment> findForCustomer(Long tenantId, Long customerId);

    @Query("select p from Payment p left join fetch p.customer left join fetch p.outlet o "
            + "where p.tenantId = :tenantId and o.id in :outletIds "
            + "and p.createdAt >= :from and p.createdAt < :to order by p.id desc")
    List<Payment> findInPeriod(Long tenantId, java.util.Collection<Long> outletIds, LocalDateTime from, LocalDateTime to);
}
