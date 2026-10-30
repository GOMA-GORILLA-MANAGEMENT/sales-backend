package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.model.Sequence;
import vector.StockManagement.repositories.SequenceRepository;

@Service
@RequiredArgsConstructor
public class SequenceService {

    private final SequenceRepository sequenceRepository;

    /** Next number for a prefix, e.g. ORD-000001. Numbers are never reused. */
    @Transactional
    public String next(Long tenantId, String prefix) {
        Sequence seq = sequenceRepository.lock(tenantId, prefix).orElse(null);
        if (seq == null) {
            seq = new Sequence();
            seq.setTenantId(tenantId);
            seq.setName(prefix);
            seq.setValue(0);
            seq = sequenceRepository.saveAndFlush(seq);
        }
        seq.setValue(seq.getValue() + 1);
        sequenceRepository.save(seq);
        return String.format("%s-%06d", prefix, seq.getValue());
    }
}
