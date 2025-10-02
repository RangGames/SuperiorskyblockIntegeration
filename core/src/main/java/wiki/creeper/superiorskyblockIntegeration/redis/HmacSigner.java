package wiki.creeper.superiorskyblockIntegeration.redis;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility for HMAC-SHA256 signatures.
 */
public final class HmacSigner {

    private final byte[] key;

    public HmacSigner(String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign payload", ex);
        }
    }

    public boolean verify(String payload, String signature) {
        return sign(payload).equals(signature);
    }
}
