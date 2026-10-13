package com.tencent.wxcloudrun.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final WechatClient wechat;
    private final SecureRandom random = new SecureRandom();
    public AuthService(JdbcTemplate db, WechatClient wechat) { this.db = db; this.wechat = wechat; }
    public Map<String, Object> login(String code) {
        String openid = wechat.exchange(code);
        List<String> found = db.queryForList("SELECT id FROM qy_users WHERE openid=?", String.class, openid);
        String userId;
        if (found.isEmpty()) {
            userId = UUID.randomUUID().toString();
            String account = "qy_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            try {
                db.update("INSERT INTO qy_users(id,openid,name,account) VALUES(?,?,?,?)", userId, openid, "拾痕用户", account);
            } catch (DuplicateKeyException e) {
                userId = db.queryForObject("SELECT id FROM qy_users WHERE openid=?", String.class, openid);
            }
        } else userId = found.get(0);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        db.update("DELETE FROM qy_sessions WHERE user_id=? AND expires_at<CURRENT_TIMESTAMP", userId);
        db.update("INSERT INTO qy_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)", hash(token), userId, Timestamp.from(expiry));
        return Map.of("token", token, "expiresAt", expiry.toString(), "userId", userId);
    }
    public String require(String header) {
        if (header == null || !header.matches("Bearer [A-Za-z0-9_-]{43}")) throw new ApiError(401, "请先登录");
        List<String> found = db.queryForList("SELECT user_id FROM qy_sessions WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP",
                String.class, hash(header.substring(7)));
        if (found.isEmpty()) throw new ApiError(401, "登录已过期，请重新登录");
        return found.get(0);
    }
    public void logout(String header) {
        require(header);
        db.update("DELETE FROM qy_sessions WHERE token_hash=?", hash(header.substring(7)));
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
