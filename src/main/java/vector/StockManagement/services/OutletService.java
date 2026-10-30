package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Outlet;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.OutletStatus;
import vector.StockManagement.model.enums.OutletType;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.repositories.OutletRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutletService {

    private final OutletRepository outletRepository;
    private final Access access;
    private final AuditService audit;

    /** Outlets the caller may work in. */
    @Transactional(readOnly = true)
    public List<Views.OutletView> list() {
        return access.visibleOutlets(access.user()).stream().map(Views::of).toList();
    }

    /** Every outlet of the business, so stock can be moved to one the caller does not work in. */
    @Transactional(readOnly = true)
    public List<Views.OutletView> transferTargets() {
        access.require(Permission.STOCK_TRANSFER);
        return outletRepository.findAllByTenant(access.tenantId()).stream().map(Views::of).toList();
    }

    @Transactional
    public Views.OutletView create(Dtos.OutletRequest request) {
        access.require(Permission.OUTLET_MANAGE);
        Outlet outlet = new Outlet();
        outlet.setTenantId(access.tenantId());
        apply(outlet, request);
        outlet = outletRepository.save(outlet);
        audit.log("outlet.created", "Outlet", outlet.getId(), outlet.getName(), null, Views.of(outlet), null, outlet.getId());
        return Views.of(outlet);
    }

    @Transactional
    public Views.OutletView update(Long id, Dtos.OutletRequest request) {
        access.require(Permission.OUTLET_MANAGE);
        Outlet outlet = find(id);
        Views.OutletView before = Views.of(outlet);
        apply(outlet, request);
        audit.log("outlet.updated", "Outlet", id, outlet.getName(), before, Views.of(outlet), null, id);
        return Views.of(outlet);
    }

    /** Pausing an outlet stops new orders; reopening lets it trade again. */
    @Transactional
    public Views.OutletView setStatus(Long id, OutletStatus status) {
        access.require(Permission.OUTLET_MANAGE);
        Outlet outlet = find(id);
        OutletStatus before = outlet.getStatus();
        outlet.setStatus(status);
        audit.log(status == OutletStatus.PAUSED ? "outlet.paused" : "outlet.reopened", "Outlet", id, outlet.getName(),
                before, status, null, id);
        return Views.of(outlet);
    }

    private Outlet find(Long id) {
        return outletRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Outlet not found"));
    }

    private void apply(Outlet outlet, Dtos.OutletRequest request) {
        outlet.setName(request.name().trim());
        outlet.setType(request.type() == null ? OutletType.RESTAURANT : request.type());
        outlet.setAddress(request.address());
        outlet.setPhone(request.phone());
    }
}
