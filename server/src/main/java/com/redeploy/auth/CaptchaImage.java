package com.redeploy.auth;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

public final class CaptchaImage {
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaImage() {}

    public static String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static byte[] png(String code) throws IOException {
        int w = 132;
        int h = 44;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(18, 23, 29));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(40 + RANDOM.nextInt(30), 48 + RANDOM.nextInt(30), 58 + RANDOM.nextInt(30)));
            g.drawLine(RANDOM.nextInt(w), RANDOM.nextInt(h), RANDOM.nextInt(w), RANDOM.nextInt(h));
        }
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(190 + RANDOM.nextInt(50), 198 + RANDOM.nextInt(40), 206 + RANDOM.nextInt(40)));
            int y = 30 + RANDOM.nextInt(6) - 2;
            g.drawString(String.valueOf(code.charAt(i)), 14 + i * 28, y);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
