package wiki.creeper.superiorskyblockIntegeration.client.cache;

/**
 * Utility for constructing cache keys consistently.
 */
public final class ClientCacheKeys {

    private ClientCacheKeys() {
    }

    public static String island(String ownerOrActorUuid) {
        return "island:" + ownerOrActorUuid;
    }

    public static String members(String islandId) {
        return "members:" + islandId;
    }

    public static String invites(String playerUuid) {
        return "invites:" + playerUuid;
    }
}
