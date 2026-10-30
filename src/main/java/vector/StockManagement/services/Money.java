package vector.StockManagement.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Amounts as people read them: "1,500 RWF", no decimals. */
final class Money {
    private Money() {}

    static String rwf(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount.setScale(0, RoundingMode.HALF_UP)) + " RWF";
    }
}
