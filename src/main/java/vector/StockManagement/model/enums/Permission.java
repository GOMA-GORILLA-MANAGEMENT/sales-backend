package vector.StockManagement.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Permission {
    PLATFORM_MANAGE("platform:manage"),
    SETTINGS_MANAGE("settings:manage"),
    OUTLET_MANAGE("outlet:manage"),
    STAFF_MANAGE("staff:manage"),
    MENU_MANAGE("menu:manage"),
    MENU_VIEW("menu:view"),
    ORDER_CREATE("order:create"),
    ORDER_VIEW_ALL("order:view-all"),
    ORDER_APPROVE("order:approve"),
    ORDER_CANCEL_ANY("order:cancel-any"),
    ORDER_CANCEL_OWN("order:cancel-own"),
    ORDER_DELIVER("order:deliver"),
    PAYMENT_TAKE("payment:take"),
    STOCK_VIEW("stock:view"),
    STOCK_RECEIVE("stock:receive"),
    STOCK_TRANSFER("stock:transfer"),
    STOCK_ADJUST("stock:adjust"),
    CUSTOMER_MANAGE("customer:manage"),
    CUSTOMER_VIEW("customer:view"),
    DEBT_VIEW("debt:view"),
    DEBT_ADJUST("debt:adjust"),
    CREDIT_OVERRIDE("credit:override"),
    EXPENSE_MANAGE("expense:manage"),
    EXPENSE_VOID("expense:void"),
    DASHBOARD_VIEW("dashboard:view"),
    REPORT_VIEW("report:view"),
    AUDIT_VIEW("audit:view");

    @Getter
    private final String permission;
}
