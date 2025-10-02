package wiki.creeper.superiorskyblockIntegeration.redis;

/**
 * Applies HMAC signatures to messages.
 */
public final class MessageSecurity {

    private final HmacSigner signer;

    public MessageSecurity(HmacSigner signer) {
        this.signer = signer;
    }

    public void sign(RedisMessage message) {
        message.ensureVersion();
        message.attachSignature(signer.sign(message.canonicalPayload()));
    }

    public boolean verify(RedisMessage message) {
        String provided = message.signature();
        if (provided == null || provided.isBlank()) {
            return false;
        }
        return signer.verify(message.canonicalPayload(), provided);
    }
}
