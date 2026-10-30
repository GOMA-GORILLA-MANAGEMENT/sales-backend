package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByNameIgnoreCase(String name);

    @Query("select coalesce(t.active, true) from Tenant t where t.id = :id")
    Boolean isActive(Long id);

    long countByActiveTrue();

    long countByActiveFalse();
}
