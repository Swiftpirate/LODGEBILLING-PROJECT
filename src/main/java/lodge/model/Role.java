package lodge.model;

/** What a staff member is allowed to do. */
public enum Role {
    /** Everything, including editing/deleting bills and managing staff accounts. */
    MANAGER("Manager"),
    /** Create bills, record payments, print receipts and view reports. */
    RECEPTIONIST("Receptionist");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
