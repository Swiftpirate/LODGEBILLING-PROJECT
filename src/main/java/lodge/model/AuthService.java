package lodge.model;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Sign-in, recovery and staff account rules. Methods throw IllegalArgumentException with a message that
 * can be shown to the user as-is, and IOException if users.csv cannot be written.
 */
public final class AuthService {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofSeconds(30);

    /** Used to spend the same time on unknown usernames, so timing does not reveal which names exist. */
    private static final String DUMMY_SALT = Base64.getEncoder().encodeToString(new byte[16]);
    private static final String DUMMY_HASH = Base64.getEncoder().encodeToString(new byte[32]);

    public record LoginResult(User user, String error) {
        public boolean ok() {
            return user != null;
        }
    }

    private final UserStore store;
    private final Clock clock;
    private final List<User> users = new ArrayList<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final Map<String, Instant> lockedUntil = new HashMap<>();

    public AuthService(UserStore store, Clock clock) throws IOException {
        this.store = store;
        this.clock = clock;
        users.addAll(store.load());
    }

    public synchronized boolean needsSetup() {
        return users.isEmpty();
    }

    public synchronized List<User> users() {
        return List.copyOf(users);
    }

    public synchronized Optional<User> find(String username) {
        String key = normalize(username);
        return users.stream().filter(u -> u.username().equals(key)).findFirst();
    }

    // ----- Signing in -----

    public synchronized LoginResult login(String username, char[] password) {
        String key = normalize(username);
        Instant now = clock.instant();
        String locked = lockMessage(key, now);
        if (locked != null) {
            return new LoginResult(null, locked);
        }
        Optional<User> user = find(key);
        boolean ok;
        if (user.isPresent()) {
            ok = PasswordHasher.verify(password, user.get().iterations(), user.get().salt(), user.get().passwordHash());
        } else {
            PasswordHasher.verify(password, PasswordHasher.ITERATIONS, DUMMY_SALT, DUMMY_HASH);
            ok = false;
        }
        if (ok) {
            clearFailures(key);
            return new LoginResult(user.get(), null);
        }
        return failure(key, now, "Incorrect username or password.");
    }

    /**
     * "Forgot password": proves who you are with the recovery key instead of the password, then sets a new
     * password. The key is used up; call issueRecoveryKey afterwards to hand out a fresh one.
     */
    public synchronized LoginResult recover(String username, String recoveryKey, char[] newPassword) throws IOException {
        requireStrong(newPassword); // checked first, so a weak password never costs an attempt
        String key = normalize(username);
        Instant now = clock.instant();
        String locked = lockMessage(key, now);
        if (locked != null) {
            return new LoginResult(null, locked);
        }
        char[] typed = RecoveryKey.normalize(recoveryKey).toCharArray();
        Optional<User> user = find(key).filter(User::hasRecoveryKey);
        boolean ok;
        if (user.isPresent()) {
            ok = PasswordHasher.verify(typed, user.get().recoveryIterations(), user.get().recoverySalt(),
                    user.get().recoveryHash());
        } else {
            PasswordHasher.verify(typed, PasswordHasher.ITERATIONS, DUMMY_SALT, DUMMY_HASH);
            ok = false;
        }
        if (!ok) {
            return failure(key, now, "That username and recovery key do not match.");
        }
        clearFailures(key);
        User updated = replace(user.get(),
                user.get().withPassword(PasswordHasher.hash(newPassword), false).withRecovery(null));
        return new LoginResult(updated, null);
    }

    // ----- Passwords and recovery keys -----

    /** Creates a recovery key for this account (replacing any old one) and returns it; only its hash is kept. */
    public synchronized String issueRecoveryKey(User user) throws IOException {
        User stored = existing(user.username());
        String recoveryKey = RecoveryKey.generate();
        replace(stored, stored.withRecovery(PasswordHasher.hash(RecoveryKey.normalize(recoveryKey).toCharArray())));
        return recoveryKey;
    }

    public synchronized User changePassword(User user, char[] current, char[] next) throws IOException {
        User stored = existing(user.username());
        if (!PasswordHasher.verify(current, stored.iterations(), stored.salt(), stored.passwordHash())) {
            throw new IllegalArgumentException("Your current password is not correct.");
        }
        requireStrong(next);
        return replace(stored, stored.withPassword(PasswordHasher.hash(next), false));
    }

    /** After signing in with a temporary password: choose your own. */
    public synchronized User replaceTemporaryPassword(User user, char[] next) throws IOException {
        User stored = existing(user.username());
        if (!stored.mustChangePassword()) {
            throw new IllegalArgumentException("Your password is not a temporary one.");
        }
        requireStrong(next);
        if (PasswordHasher.verify(next, stored.iterations(), stored.salt(), stored.passwordHash())) {
            throw new IllegalArgumentException("Please choose a different password from the temporary one.");
        }
        return replace(stored, stored.withPassword(PasswordHasher.hash(next), false));
    }

    /** A manager sets a temporary password for a staff member, who must change it at next sign-in. */
    public synchronized void resetPassword(User manager, String username, char[] temporary) throws IOException {
        requireManager(manager);
        User stored = existing(username);
        requireStrong(temporary);
        replace(stored, stored.withPassword(PasswordHasher.hash(temporary), !stored.username().equals(manager.username())));
        clearFailures(stored.username());
    }

    /**
     * Last resort when a manager has lost both password and recovery key (run from the command line on the
     * lodge PC). Returns a random temporary password that must be changed at the next sign-in.
     */
    public synchronized String emergencyReset(String username) throws IOException {
        User stored = find(username).orElseThrow(() ->
                new IllegalArgumentException("There is no account called \"" + normalize(username) + "\"."));
        String temporary = RecoveryKey.temporaryPassword();
        replace(stored, stored.withPassword(PasswordHasher.hash(temporary.toCharArray()), true));
        clearFailures(stored.username());
        return temporary;
    }

    // ----- Staff accounts -----

    /** The very first account is the manager who set up the app; everyone else starts with a temporary password. */
    public synchronized User createUser(String username, String fullName, Role role, char[] password) throws IOException {
        String key = normalize(username);
        if (!key.matches("[a-z0-9._-]{3,20}")) {
            throw new IllegalArgumentException(
                    "Username must be 3–20 characters: letters, numbers, dot, dash or underscore (no spaces).");
        }
        if (find(key).isPresent()) {
            throw new IllegalArgumentException("The username \"" + key + "\" is already taken.");
        }
        String name = fullName == null ? "" : fullName.strip();
        if (name.isEmpty() || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("Please enter the staff member's full name.");
        }
        boolean first = users.isEmpty();
        if (first && role != Role.MANAGER) {
            throw new IllegalArgumentException("The first account must be a manager.");
        }
        requireStrong(password);
        User user = new User(key, name, role, 0, "", "", LocalDateTime.now(clock), false, 0, "", "")
                .withPassword(PasswordHasher.hash(password), !first);
        users.add(user);
        saveOrUndo(() -> users.remove(user));
        return user;
    }

    public synchronized void setRole(User manager, String username, Role role) throws IOException {
        requireManager(manager);
        User stored = existing(username);
        if (stored.username().equals(manager.username())) {
            throw new IllegalArgumentException("You cannot change your own role while signed in.");
        }
        if (stored.isManager() && role != Role.MANAGER && managerCount() == 1) {
            throw new IllegalArgumentException("There must always be at least one manager.");
        }
        replace(stored, stored.withRole(role));
    }

    public synchronized void removeUser(User manager, String username) throws IOException {
        requireManager(manager);
        User stored = existing(username);
        if (stored.username().equals(manager.username())) {
            throw new IllegalArgumentException("You cannot remove your own account while signed in.");
        }
        if (stored.isManager() && managerCount() == 1) {
            throw new IllegalArgumentException("There must always be at least one manager.");
        }
        int index = users.indexOf(stored);
        users.remove(index);
        saveOrUndo(() -> users.add(index, stored));
    }

    // ----- Helpers -----

    private String lockMessage(String key, Instant now) {
        Instant until = lockedUntil.get(key);
        if (until != null && now.isBefore(until)) {
            long seconds = Math.max(1, Duration.between(now, until).toSeconds());
            return "Too many failed attempts. Try again in " + seconds + " seconds.";
        }
        return null;
    }

    private LoginResult failure(String key, Instant now, String message) {
        int count = failures.merge(key, 1, Integer::sum);
        if (count >= MAX_ATTEMPTS) {
            failures.remove(key);
            lockedUntil.put(key, now.plus(LOCKOUT));
            return new LoginResult(null, "Too many failed attempts. Locked for " + LOCKOUT.toSeconds() + " seconds.");
        }
        int left = MAX_ATTEMPTS - count;
        return new LoginResult(null, message
                + (left <= 2 ? " " + left + " attempt" + (left == 1 ? "" : "s") + " left." : ""));
    }

    private void clearFailures(String key) {
        failures.remove(key);
        lockedUntil.remove(key);
    }

    private User existing(String username) {
        return find(username).orElseThrow(() -> new IllegalArgumentException("Account no longer exists."));
    }

    private User replace(User old, User updated) throws IOException {
        int index = users.indexOf(old);
        users.set(index, updated);
        saveOrUndo(() -> users.set(index, old));
        return updated;
    }

    /** Keeps memory and file in step: if saving fails, the change is rolled back. */
    private void saveOrUndo(Runnable undo) throws IOException {
        try {
            store.save(users);
        } catch (IOException e) {
            undo.run();
            throw e;
        }
    }

    private long managerCount() {
        return users.stream().filter(User::isManager).count();
    }

    private void requireManager(User user) {
        if (user == null || find(user.username()).filter(User::isManager).isEmpty()) {
            throw new IllegalArgumentException("Only a manager can do that.");
        }
    }

    private static void requireStrong(char[] password) {
        String weakness = PasswordHasher.weakness(password);
        if (weakness != null) {
            throw new IllegalArgumentException(weakness);
        }
    }

    public static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
