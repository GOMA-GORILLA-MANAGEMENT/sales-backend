package vector.StockManagement.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.AuditChainHead;

import java.util.Optional;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.chainKey = :chainKey")
    Optional<AuditChainHead> lockByChainKey(Long chainKey);
}
