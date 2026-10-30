package vector.StockManagement.exceptions;

public class TenantException extends RuntimeException {
    private final Long tenantId;
    private final String operation;

    public TenantException(String message, Long tenantId, String operation) {
        super(message);
        this.tenantId = tenantId;
        this.operation = operation;
    }

    public TenantException(String message, Long tenantId, String operation, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
        this.operation = operation;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getOperation() {
        return operation;
    }
}
