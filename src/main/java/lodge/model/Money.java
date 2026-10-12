package lodge.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Kwacha amounts: always two decimal places, formatted the same on every PC. */
public final class Money {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    // Fixed locale so a PC set to use "," as the decimal separator still shows K 1,234.50
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    private Money() {
    }

    public static BigDecimal of(String text) {
        return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal parse(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? ZERO : of(trimmed);
    }

    public static String format(BigDecimal value) {
        return "K " + new DecimalFormat("#,##0.00", SYMBOLS).format(value.setScale(2, RoundingMode.HALF_UP));
    }

    public static String format(double value) {
        return format(BigDecimal.valueOf(value));
    }
}
