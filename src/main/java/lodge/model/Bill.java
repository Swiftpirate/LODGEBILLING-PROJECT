package lodge.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A saved bill. The charges are stored, so later price changes never alter old bills.
 * createdBy is the username of the staff member who saved it ("" for bills from before logins).
 */
public record Bill(String id, String guest, RoomType room, LocalDate checkIn, int nights,
            BigDecimal roomCharge, BigDecimal discount, BigDecimal extras,
            BigDecimal total, BigDecimal paid, String notes, LocalDateTime createdAt, String createdBy) {

    public LocalDate checkOut() {
        return checkIn.plusDays(nights);
    }

    public BigDecimal balance() {
        return total.subtract(paid);
    }

    public boolean settled() {
        return balance().signum() <= 0;
    }

    public Bill withPayment(BigDecimal amount) {
        return new Bill(id, guest, room, checkIn, nights, roomCharge, discount, extras,
                total, paid.add(amount), notes, createdAt, createdBy);
    }
}
