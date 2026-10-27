package com.redeploy.auth;

/** 登录失败。captchaRequired 告诉前端下一步要不要显示验证码。 */
public class AuthException extends RuntimeException {
    private final int status;
    private final boolean captchaRequired;

    public AuthException(int status, String message) {
        this(status, message, false);
    }

    public AuthException(int status, String message, boolean captchaRequired) {
        super(message);
        this.status = status;
        this.captchaRequired = captchaRequired;
    }

    public int getStatus() { return status; }
    public boolean isCaptchaRequired() { return captchaRequired; }
}
