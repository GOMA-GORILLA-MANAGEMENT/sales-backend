package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** One link of a tamper-evident chain: {@code hash} covers this entry and the previous hash. */
@Entity
@Immutable
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_tenant_seq", columnList = "chain_key, seq"))
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant id, or 0 for platform-level events. */
    @Column(name = "chain_key", nullable = false, updatable = false)
    private Long chainKey;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_email", updatable = false)
    private String actorEmail;

    @Column(name = "actor_role", updatable = false)
    private String actorRole;

    @Column(name = "outlet_id", updatable = false)
    private Long outletId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @Column(updatable = false)
    private String reference;

    @Column(name = "previous_value", columnDefinition = "TEXT", updatable = false)
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "request_id", updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "prev_hash", length = 64, updatable = false)
    private String prevHash;

    @Column(nullable = false, length = 64, updatable = false)
    private String hash;
}
