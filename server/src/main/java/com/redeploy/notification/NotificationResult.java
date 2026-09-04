package com.redeploy.notification;

/** 通道无关的发送结果。 */
public class NotificationResult {

    private final boolean success;
    private final String errorMessage;

    private NotificationResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static NotificationResult ok() {
        return new NotificationResult(true, null);
    }

    public static NotificationResult fail(String error) {
        return new NotificationResult(false, error);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}