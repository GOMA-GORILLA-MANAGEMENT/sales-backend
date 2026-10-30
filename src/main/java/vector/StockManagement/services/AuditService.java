package vector.StockManagement.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.config.RequestIdFilter;
import vector.StockManagement.config.TenantContext;
import vector.StockManagement.model.AuditChainHead;
import vector.StockManagement.model.AuditLog;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.repositories.AuditChainHeadRepository;
import vector.StockManagement.repositories.AuditLogRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Append-only, hash-chained audit trail. Each entry stores the hash of the one before it, so changing
 * or removing any record breaks every hash after it and {@link #verify} reports where.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditChainHeadRepository headRepository;
    private final Access access;
    private final ObjectMapper objectMapper;

    public record Verification(boolean valid, long entries, Long brokenAtSeq, String message) {}

    /** Records an action in the current business's chain (or the platform chain for administrators). */
    @Transactional
    public void log(String action, String entityType, Object entityId, String reference,
                    Object previous, Object next, String reason, Long outletId) {
        Long tenant = TenantContext.getTenantId();
        logIn(tenant == null ? 0L : tenant, action, entityType, entityId, reference, previous, next, reason, outletId);
    }

    @Transactional
    public void logIn(Long chainKey, String action, String entityType, Object entityId, String reference,
                      Object previous, Object next, String reason, Long outletId) {
        AuditChainHead head = headRepository.lockByChainKey(chainKey).orElse(null);
        if (head == null) {
            head = new AuditChainHead();
            head.setChainKey(chainKey);
            head.setLastSeq(0);
            head = headRepository.saveAndFlush(head);
            head = headRepository.lockByChainKey(chainKey).orElse(head);
        }
        User actor = access.principal();
        AuditLog entry = new AuditLog();
        entry.setChainKey(chainKey);
        entry.setSeq(head.getLastSeq() + 1);
        if (actor != null) {
            entry.setActorId(actor.getId());
            entry.setActorEmail(actor.getEmail());
            entry.setActorRole(actor.getRole().name());
        }
        entry.setOutletId(outletId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setReference(reference);
        entry.setPreviousValue(toJson(previous));
        entry.setNewValue(toJson(next));
        entry.setReason(reason);
        entry.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
        entry.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        entry.setPrevHash(head.getLastHash());
        entry.setHash(hash(entry));
        auditLogRepository.save(entry);

        head.setLastSeq(entry.getSeq());
        head.setLastHash(entry.getHash());
        headRepository.save(head);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> search(LocalDate from, LocalDate to, String action) {
        access.require(Permission.AUDIT_VIEW);
        Long tenantId = access.tenantId();
        LocalDateTime start = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return auditLogRepository.search(tenantId, start, end, action == null || action.isBlank() ? null : action);
    }

    @Transactional(readOnly = true)
    public Verification verifyCurrentBusiness() {
        access.require(Permission.AUDIT_VIEW);
        return verify(access.tenantId());
    }

    @Transactional(readOnly = true)
    public Verification verify(Long chainKey) {
        List<AuditLog> chain = auditLogRepository.findChain(chainKey);
        String previousHash = null;
        long expectedSeq = 1;
        for (AuditLog entry : chain) {
            if (entry.getSeq() != expectedSeq) {
                return new Verification(false, chain.size(), expectedSeq,
                        "Record " + expectedSeq + " is missing from the trail");
            }
            if (!java.util.Objects.equals(entry.getPrevHash(), previousHash)) {
                return new Verification(false, chain.size(), entry.getSeq(),
                        "Record " + entry.getSeq() + " does not follow the record before it");
            }
            if (!hash(entry).equals(entry.getHash())) {
                return new Verification(false, chain.size(), entry.getSeq(),
                        "Record " + entry.getSeq() + " was altered");
            }
            previousHash = entry.getHash();
            expectedSeq++;
        }
        return new Verification(true, chain.size(), null, "All " + chain.size() + " records are intact");
    }

    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    static String hash(AuditLog e) {
        String canonical = String.join("|",
                String.valueOf(e.getSeq()), String.valueOf(e.getChainKey()), nz(e.getActorId()), nz(e.getActorEmail()),
                nz(e.getActorRole()), nz(e.getOutletId()), nz(e.getAction()), nz(e.getEntityType()), nz(e.getEntityId()),
                nz(e.getReference()), nz(e.getPreviousValue()), nz(e.getNewValue()), nz(e.getReason()),
                nz(e.getRequestId()), String.valueOf(e.getCreatedAt().truncatedTo(ChronoUnit.MILLIS)), nz(e.getPrevHash()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Hashing is unavailable", ex);
        }
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
