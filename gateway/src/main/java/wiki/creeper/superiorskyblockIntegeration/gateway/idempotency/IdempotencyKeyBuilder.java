package wiki.creeper.superiorskyblockIntegeration.gateway.idempotency;

import com.google.gson.JsonObject;

import java.util.StringJoiner;

/**
 * Builds consistent Redis keys to protect write operations.
 */
public final class IdempotencyKeyBuilder {

    private final String prefix;

    public IdempotencyKeyBuilder(String prefix) {
        this.prefix = prefix;
    }

    public String forInviteCreate(String actor, JsonObject data) {
        return key("invite.create", actor, read(data, "target"));
    }

    public String forInviteAccept(String actor) {
        return key("invite.accept", actor);
    }

    public String forInviteDeny(String actor) {
        return key("invite.deny", actor);
    }

    private String key(String op, String... parts) {
        StringJoiner joiner = new StringJoiner(":", prefix + ":", "");
        joiner.add(op);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }

    private static String read(JsonObject data, String field) {
        return data.has(field) ? data.get(field).getAsString() : null;
    }
}
