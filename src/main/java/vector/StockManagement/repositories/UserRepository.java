package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("select u from User u where u.tenant.id = :tenantId order by u.firstName")
    List<User> findAllByTenantId(Long tenantId);

    @Query("select u from User u where u.id = :id and u.tenant.id = :tenantId")
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    @Query("select count(u) from User u where u.tenant.id = :tenantId")
    long countByTenantId(Long tenantId);

    @Query("select u from User u where u.tenant.id = :tenantId and u.role = :role order by u.id")
    List<User> findByTenantIdAndRole(Long tenantId, Role role);

    boolean existsByRole(Role role);

    long countByRoleNot(Role role);
}
