package wiki.creeper.superiorskyblockIntegeration.redis;

import java.util.Objects;

/**
 * Helper for composing Redis channel names.
 */
public final class RedisChannels {

    private final String prefix;

    public RedisChannels(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    public String requestChannel(String operation) {
        return prefix + ".req." + operation;
    }

    public String responseChannel(String requestId) {
        return prefix + ".resp." + requestId;
    }

    public String eventChannel(String eventType) {
        return prefix + ".evt." + eventType;
    }

    public String requestPattern() {
        return prefix + ".req.*";
    }

    public String responsePattern() {
        return prefix + ".resp.*";
    }

    public String eventPattern() {
        return prefix + ".evt.*";
    }

    public boolean isResponseChannel(String channel) {
        return channel.startsWith(prefix + ".resp.");
    }

    public boolean isEventChannel(String channel) {
        return channel.startsWith(prefix + ".evt.");
    }
}
