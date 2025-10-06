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

    public String busChannel(String topic) {
        return prefix + ".bus." + topic;
    }

    public String busPattern() {
        return prefix + ".bus.*";
    }

    public boolean isBusChannel(String channel) {
        return channel.startsWith(prefix + ".bus.");
    }

    public String busTopic(String channel) {
        if (!isBusChannel(channel)) {
            return channel;
        }
        return channel.substring((prefix + ".bus.").length());
    }
}
