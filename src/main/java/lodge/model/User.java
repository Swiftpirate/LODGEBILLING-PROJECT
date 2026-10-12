package lodge.model;

import java.time.LocalDateTime;

/**
 * A staff account. Only salted PBKDF2 hashes are kept: of the password, and of the recovery key if the
 * account has one. mustChangePassword is set after a temporary password was issued.
 */
public record User(String username, String fullName, Role role,
            int iterations, String salt, String passwordHash, LocalDateTime createdAt,
            boolean mustChangePassword, int recoveryIterations, String recoverySalt, String recoveryHash) {

    public boolean isManager() {
        return role == Role.MANAGER;
    }

    public boolean hasRecoveryKey() {
        return !recoveryHash.isEmpty();
    }

    public User withRole(Role newRole) {
        return new User(username, fullName, newRole, iterations, salt, passwordHash, createdAt,
                mustChangePassword, recoveryIterations, recoverySalt, recoveryHash);
    }

    public User withPassword(PasswordHasher.Hashed hashed, boolean temporary) {
        return new User(username, fullName, role, hashed.iterations(), hashed.salt(), hashed.hash(), createdAt,
                temporary, recoveryIterations, recoverySalt, recoveryHash);
    }

    /** Sets the recovery key hash, or removes the key when hashed is null. */
    public User withRecovery(PasswordHasher.Hashed hashed) {
        return hashed == null
                ? new User(username, fullName, role, iterations, salt, passwordHash, createdAt,
                        mustChangePassword, 0, "", "")
                : new User(username, fullName, role, iterations, salt, passwordHash, createdAt,
                        mustChangePassword, hashed.iterations(), hashed.salt(), hashed.hash());
    }
}
