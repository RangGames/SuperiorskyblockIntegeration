package wiki.creeper.superiorskyblockIntegeration.common;

/**
 * Simple lifecycle contract for gateway/client components.
 */
public interface ComponentLifecycle {

    /**
     * Initialise and register listeners/resources.
     */
    void start();

    /**
     * Shut down listeners/resources.
     */
    void stop();
}
