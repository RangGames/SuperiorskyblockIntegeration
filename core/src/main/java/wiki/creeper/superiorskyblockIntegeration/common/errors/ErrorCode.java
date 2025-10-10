package wiki.creeper.superiorskyblockIntegeration.common.errors;

/**
 * Known error codes shared between client and gateway.
 */
public enum ErrorCode {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    TIMEOUT,
    INTERNAL,

    INVITE_ALREADY_EXISTS,
    INVITE_NOT_FOUND,
    INVITE_EXPIRED,
    ISLAND_NOT_FOUND,
    MEMBER_LIMIT_REACHED,
    ALREADY_MEMBER,
    TARGET_OFFLINE,
    PLAYER_OFFLINE,
    COOLDOWN_ACTIVE,
    UNKNOWN_OPERATION,
    QUEST_ALREADY_ASSIGNED,
    QUEST_NOT_ASSIGNED,
    QUEST_NOT_FOUND,
    QUEST_PERMISSION_DENIED;

    public String code() {
        return name();
    }
}
