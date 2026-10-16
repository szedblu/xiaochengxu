package com.tencent.wxcloudrun.api;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media/uploads")
public class MediaUploadController {
    private final AuthService auth;
    private final MediaUploadService uploads;
    public MediaUploadController(AuthService auth, MediaUploadService uploads) { this.auth = auth; this.uploads = uploads; }
    private String user(HttpServletRequest r) { return auth.require(r.getHeader("Authorization")); }
    @PostMapping
    public Map<String, Object> start(HttpServletRequest r, @RequestBody Map<String, Object> data) { return ApiResponse.ok(uploads.start(user(r), data)); }
    @PutMapping("/{id}/parts/{index}")
    public Map<String, Object> part(HttpServletRequest r, @PathVariable String id, @PathVariable int index, @RequestBody Map<String, Object> data) {
        uploads.part(user(r), id, index, data); return ApiResponse.ok(null);
    }
    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(HttpServletRequest r, @PathVariable String id) { return ApiResponse.ok(Map.of("url", uploads.complete(user(r), id))); }
    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(HttpServletRequest r, @PathVariable String id) { uploads.cancel(user(r), id); return ApiResponse.ok(null); }
}
