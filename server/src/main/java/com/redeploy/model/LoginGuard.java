package com.redeploy.model;

import java.time.LocalDateTime;

/** 浏览器维度的验证码状态：通过后 7 天免验，密码错误后重新要求验证码。 */
public class LoginGuard {
    private String clientKey;
    private String captchaCode;
    private LocalDateTime captchaExpireAt;
    private LocalDateTime trustUntil;
    private Integer forceCaptcha;

    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
    public LocalDateTime getCaptchaExpireAt() { return captchaExpireAt; }
    public void setCaptchaExpireAt(LocalDateTime captchaExpireAt) { this.captchaExpireAt = captchaExpireAt; }
    public LocalDateTime getTrustUntil() { return trustUntil; }
    public void setTrustUntil(LocalDateTime trustUntil) { this.trustUntil = trustUntil; }
    public Integer getForceCaptcha() { return forceCaptcha; }
    public void setForceCaptcha(Integer forceCaptcha) { this.forceCaptcha = forceCaptcha; }
}
