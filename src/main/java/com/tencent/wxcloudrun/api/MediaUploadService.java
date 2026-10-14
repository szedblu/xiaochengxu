package com.tencent.wxcloudrun.api;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small authenticated requests avoid the callContainer request-size ceiling. */
@Service
public class MediaUploadService {
    static final int CHUNK_SIZE = 48 * 1024;
    private final JdbcTemplate db;
    private final LifeService life;
    public MediaUploadService(JdbcTemplate db, LifeService life) { this.db = db; this.life = life; }

    @Transactional
    public Map<String, Object> start(String user, Map<String, Object> data) {
        int size = Input.decimal(data.get("size"), 1, 2 * 1024 * 1024, 0).intValueExact();
        int parts = Input.decimal(data.get("parts"), 1, 43, 0).intValueExact();
        if (parts != (size + CHUNK_SIZE - 1) / CHUNK_SIZE) throw new ApiError(400, "图片分块数量不正确");
        // Serialize creation per user to bound incomplete uploads, including concurrent requests.
        db.queryForObject("SELECT id FROM qy_users WHERE id=? FOR UPDATE", String.class, user);
        db.update("DELETE FROM qy_uploads WHERE user_id=? AND expires_at<?", user, Timestamp.from(Instant.now()));
        if (db.queryForObject("SELECT COUNT(*) FROM qy_uploads WHERE user_id=?", Integer.class, user) >= 3)
            throw new ApiError(429, "未完成的上传过多，请稍后重试");
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO qy_uploads(id,user_id,byte_size,part_count,expires_at) VALUES(?,?,?,?,?)",
                id, user, size, parts, Timestamp.from(Instant.now().plusSeconds(600)));
        return Map.of("uploadId", id, "chunkSize", CHUNK_SIZE);
    }
    private Map<String, Object> owned(String user, String id) {
        List<Map<String, Object>> found = db.queryForList("SELECT byte_size,part_count FROM qy_uploads WHERE id=? AND user_id=? AND expires_at>? FOR UPDATE",
                id, user, Timestamp.from(Instant.now()));
        if (found.isEmpty()) throw new ApiError(404, "图片上传已过期，请重新选择图片");
        return found.get(0);
    }
    @Transactional
    public void part(String user, String id, int index, Map<String, Object> data) {
        Map<String, Object> upload = owned(user, id);
        int size = ((Number) upload.get("byte_size")).intValue(), count = ((Number) upload.get("part_count")).intValue();
        if (index < 0 || index >= count) throw new ApiError(400, "图片分块序号不正确");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(Input.text(data, "base64", CHUNK_SIZE * 4 / 3, true)); }
        catch (IllegalArgumentException e) { throw new ApiError(400, "图片分块格式不正确", e); }
        if (bytes.length != Math.min(CHUNK_SIZE, size - index * CHUNK_SIZE)) throw new ApiError(400, "图片分块大小不正确");
        db.update("INSERT INTO qy_upload_parts(upload_id,part_index,content) VALUES(?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content)", id, index, bytes);
    }
    @Transactional
    public String complete(String user, String id) {
        Map<String, Object> upload = owned(user, id);
        List<byte[]> parts = db.query("SELECT content FROM qy_upload_parts WHERE upload_id=? ORDER BY part_index", (r, i) -> r.getBytes(1), id);
        if (parts.size() != ((Number) upload.get("part_count")).intValue()) throw new ApiError(400, "图片尚未上传完成");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        parts.forEach(bytes::writeBytes);
        if (bytes.size() != ((Number) upload.get("byte_size")).intValue()) throw new ApiError(400, "图片大小不正确");
        String url = life.saveImage(user, bytes.toByteArray());
        db.update("DELETE FROM qy_uploads WHERE id=?", id);
        return url;
    }
    @Transactional
    public void cancel(String user, String id) {
        db.update("DELETE FROM qy_uploads WHERE id=? AND user_id=?", id, user);
    }
}
