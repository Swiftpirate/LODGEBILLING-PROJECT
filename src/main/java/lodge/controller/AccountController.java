package lodge.controller;

import javafx.stage.Window;
import lodge.model.AuthService;
import lodge.model.Role;
import lodge.model.User;
import lodge.view.AccountDialogs;
import lodge.view.Ui;

import java.io.IOException;
import java.util.List;

/** The Account menu: your own password and recovery key, and (for managers) the staff accounts. */
public final class AccountController implements AccountDialogs.StaffActions {

    private final Window owner;
    private final AuthService auth;
    private final User user;

    public AccountController(Window owner, AuthService auth, User user) {
        this.owner = owner;
        this.auth = auth;
        this.user = user;
    }

    public boolean changePassword() {
        return AccountDialogs.changePassword(owner, user, (current, next, confirm) -> {
            if (!next.equals(confirm)) {
                return "The new passwords do not match.";
            }
            auth.changePassword(user, current.toCharArray(), next.toCharArray());
            return null;
        });
    }

    /** Makes a new recovery key for the signed-in manager (the old one stops working) and shows it once. */
    public boolean newRecoveryKey() {
        if (!Ui.confirm(owner, "Create a new recovery key?",
                "Your current recovery key will stop working. The new key is shown only once, so have "
                        + "paper ready to write it down.", "Create new key")) {
            return false;
        }
        String key;
        try {
            key = auth.issueRecoveryKey(user);
        } catch (IOException | IllegalArgumentException e) {
            Ui.warn(owner, "Could not create a recovery key: " + e.getMessage());
            return false;
        }
        AccountDialogs.showRecoveryKey(owner, user, key);
        return true;
    }

    public void manageStaff() {
        AccountDialogs.manageStaff(owner, this);
    }

    // ===================================================================== Staff dialog buttons

    @Override
    public List<User> users() {
        return auth.users();
    }

    @Override
    public boolean add(Window here) {
        return AccountDialogs.addStaff(here, (fullName, username, role, password, confirm) -> {
            if (!password.equals(confirm)) {
                return "The passwords do not match.";
            }
            auth.createUser(username, fullName, role, password.toCharArray());
            return null;
        });
    }

    @Override
    public boolean resetPassword(Window here, User target) {
        boolean done = AccountDialogs.resetPassword(here, target, (password, confirm) -> {
            if (!password.equals(confirm)) {
                return "The passwords do not match.";
            }
            auth.resetPassword(user, target.username(), password.toCharArray());
            return null;
        });
        if (done) {
            Ui.info(here, "Password reset", "The password for " + target.username() + " has been reset.\n\n"
                    + "Give them the temporary password. They will be asked to choose their own when they sign in.");
        }
        return false;
    }

    @Override
    public boolean toggleRole(Window here, User target) {
        Role newRole = target.isManager() ? Role.RECEPTIONIST : Role.MANAGER;
        try {
            auth.setRole(user, target.username(), newRole);
            return true;
        } catch (IllegalArgumentException | IOException e) {
            Ui.warn(here, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean remove(Window here, User target) {
        if (!Ui.confirm(here, "Remove " + target.username() + "?",
                target.fullName() + " will no longer be able to sign in. Their saved bills are kept.",
                "Remove account")) {
            return false;
        }
        try {
            auth.removeUser(user, target.username());
            return true;
        } catch (IllegalArgumentException | IOException e) {
            Ui.warn(here, e.getMessage());
            return false;
        }
    }
}
