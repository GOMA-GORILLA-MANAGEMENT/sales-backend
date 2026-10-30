package vector.StockManagement.controllers;

import java.time.LocalDate;

final class Api {
    private Api() {}

    static LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
