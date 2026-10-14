package com.tencent.wxcloudrun.api;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class LifeController {
    private final AuthService auth;
    private final LifeService life;
    private final JdbcTemplate db;
    private final CloudIdentity cloud;
    public LifeController(AuthService auth, LifeService life, JdbcTemplate db, CloudIdentity cloud) { this.auth = auth; this.life = life; this.db = db; this.cloud = cloud; }
    private String user(HttpServletRequest r) { return auth.require(r.getHeader("Authorization")); }
    public static Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("code", 0); result.put("data", data); return result;
    }
    private Map<String, Object> updated(String user) { return ok(life.bootstrap(user)); }
    @GetMapping("/health")
    public Map<String, Object> health() {
        db.queryForObject("SELECT 1", Integer.class);
        return ok(Map.of("status", "UP", "schema", 1));
    }
    @PostMapping("/auth/wechat")
    public Map<String, Object> login(@RequestBody Map<String, Object> data) {
        return ok(auth.login(Input.text(data, "code", 256, true)));
    }
    @PostMapping("/auth/cloud")
    public Map<String, Object> cloudLogin(HttpServletRequest request) {
        return ok(auth.loginIdentity(cloud.require(request)));
    }
    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest r) { auth.logout(r.getHeader("Authorization")); return ok(Map.of()); }
    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap(HttpServletRequest r) { return updated(user(r)); }
    @PutMapping("/me")
    public Map<String, Object> profile(HttpServletRequest r, @RequestBody Map<String, Object> data) {
        String user = user(r); life.updateProfile(user, data); return updated(user);
    }
    @GetMapping("/users/search")
    public Map<String, Object> search(HttpServletRequest r, @RequestParam String account) { user(r); return ok(life.findUser(account.trim())); }
    @PutMapping("/friends/{id}")
    public Map<String, Object> friend(HttpServletRequest r, @PathVariable String id) {
        String user = user(r); life.friend(user, id, true); return updated(user);
    }
    @DeleteMapping("/friends/{id}")
    public Map<String, Object> unfriend(HttpServletRequest r, @PathVariable String id) {
        String user = user(r); life.friend(user, id, false); return updated(user);
    }
    @PostMapping("/posts")
    public Map<String, Object> post(HttpServletRequest r, @RequestBody Map<String, Object> data) {
        String user = user(r); life.post(user, data); return updated(user);
    }
    @PutMapping("/posts/{id}/like")
    public Map<String, Object> like(HttpServletRequest r, @PathVariable String id, @RequestBody Map<String, Object> data) {
        if (!(data.get("liked") instanceof Boolean)) throw new ApiError(400, "点赞状态不正确");
        String user = user(r); life.like(user, id, (Boolean) data.get("liked")); return updated(user);
    }
    @PutMapping("/checkins/{date}")
    public Map<String, Object> checkin(HttpServletRequest r, @PathVariable String date) {
        String user = user(r); life.checkin(user, date, true); return updated(user);
    }
    @DeleteMapping("/checkins/{date}")
    public Map<String, Object> uncheck(HttpServletRequest r, @PathVariable String date) {
        String user = user(r); life.checkin(user, date, false); return updated(user);
    }
    @PostMapping("/schedules")
    public Map<String, Object> schedule(HttpServletRequest r, @RequestBody Map<String, Object> data) {
        String user = user(r); life.schedule(user, data); return updated(user);
    }
    @PutMapping("/weights/{date}")
    public Map<String, Object> weight(HttpServletRequest r, @PathVariable String date, @RequestBody Map<String, Object> data) {
        String user = user(r); life.weight(user, date, data); return updated(user);
    }
    @DeleteMapping("/weights/{date}")
    public Map<String, Object> removeWeight(HttpServletRequest r, @PathVariable String date) {
        String user = user(r); life.deleteWeight(user, date); return updated(user);
    }
    @PutMapping("/goal")
    public Map<String, Object> goal(HttpServletRequest r, @RequestBody Map<String, Object> data) {
        String user = user(r); life.goal(user, data); return updated(user);
    }
    @PostMapping("/ledger")
    public Map<String, Object> ledger(HttpServletRequest r, @RequestBody Map<String, Object> data) {
        String user = user(r); life.ledger(user, data); return updated(user);
    }
    @PostMapping("/media")
    public Map<String, Object> upload(HttpServletRequest r, @RequestBody Map<String, Object> data) { return ok(Map.of("url", life.upload(user(r), data))); }
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> media(@PathVariable String id) {
        Map<String, Object> media = life.media(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String) media.get("mime")))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body((byte[]) media.get("bytes"));
    }
}
