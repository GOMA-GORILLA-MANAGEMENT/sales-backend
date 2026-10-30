package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import vector.StockManagement.model.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

/** Insert and read only: the application has no way to update or delete audit entries. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);

    @Query("select a from AuditLog a where a.chainKey = :chainKey order by a.seq")
    List<AuditLog> findChain(Long chainKey);

    @Query("select a from AuditLog a where a.chainKey = :chainKey and a.createdAt >= :from and a.createdAt < :to "
            + "and (:action is null or a.action = :action) order by a.seq desc")
    List<AuditLog> search(Long chainKey, LocalDateTime from, LocalDateTime to, String action);
}
