package vector.StockManagement.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Sequence;

import java.util.Optional;

public interface SequenceRepository extends JpaRepository<Sequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sequence s where s.tenantId = :tenantId and s.name = :name")
    Optional<Sequence> lock(Long tenantId, String name);
}
