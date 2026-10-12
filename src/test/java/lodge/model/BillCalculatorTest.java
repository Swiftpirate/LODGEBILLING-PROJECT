package lodge.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillCalculatorTest {

    @Test
    void shortStayHasNoDiscount() {
        var b = BillCalculator.calculate(RoomType.STANDARD, 4, Money.ZERO);
        assertEquals(Money.of("1400"), b.roomCharge());
        assertEquals(Money.ZERO, b.discount());
        assertEquals(Money.of("1400"), b.total());
    }

    @Test
    void fiveNightsGetsFivePercentOffTheRoomCharge() {
        var b = BillCalculator.calculate(RoomType.DELUXE, 5, Money.ZERO);
        assertEquals(Money.of("2750"), b.roomCharge());
        assertEquals(Money.of("137.50"), b.discount());
        assertEquals(Money.of("2612.50"), b.total());
    }

    @Test
    void extrasAreNeverDiscounted() {
        var b = BillCalculator.calculate(RoomType.FAMILY, 6, Money.of("100"));
        // 750 x 6 = 4500, minus 225 discount, plus the full 100 of extras
        assertEquals(Money.of("4375"), b.total());
    }

    @Test
    void rejectsImpossibleInput() {
        assertThrows(IllegalArgumentException.class,
                () -> BillCalculator.calculate(RoomType.STANDARD, 0, Money.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> BillCalculator.calculate(RoomType.STANDARD, 1, new BigDecimal("-1")));
    }

    @Test
    void moneyFormatIgnoresTheComputerLocale() {
        assertEquals("K 1,234.50", Money.format(new BigDecimal("1234.5")));
        assertEquals("K 0.00", Money.format(0));
        assertEquals(Money.of("12.35"), Money.parse("12.345"));
        assertEquals(Money.ZERO, Money.parse("  "));
    }
}
