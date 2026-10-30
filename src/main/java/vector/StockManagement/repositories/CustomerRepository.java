package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where c.tenantId = :tenantId order by c.name")
    List<Customer> findAllByTenant(Long tenantId);

    @Query("select c from Customer c where c.id = :id and c.tenantId = :tenantId")
    Optional<Customer> findByIdAndTenant(Long id, Long tenantId);

    @Query("select c from Customer c where c.tenantId = :tenantId and c.phone = :phone order by c.id")
    List<Customer> findByPhone(Long tenantId, String phone);

    @Query("select c from Customer c where c.tenantId = :tenantId and lower(c.name) = lower(:name) order by c.id")
    List<Customer> findByNameIgnoreCase(Long tenantId, String name);
}
