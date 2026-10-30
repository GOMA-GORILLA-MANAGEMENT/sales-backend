package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select distinct p from Product p left join fetch p.variants where p.tenantId = :tenantId order by p.name")
    List<Product> findAllByTenant(Long tenantId);

    @Query("select p from Product p left join fetch p.variants where p.id = :id and p.tenantId = :tenantId")
    Optional<Product> findByIdAndTenant(Long id, Long tenantId);
}
