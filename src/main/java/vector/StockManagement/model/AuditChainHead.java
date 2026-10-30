package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tail of a tenant's audit chain. Locked while appending so the chain cannot fork. */
@Entity
@Table(name = "audit_chain_heads")
@Getter
@Setter
@NoArgsConstructor
public class AuditChainHead {

    @Id
    @Column(name = "chain_key")
    private Long chainKey;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_hash", length = 64)
    private String lastHash;
}
