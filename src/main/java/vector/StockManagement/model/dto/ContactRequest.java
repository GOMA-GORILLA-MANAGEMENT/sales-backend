package vector.StockManagement.model.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContactRequest {
    private String fullName;
    private String emailAddress;
    private String message;
    private String subject;

}

