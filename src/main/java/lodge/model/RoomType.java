package lodge.model;

import java.math.BigDecimal;

/** The single place room names and nightly rates are defined. */
public enum RoomType {
    STANDARD("Standard", "350.00"),
    DELUXE("Deluxe", "550.00"),
    FAMILY("Family", "750.00");

    private final String label;
    private final BigDecimal rate;

    RoomType(String label, String rate) {
        this.label = label;
        this.rate = Money.of(rate);
    }

    public BigDecimal rate() {
        return rate;
    }

    @Override
    public String toString() {
        return label;
    }
}
