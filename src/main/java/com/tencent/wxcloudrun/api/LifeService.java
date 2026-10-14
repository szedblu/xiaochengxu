package com.tencent.wxcloudrun.api;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifeService {
    private final JdbcTemplate db;
    private final String publicUrl;
    private static final Set<String> EXPENSE = Set.of("food", "shop", "traffic", "daily", "fun", "home", "medical", "study", "gift", "other");
    private static final Set<String> INCOME = Set.of("salary", "bonus", "finance", "parttime", "otherIncome");
    public LifeService(JdbcTemplate db, @Value("${app.public-url}") String publicUrl) {
        this.db = db; this.publicUrl = publicUrl.replaceAll("/+$", "");
    }
    private Map<String, Object> publicUser(ResultSet r) throws SQLException {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", r.getString("id")); u.put("name", r.getString("name"));
        u.put("account", r.getString("account")); u.put("avatar", r.getString("avatar")); u.put("bio", r.getString("intro"));
        return u;
    }
    public Map<String, Object> profile(String user) {
        return db.queryForObject("SELECT * FROM qy_users WHERE id=?", (r, i) -> {
            Map<String, Object> p = publicUser(r);
            p.put("intro", r.getString("intro"));
            p.put("birthday", r.getDate("birthday") == null ? "" : r.getDate("birthday").toLocalDate().toString());
            p.put("heightCm", r.getBigDecimal("height_cm")); p.put("goalWeight", r.getBigDecimal("goal_weight"));
            return p;
        }, user);
    }
    public Map<String, Object> bootstrap(String user) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> p = profile(user);
        List<Map<String, Object>> users = db.query("SELECT * FROM qy_users WHERE id=? OR id IN (SELECT friend_id FROM qy_friends WHERE user_id=?)",
                (r, i) -> publicUser(r), user, user);
        result.put("profile", p); result.put("users", users); result.put("meId", user);
        result.put("today", Input.today().toString()); result.put("goalWeight", p.get("goalWeight"));
        result.put("friendIds", db.queryForList("SELECT friend_id FROM qy_friends WHERE user_id=?", String.class, user));
        result.put("posts", posts(user));
        result.put("checkins", db.query("SELECT entry_date FROM qy_checkins WHERE user_id=? ORDER BY entry_date", (r, i) -> r.getDate(1).toLocalDate().toString(), user));
        result.put("schedules", db.query("SELECT * FROM qy_schedules WHERE user_id=? ORDER BY entry_date,id", (r, i) -> Map.of(
                "id", r.getString("id"), "title", r.getString("title"), "type", r.getString("entry_type"),
                "date", r.getDate("entry_date").toLocalDate().toString(), "note", r.getString("note")), user));
        result.put("weights", db.query("SELECT * FROM qy_weights WHERE user_id=? ORDER BY entry_date", (r, i) -> Map.of(
                "date", r.getDate("entry_date").toLocalDate().toString(), "value", r.getBigDecimal("weight")), user));
        result.put("ledger", db.query("SELECT * FROM qy_ledger WHERE user_id=? ORDER BY entry_date DESC,id", (r, i) -> Map.of(
                "id", r.getString("id"), "type", r.getString("entry_type"), "category", r.getString("category"),
                "amount", r.getBigDecimal("amount"), "note", r.getString("note"), "date", r.getTimestamp("entry_date").toLocalDateTime().toString()), user));
        return result;
    }
    private List<Map<String, Object>> posts(String user) {
        return db.query("SELECT p.*, (SELECT COUNT(*) FROM qy_likes l WHERE l.post_id=p.id) AS total_likes, "
                + "(SELECT COUNT(*) FROM qy_likes l WHERE l.post_id=p.id AND l.user_id=?) AS my_like "
                + "FROM qy_posts p WHERE p.user_id=? OR p.user_id IN (SELECT friend_id FROM qy_friends WHERE user_id=?) ORDER BY p.created_at DESC,p.id",
                (r, i) -> {
                    Map<String, Object> post = new LinkedHashMap<>();
                    post.put("id", r.getString("id")); post.put("authorId", r.getString("user_id"));
                    post.put("content", Arrays.asList(r.getString("content").split("\n")));
                    post.put("cover", r.getString("cover"));
                    post.put("time", r.getTimestamp("created_at").toLocalDateTime().format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")));
                    post.put("liked", r.getInt("my_like") > 0); post.put("likes", r.getInt("total_likes") - r.getInt("my_like"));
                    return post;
                }, user, user, user);
    }
    public void updateProfile(String user, Map<String, Object> data) {
        Set<String> allowed = Set.of("name", "intro", "account", "birthday", "heightCm", "avatar");
        if (data.isEmpty() || !allowed.containsAll(data.keySet())) throw new ApiError(400, "不支持的资料字段");
        List<String> fields = new ArrayList<>(); List<Object> args = new ArrayList<>();
        for (String key : data.keySet()) {
            Object value;
            String column = key;
            switch (key) {
                case "name": value = Input.text(data, key, 20, true); break;
                case "intro": value = Input.text(data, key, 100, false); break;
                case "account":
                    value = Input.text(data, key, 30, true);
                    if (!value.toString().matches("[\\p{L}\\p{N}_-]{2,30}")) throw new ApiError(400, "账号需为 2–30 位文字、数字、下划线或短横线");
                    break;
                case "birthday":
                    LocalDate date = Input.date(data.get(key));
                    if (date.isAfter(Input.today())) throw new ApiError(400, "生日不能晚于今天");
                    value = date; break;
                case "heightCm": value = Input.decimal(data.get(key), 50, 250, 1); column = "height_cm"; break;
                case "avatar": value = ownedMedia(user, Input.text(data, key, 500, true)); break;
                default: throw new ApiError(400, "不支持的资料字段");
            }
            fields.add(column + "=?"); args.add(value);
        }
        args.add(user);
        db.update("UPDATE qy_users SET " + String.join(",", fields) + " WHERE id=?", args.toArray());
    }
    public Map<String, Object> findUser(String account) {
        if (account.isBlank() || account.length() > 30) throw new ApiError(400, "请输入好友账号");
        List<Map<String, Object>> found = db.query("SELECT * FROM qy_users WHERE account=?", (r, i) -> publicUser(r), account);
        return found.isEmpty() ? null : found.get(0);
    }
    @Transactional
    public void friend(String user, String friend, boolean add) {
        if (user.equals(friend)) throw new ApiError(400, "不能添加自己为好友");
        if (db.queryForObject("SELECT COUNT(*) FROM qy_users WHERE id=?", Integer.class, friend) == 0) throw new ApiError(404, "用户不存在");
        // 锁定用户行，好友修改与点赞均按同一顺序加锁，避免删除好友和点赞并发越权。
        lockPair(user, friend);
        if (add) {
            db.update("INSERT INTO qy_friends(user_id,friend_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM qy_friends WHERE user_id=? AND friend_id=?)", user, friend, user, friend);
            db.update("INSERT INTO qy_friends(user_id,friend_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM qy_friends WHERE user_id=? AND friend_id=?)", friend, user, friend, user);
        } else {
            db.update("DELETE FROM qy_friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)", user, friend, friend, user);
            db.update("DELETE FROM qy_likes WHERE (user_id=? AND post_id IN (SELECT id FROM qy_posts WHERE user_id=?)) OR (user_id=? AND post_id IN (SELECT id FROM qy_posts WHERE user_id=?))", user, friend, friend, user);
        }
    }
    private void lockPair(String a, String b) {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        db.queryForList("SELECT id FROM qy_users WHERE id=? FOR UPDATE", first);
        if (!first.equals(second)) db.queryForList("SELECT id FROM qy_users WHERE id=? FOR UPDATE", second);
    }
    public void post(String user, Map<String, Object> data) {
        Object lines = data.get("content");
        if (!(lines instanceof List<?>)) throw new ApiError(400, "动态内容格式不正确");
        List<String> content = new ArrayList<>();
        for (Object line : (List<?>) lines) {
            if (!(line instanceof String)) throw new ApiError(400, "动态内容格式不正确");
            if (!((String) line).trim().isEmpty()) content.add(((String) line).trim());
        }
        String text = String.join("\n", content);
        if (text.isBlank() || text.length() > 500) throw new ApiError(400, "动态需要 1–500 字");
        String cover = Input.text(data, "cover", 500, false);
        if (!cover.isEmpty()) cover = ownedMedia(user, cover);
        db.update("INSERT INTO qy_posts(id,user_id,content,cover) VALUES(?,?,?,?)", UUID.randomUUID().toString(), user, text, cover.isEmpty() ? null : cover);
    }
    @Transactional
    public void like(String user, String postId, boolean liked) {
        List<String> authors = db.queryForList("SELECT user_id FROM qy_posts WHERE id=?", String.class, postId);
        if (authors.isEmpty()) throw new ApiError(404, "动态不存在");
        String author = authors.get(0);
        lockPair(user, author);
        if (!user.equals(author) && db.queryForObject("SELECT COUNT(*) FROM qy_friends WHERE user_id=? AND friend_id=?", Integer.class, user, author) == 0)
            throw new ApiError(403, "只能给自己和好友点赞");
        if (liked) db.update("INSERT INTO qy_likes(user_id,post_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM qy_likes WHERE user_id=? AND post_id=?)", user, postId, user, postId);
        else db.update("DELETE FROM qy_likes WHERE user_id=? AND post_id=?", user, postId);
    }
    public void checkin(String user, String date, boolean checked) {
        if (!Input.date(date).equals(Input.today())) throw new ApiError(400, "只能为今天打卡");
        if (checked) db.update("INSERT INTO qy_checkins(user_id,entry_date) VALUES(?,?) ON DUPLICATE KEY UPDATE entry_date=VALUES(entry_date)", user, date);
        else db.update("DELETE FROM qy_checkins WHERE user_id=? AND entry_date=?", user, date);
    }
    public void schedule(String user, Map<String, Object> data) {
        String title = Input.text(data, "title", 30, true), type = Input.text(data, "type", 16, true);
        if (!Set.of("schedule", "anniversary").contains(type)) throw new ApiError(400, "日程类型不正确");
        LocalDate date = Input.date(data.get("date"));
        db.update("INSERT INTO qy_schedules(id,user_id,title,entry_type,entry_date,note) VALUES(?,?,?,?,?,?)", UUID.randomUUID().toString(), user, title, type, date, Input.text(data, "note", 100, false));
    }
    public void weight(String user, String date, Map<String, Object> data) {
        if (!Input.date(date).equals(Input.today())) throw new ApiError(400, "只能更新今日体重");
        BigDecimal value = Input.decimal(data.get("value"), 30, 150, 1);
        db.update("INSERT INTO qy_weights(user_id,entry_date,weight) VALUES(?,?,?) ON DUPLICATE KEY UPDATE weight=VALUES(weight)", user, date, value);
    }
    public void deleteWeight(String user, String date) {
        Input.date(date);
        db.update("DELETE FROM qy_weights WHERE user_id=? AND entry_date=?", user, date);
    }
    public void goal(String user, Map<String, Object> data) {
        db.update("UPDATE qy_users SET goal_weight=? WHERE id=?", Input.decimal(data.get("value"), 30, 150, 1), user);
    }
    public void ledger(String user, Map<String, Object> data) {
        String type = Input.text(data, "type", 16, true), category = Input.text(data, "category", 20, true);
        if (!("expense".equals(type) ? EXPENSE : "income".equals(type) ? INCOME : Set.of()).contains(category)) throw new ApiError(400, "账单分类不正确");
        BigDecimal amount = Input.decimal(data.get("amount"), 0.01, 9999999999.99, 2);
        LocalDateTime date;
        try { date = LocalDateTime.parse(Input.text(data, "date", 30, true)); }
        catch (RuntimeException e) { throw new ApiError(400, "账单日期格式不正确", e); }
        Input.date(date.toLocalDate().toString());
        if (date.toLocalDate().isAfter(Input.today())) throw new ApiError(400, "不能记录未来的账单");
        db.update("INSERT INTO qy_ledger(id,user_id,entry_type,category,amount,note,entry_date) VALUES(?,?,?,?,?,?,?)", UUID.randomUUID().toString(), user, type, category, amount, Input.text(data, "note", 100, false), Timestamp.valueOf(date));
    }
    public String upload(String user, Map<String, Object> data) {
        String encoded = Input.text(data, "base64", 2800000, true);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (RuntimeException e) { throw new ApiError(400, "图片格式不正确", e); }
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
        db.update("INSERT INTO qy_media(id,user_id,mime,content) VALUES(?,?,?,?)", id, user, mime, bytes);
        return publicUrl + "/api/media/" + id;
    }
    private String ownedMedia(String user, String url) {
        String prefix = publicUrl + "/api/media/";
        if (!url.startsWith(prefix)) throw new ApiError(400, "请先上传图片");
        String id = url.substring(prefix.length());
        if (!id.matches("[a-f0-9-]{36}") || db.queryForObject("SELECT COUNT(*) FROM qy_media WHERE id=? AND user_id=?", Integer.class, id, user) == 0)
            throw new ApiError(400, "图片不可用");
        return url;
    }
    public Map<String, Object> media(String id) {
        List<Map<String, Object>> found = db.query("SELECT mime,content FROM qy_media WHERE id=?", (r, i) -> Map.of("mime", r.getString("mime"), "bytes", r.getBytes("content")), id);
        if (found.isEmpty()) throw new ApiError(404, "图片不存在");
        return found.get(0);
    }
}
