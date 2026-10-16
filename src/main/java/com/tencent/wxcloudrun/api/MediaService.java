package com.tencent.wxcloudrun.api;

import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaService {
    private final JdbcTemplate db;
    private final String publicUrl;
    public MediaService(JdbcTemplate db, @Value("${app.public-url}") String publicUrl) { this.db=db; this.publicUrl=publicUrl.replaceAll("/+$", ""); }
    String saveImage(String user, byte[] bytes) {
        if (bytes.length > 2 * 1024 * 1024) throw new ApiError(400, "图片需小于 2 MB");
        String mime;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) mime = "image/jpeg";
        else if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[] {(byte)137,80,78,71,13,10,26,10})) mime = "image/png";
        else throw new ApiError(400, "请选择 JPG 或 PNG 图片");
        // 校验真实图像，拒绝仅伪装文件头的数据及超大像素图。
        try {
            javax.imageio.stream.ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            if (stream == null) throw new IllegalArgumentException();
            try (stream) {
                java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                javax.imageio.ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000) throw new IllegalArgumentException();
                    if (reader.read(0) == null) throw new IllegalArgumentException();
                } finally { reader.dispose(); }
            }
        } catch (Exception e) { throw new ApiError(400, "图片无效或尺寸过大", e); }
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO cf_media(id,user_id,mime,content) VALUES(?,?,?,?)", id, user, mime, bytes);
        return publicUrl + "/api/media/" + id;
    }
    public Map<String, Object> media(String id) {
        List<Map<String, Object>> found = db.query("SELECT mime,content FROM cf_media WHERE id=?", (r, i) -> Map.of("mime", r.getString("mime"), "bytes", r.getBytes("content")), id);
        if (found.isEmpty()) throw new ApiError(404, "图片不存在");
        return found.get(0);
    }
}
