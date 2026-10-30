package vector.StockManagement.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @JsonAlias("token")
    private String resetToken;
    private String newPassword;
}
