package wiki.creeper.superiorskyblockIntegeration.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported SSB2 network operations.
 */
public enum Operations {
    INVITE_CREATE("invite.create"),
    INVITE_ACCEPT("invite.accept"),
    INVITE_DENY("invite.deny"),
    ISLAND_GET("island.get"),
    MEMBERS_LIST("members.list");

    private final String op;

    Operations(String op) {
        this.op = op;
    }

    public String op() {
        return op;
    }

    public static Optional<Operations> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(op -> op.op.equals(normalised))
                .findFirst();
    }
}
