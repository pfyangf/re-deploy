package com.redeploy.notification;

/**
 * 通道无关的消息中间表示 — dispatcher 渲染 SystemEvent 为 NotificationMessage，
 * 再交给通道实现做通道特定的格式转换。
 */
public class NotificationMessage {

    private final String title;
    private final String markdown;        // Markdown 内容（钉钉用）
    private final String text;            // 纯文本（企微 / webhook 备用）
    private final String html;            // HTML 内容（邮件用）
    private final String subject;         // 邮件主题

    public NotificationMessage(String title, String markdown, String text, String html, String subject) {
        this.title = title;
        this.markdown = markdown;
        this.text = text;
        this.html = html;
        this.subject = subject;
    }

    /**
     * 便捷构造：title + 统一正文。
     * 正文同时作为 markdown（钉钉）与 text（企微/webhook/邮件）内容，subject 回退为 title。
     */
    public NotificationMessage(String title, String body) {
        this(title, body, body, null, title);
    }

    public String getTitle() { return title; }
    public String getMarkdown() { return markdown; }
    public String getText() { return text; }
    public String getHtml() { return html; }
    public String getSubject() { return subject; }
}