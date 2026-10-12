package lodge.model;

import java.security.SecureRandom;
import java.util.Locale;

/** Recovery keys (e.g. K7QF-9MZD-2HXA-PL4T) and temporary passwords. */
final class RecoveryKey {

    /** No 0/O, 1/I/L, so keys are easy to read back from paper. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryKey() {
    }

    /** 16 random characters (about 79 bits), in four groups of four. */
    static String generate() {
        return groups(4);
    }

    /** A strong one-time password such as "Temp-7KQ4-M2XA-9PDF" (always has letters and digits). */
    static String temporaryPassword() {
        String body;
        do {
            body = groups(3);
        } while (!body.matches(".*\\d.*"));
        return "Temp-" + body;
    }

    /** Ignores case, spaces and dashes, so "k7qf 9mzd-2hxa pl4t" matches K7QF-9MZD-2HXA-PL4T. */
    static String normalize(String key) {
        return key == null ? "" : key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String groups(int count) {
        StringBuilder key = new StringBuilder();
        for (int g = 0; g < count; g++) {
            if (g > 0) {
                key.append('-');
            }
            for (int i = 0; i < 4; i++) {
                key.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return key.toString();
    }
}
