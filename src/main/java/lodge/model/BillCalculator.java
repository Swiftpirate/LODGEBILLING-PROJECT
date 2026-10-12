package lodge.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Billing rules, kept free of JavaFX so they can be unit tested. */
public final class BillCalculator {

    public static final int DISCOUNT_MIN_NIGHTS = 5;
    public static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.05");

    public record Breakdown(BigDecimal roomCharge, BigDecimal discount, BigDecimal extras, BigDecimal total) {
    }

    private BillCalculator() {
    }

    /** Room charge, minus 5% for long stays, plus extras (extras are never discounted, no tax). */
    public static Breakdown calculate(RoomType room, int nights, BigDecimal extras) {
        if (nights < 1) {
            throw new IllegalArgumentException("Nights must be at least 1");
        }
        if (extras.signum() < 0) {
            throw new IllegalArgumentException("Extras cannot be negative");
        }
        BigDecimal roomCharge = room.rate().multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = nights >= DISCOUNT_MIN_NIGHTS
                ? roomCharge.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP)
                : Money.ZERO;
        BigDecimal cleanExtras = extras.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = roomCharge.subtract(discount).add(cleanExtras);
        return new Breakdown(roomCharge, discount, cleanExtras, total);
    }
}
