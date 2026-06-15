package com.yunmo.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 简易 JWT 工具 — HMAC-SHA256 签名
 */
public class JwtUtil {

    public static String create(String secret, String userId, long ttlMillis) {
        long now = System.currentTimeMillis();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + userId + "\",\"iat\":" + (now / 1000)
                + ",\"exp\":" + ((now + ttlMillis) / 1000) + "}");
        String signature = base64Url(hmacSha256(secret, header + "." + payload));
        return header + "." + payload + "." + signature;
    }

    public static String verifyAndGetSubject(String secret, String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String expectedSig = base64Url(hmacSha256(secret, parts[0] + "." + parts[1]));
            if (!expectedSig.equals(parts[2])) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // 验证过期时间（exp）
            long now = System.currentTimeMillis() / 1000;
            int expIdx = payload.indexOf("\"exp\":");
            if (expIdx >= 0) {
                int numStart = expIdx + 6;
                int numEnd = numStart;
                while (numEnd < payload.length() && Character.isDigit(payload.charAt(numEnd))) numEnd++;
                if (numEnd > numStart) {
                    long exp = Long.parseLong(payload.substring(numStart, numEnd));
                    if (now > exp) return null; // token 已过期
                }
            }

            // 提取 sub
            int start = payload.indexOf("\"sub\":\"");
            if (start < 0) return null;
            start += 7;
            int end = payload.indexOf('"', start);
            return end > start ? payload.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 失败", e);
        }
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
