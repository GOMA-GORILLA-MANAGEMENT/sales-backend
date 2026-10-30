package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vector.StockManagement.config.TenantContext;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Outlet;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.model.enums.Role;
import vector.StockManagement.repositories.OutletRepository;
import vector.StockManagement.repositories.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Who is calling, which business they act in, which permissions and outlets they have. */
@Component
@RequiredArgsConstructor
public class Access {

    private final UserRepository userRepository;
    private final OutletRepository outletRepository;

    /** The signed-in user, freshly loaded so lazy fields can be read inside the current transaction. */
    public User user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in"));
    }

    /** Snapshot of the caller's identity, readable without a database call. */
    public User principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User principal)) {
            return null;
        }
        return principal;
    }

    /** The business the caller works in. Platform administrators have none. */
    public Long tenantId() {
        Long id = TenantContext.getTenantId();
        if (id == null || id == 0L) {
            throw ApiException.forbidden("This action belongs to a restaurant account");
        }
        return id;
    }

    public void require(Permission permission) {
        User user = principal();
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in");
        if (!user.getRole().has(permission)) {
            throw ApiException.forbidden("You do not have permission to do this");
        }
    }

    public boolean can(Permission permission) {
        User user = principal();
        return user != null && user.getRole().has(permission);
    }

    /** True when the caller sees every outlet of the business (owner, or overall manager with none assigned). */
    public boolean seesAllOutlets(User user) {
        return user.getRole() == Role.OWNER
                || (user.getRole() == Role.OVERALL_MANAGER && user.getOutlets().isEmpty());
    }

    public List<Outlet> visibleOutlets(User user) {
        Long tenantId = tenantId();
        if (seesAllOutlets(user)) {
            return outletRepository.findAllByTenant(tenantId);
        }
        return user.getOutlets().stream()
                .filter(o -> tenantId.equals(o.getTenantId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    public Set<Long> visibleOutletIds(User user) {
        Set<Long> ids = new HashSet<>();
        visibleOutlets(user).forEach(o -> ids.add(o.getId()));
        return ids;
    }

    /** Loads an outlet of the caller's business and checks the caller may work in it. */
    public Outlet outlet(Long outletId) {
        if (outletId == null) throw ApiException.badRequest("Choose an outlet");
        Outlet outlet = outletRepository.findByIdAndTenant(outletId, tenantId())
                .orElseThrow(() -> ApiException.notFound("Outlet not found"));
        User user = user();
        if (!seesAllOutlets(user) && user.getOutlets().stream().noneMatch(o -> o.getId().equals(outletId))) {
            throw ApiException.forbidden("You are not assigned to this outlet");
        }
        return outlet;
    }

    /** Outlets to filter a listing by: one chosen outlet, or everything the caller may see. */
    public Set<Long> scopeOutlets(Long outletId) {
        User user = user();
        if (outletId != null) {
            outlet(outletId);
            return Set.of(outletId);
        }
        Set<Long> ids = visibleOutletIds(user);
        if (ids.isEmpty()) ids.add(-1L);
        return ids;
    }
}
