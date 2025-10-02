package wiki.creeper.superiorskyblockIntegeration.gateway.errors;

import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;

public final class GatewayException extends RuntimeException {

    private final ErrorCode code;
    private final boolean retryable;

    public GatewayException(ErrorCode code, String message) {
        this(code, message, false, null);
    }

    public GatewayException(ErrorCode code, String message, boolean retryable) {
        this(code, message, retryable, null);
    }

    public GatewayException(ErrorCode code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public ErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
