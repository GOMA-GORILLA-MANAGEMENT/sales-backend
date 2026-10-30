package vector.StockManagement.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vector.StockManagement.model.Tenant;
import vector.StockManagement.model.User;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.model.enums.Role;
import vector.StockManagement.repositories.TenantRepository;
import vector.StockManagement.services.Access;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final Access access;
    private final TenantRepository tenantRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Views.MeView> me() {
        User user = access.user();
        Tenant tenant = user.getTenant() == null ? null : tenantRepository.findById(user.getTenant().getId()).orElse(null);
        List<Views.OutletView> outlets = user.getRole() == Role.SUPER_ADMIN ? List.of()
                : access.visibleOutlets(user).stream().map(Views::of).toList();
        return ResponseEntity.ok(new Views.MeView(user.getId(), user.getFirstName(), user.getLastName(), user.getFullName(),
                user.getEmail(), user.getRole(),
                user.getRole().getPermissions().stream().map(Permission::getPermission).sorted().toList(),
                tenant == null ? null : tenant.getId(), tenant == null ? "SALES Platform" : tenant.getName(),
                tenant == null || tenant.isApprovalRequired(), tenant != null && tenant.isAllowNegativeStock(), outlets));
    }
}
