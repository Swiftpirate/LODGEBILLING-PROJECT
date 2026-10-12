package lodge.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-11T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TempDir
    Path dir;
    private final TestClock clock = new TestClock();
    private UserStore store;
    private AuthService auth;
    private User mary;

    @BeforeEach
    void setUp() throws IOException {
        store = new UserStore(dir.resolve("users.csv"));
        auth = new AuthService(store, clock);
        assertTrue(auth.needsSetup());
        mary = auth.createUser("Mary", "Mary Banda", Role.MANAGER, "lodge2026".toCharArray());
    }

    @Test
    void firstAccountMustBeAManager() throws IOException {
        AuthService fresh = new AuthService(new UserStore(dir.resolve("other.csv")), clock);
        assertThrows(IllegalArgumentException.class,
                () -> fresh.createUser("john", "John Phiri", Role.RECEPTIONIST, "welcome123".toCharArray()));
    }

    @Test
    void signsInWithTheRightPasswordOnly() {
        assertTrue(auth.login("mary", "lodge2026".toCharArray()).ok());
        assertTrue(auth.login("  MARY ", "lodge2026".toCharArray()).ok(), "usernames ignore case and spaces");
        assertFalse(auth.login("mary", "Lodge2026".toCharArray()).ok());
        assertFalse(auth.login("nobody", "lodge2026".toCharArray()).ok());
    }

    @Test
    void passwordIsNotStoredInPlainText() throws IOException {
        String file = Files.readString(store.file());
        assertFalse(file.contains("lodge2026"));
        assertTrue(file.contains("mary"));
    }

    @Test
    void accountsSurviveARestart() throws IOException {
        AuthService restarted = new AuthService(store, clock);
        assertFalse(restarted.needsSetup());
        assertTrue(restarted.login("mary", "lodge2026".toCharArray()).ok());
    }

    @Test
    void locksAfterTooManyWrongPasswords() {
        for (int i = 0; i < AuthService.MAX_ATTEMPTS; i++) {
            assertFalse(auth.login("mary", "wrong-pass1".toCharArray()).ok());
        }
        AuthService.LoginResult locked = auth.login("mary", "lodge2026".toCharArray());
        assertFalse(locked.ok(), "even the right password is refused while locked");
        assertTrue(locked.error().contains("Try again"));

        clock.now = clock.now.plus(AuthService.LOCKOUT).plus(Duration.ofSeconds(1));
        assertTrue(auth.login("mary", "lodge2026".toCharArray()).ok());
    }

    @Test
    void rejectsWeakPasswordsAndBadUsernames() {
        assertThrows(IllegalArgumentException.class,
                () -> auth.createUser("john", "John", Role.RECEPTIONIST, "short1".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> auth.createUser("john", "John", Role.RECEPTIONIST, "onlyletters".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> auth.createUser("j", "John", Role.RECEPTIONIST, "welcome123".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> auth.createUser("MARY", "Another Mary", Role.RECEPTIONIST, "welcome123".toCharArray()));
    }

    @Test
    void changePasswordNeedsTheCurrentOne() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> auth.changePassword(mary, "not-it-123".toCharArray(), "newpass456".toCharArray()));
        auth.changePassword(mary, "lodge2026".toCharArray(), "newpass456".toCharArray());
        assertFalse(auth.login("mary", "lodge2026".toCharArray()).ok());
        assertTrue(auth.login("mary", "newpass456".toCharArray()).ok());
    }

    @Test
    void recoveryKeyResetsPasswordOnceAndIsThenUsedUp() throws IOException {
        String key = auth.issueRecoveryKey(mary);
        assertTrue(key.matches("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}"), key);
        assertFalse(Files.readString(store.file()).contains(RecoveryKey.normalize(key)), "only the hash is saved");

        assertFalse(auth.recover("mary", "AAAA-BBBB-CCCC-DDDD", "newpass456".toCharArray()).ok());
        // Typed in lower case with spaces still works
        AuthService.LoginResult result = auth.recover("mary", key.toLowerCase().replace('-', ' '),
                "newpass456".toCharArray());
        assertTrue(result.ok());
        assertTrue(auth.login("mary", "newpass456".toCharArray()).ok());
        assertFalse(auth.find("mary").orElseThrow().hasRecoveryKey());
        assertFalse(auth.recover("mary", key, "again12345".toCharArray()).ok(), "a key works only once");
    }

    @Test
    void recoveryRejectsWeakPasswordsAndLocksAfterGuessing() throws IOException {
        String key = auth.issueRecoveryKey(mary);
        assertThrows(IllegalArgumentException.class, () -> auth.recover("mary", key, "weak".toCharArray()));
        for (int i = 0; i < AuthService.MAX_ATTEMPTS; i++) {
            auth.recover("mary", "WRNG-WRNG-WRNG-WRNG", "newpass456".toCharArray());
        }
        assertFalse(auth.recover("mary", key, "newpass456".toCharArray()).ok(), "locked after guessing");
    }

    @Test
    void temporaryPasswordsMustBeReplaced() throws IOException {
        User john = auth.createUser("john", "John Phiri", Role.RECEPTIONIST, "welcome123".toCharArray());
        assertTrue(john.mustChangePassword(), "staff created by a manager start with a temporary password");
        assertFalse(mary.mustChangePassword(), "the manager who set up the app chose their own");

        assertThrows(IllegalArgumentException.class,
                () -> auth.replaceTemporaryPassword(john, "welcome123".toCharArray()), "must actually change");
        User changed = auth.replaceTemporaryPassword(john, "johnsown99".toCharArray());
        assertFalse(changed.mustChangePassword());
        assertThrows(IllegalArgumentException.class,
                () -> auth.replaceTemporaryPassword(changed, "another123".toCharArray()));

        auth.resetPassword(mary, "john", "reset12345".toCharArray());
        assertTrue(auth.find("john").orElseThrow().mustChangePassword());
    }

    @Test
    void emergencyResetGivesAWorkingTemporaryPassword() throws IOException {
        String temporary = auth.emergencyReset("MARY");
        assertTrue(auth.login("mary", temporary.toCharArray()).ok());
        assertFalse(auth.login("mary", "lodge2026".toCharArray()).ok());
        assertTrue(auth.find("mary").orElseThrow().mustChangePassword());
        assertThrows(IllegalArgumentException.class, () -> auth.emergencyReset("nobody"));
    }

    @Test
    void readsAccountsSavedBeforeRecoveryKeysExisted() throws IOException {
        Path old = dir.resolve("old-users.csv");
        PasswordHasher.Hashed h = PasswordHasher.hash("oldpass123".toCharArray());
        Files.writeString(old, "username,fullName,role,iterations,salt,passwordHash,createdAt\n"
                + "sam,Sam Tembo,MANAGER," + h.iterations() + "," + h.salt() + "," + h.hash()
                + ",2026-10-11T12:13:42.3362978\n");
        AuthService loaded = new AuthService(new UserStore(old), clock);
        assertTrue(loaded.login("sam", "oldpass123".toCharArray()).ok());
        assertFalse(loaded.find("sam").orElseThrow().hasRecoveryKey());
    }

    @Test
    void onlyManagersManageStaffAndOneManagerAlwaysRemains() throws IOException {
        User john = auth.createUser("john", "John Phiri", Role.RECEPTIONIST, "welcome123".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> auth.removeUser(john, "mary"));
        assertThrows(IllegalArgumentException.class,
                () -> auth.resetPassword(john, "mary", "hacked1234".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> auth.removeUser(mary, "mary"), "cannot remove yourself");
        assertThrows(IllegalArgumentException.class, () -> auth.setRole(mary, "mary", Role.RECEPTIONIST));

        auth.resetPassword(mary, "john", "fresh12345".toCharArray());
        assertTrue(auth.login("john", "fresh12345".toCharArray()).ok());

        auth.removeUser(mary, "john");
        assertFalse(auth.login("john", "fresh12345".toCharArray()).ok());
        assertEquals(1, auth.users().size());
    }
}
