package vector.StockManagement.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Order;
import vector.StockManagement.model.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select distinct o from Order o join fetch o.outlet left join fetch o.customer left join fetch o.lines "
            + "where o.id = :id and o.tenantId = :tenantId")
    Optional<Order> findByIdAndTenant(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.tenantId = :tenantId")
    Optional<Order> findForUpdate(Long id, Long tenantId);

    @Query("select o from Order o join fetch o.outlet left join fetch o.customer "
            + "where o.tenantId = :tenantId and o.outlet.id in :outletIds "
            + "and (:status is null or o.status = :status) "
            + "and (:createdBy is null or o.createdById = :createdBy) order by o.id desc")
    List<Order> search(Long tenantId, Collection<Long> outletIds, OrderStatus status, Long createdBy);

    @Query("select o from Order o join fetch o.outlet left join fetch o.customer "
            + "where o.tenantId = :tenantId and o.outlet.id in :outletIds "
            + "and o.status = 'DELIVERED' and o.deliveredAt >= :from and o.deliveredAt < :to order by o.deliveredAt")
    List<Order> deliveredInPeriod(Long tenantId, java.util.Collection<Long> outletIds, LocalDateTime from, LocalDateTime to);

    @Query("select o from Order o join fetch o.outlet left join fetch o.customer "
            + "where o.tenantId = :tenantId and o.outlet.id in :outletIds "
            + "and o.createdAt >= :from and o.createdAt < :to order by o.id desc")
    List<Order> createdInPeriod(Long tenantId, java.util.Collection<Long> outletIds, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.tenantId = :tenantId and o.status = 'DELIVERED'")
    java.math.BigDecimal totalRevenue(Long tenantId);

    @Query("select count(o) from Order o where o.tenantId = :tenantId and o.status = 'DELIVERED'")
    long countDelivered(Long tenantId);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status = 'DELIVERED'")
    java.math.BigDecimal platformRevenue();
}
