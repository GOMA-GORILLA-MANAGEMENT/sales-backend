package vector.StockManagement.model.enums;

import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static vector.StockManagement.model.enums.Permission.*;

public enum Role {
    SUPER_ADMIN(EnumSet.of(PLATFORM_MANAGE)),

    OWNER(allExcept(PLATFORM_MANAGE)),

    OVERALL_MANAGER(allExcept(PLATFORM_MANAGE, SETTINGS_MANAGE)),

    MANAGER(EnumSet.of(
            MENU_VIEW, MENU_MANAGE, ORDER_VIEW_ALL, ORDER_APPROVE, ORDER_CANCEL_ANY, ORDER_DELIVER, PAYMENT_TAKE,
            STOCK_VIEW, STOCK_RECEIVE, STOCK_TRANSFER, STOCK_ADJUST,
            CUSTOMER_MANAGE, CUSTOMER_VIEW, DEBT_VIEW, EXPENSE_MANAGE, DASHBOARD_VIEW, REPORT_VIEW)),

    WAITER(EnumSet.of(
            MENU_VIEW, ORDER_CREATE, ORDER_CANCEL_OWN, ORDER_DELIVER, PAYMENT_TAKE,
            CUSTOMER_VIEW, DEBT_VIEW));

    @Getter
    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    private static Set<Permission> allExcept(Permission... excluded) {
        EnumSet<Permission> set = EnumSet.allOf(Permission.class);
        for (Permission p : excluded) set.remove(p);
        return set;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** Roles a user holding this role may create. */
    public Set<Role> assignableRoles() {
        return switch (this) {
            case OWNER -> EnumSet.of(OVERALL_MANAGER, MANAGER, WAITER);
            case OVERALL_MANAGER -> EnumSet.of(MANAGER, WAITER);
            default -> EnumSet.noneOf(Role.class);
        };
    }

    public List<SimpleGrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Permission p : permissions) authorities.add(new SimpleGrantedAuthority(p.getPermission()));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + name()));
        return authorities;
    }
}
