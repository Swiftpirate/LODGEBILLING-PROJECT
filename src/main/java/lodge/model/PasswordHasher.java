package lodge.model;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2-HMAC-SHA256 password hashing with a random salt per password. */
public final class PasswordHasher {

    public static final int ITERATIONS = 210_000; // OWASP recommendation for PBKDF2-HMAC-SHA256
    public static final int MIN_LENGTH = 8;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Hashed(int iterations, String salt, String hash) {
    }

    private PasswordHasher() {
    }

    public static Hashed hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return new Hashed(ITERATIONS, b64.encodeToString(salt), b64.encodeToString(pbkdf2(password, salt, ITERATIONS)));
    }

    public static boolean verify(char[] password, int iterations, String salt, String hash) {
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] expected = b64.decode(hash);
        byte[] actual = pbkdf2(password, b64.decode(salt), iterations);
        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    /** Returns a reason the password is too weak, or null if it is acceptable. */
    public static String weakness(char[] password) {
        if (password.length < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : password) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            return "Password must contain both letters and numbers.";
        }
        return null;
    }

    /** 0 = empty, 1 = weak, 2 = fair, 3 = strong. Only used for the strength hint while typing. */
    public static int strength(char[] password) {
        if (password.length == 0) {
            return 0;
        }
        int classes = 0;
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : password) {
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        for (boolean b : new boolean[]{lower, upper, digit, symbol}) {
            if (b) classes++;
        }
        if (weakness(password) != null) {
            return 1;
        }
        return password.length >= 12 && classes >= 3 ? 3 : 2;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
