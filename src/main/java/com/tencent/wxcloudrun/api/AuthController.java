package com.tencent.wxcloudrun.api;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final CloudIdentity cloud;
    private final JdbcTemplate db;
    public AuthController(AuthService auth,CloudIdentity cloud,JdbcTemplate db){this.auth=auth;this.cloud=cloud;this.db=db;}
    @PostMapping("/auth/cloud") public Map<String,Object> login(HttpServletRequest r){return ApiResponse.ok(auth.loginIdentity(cloud.require(r)));}
    @PostMapping("/auth/logout") public Map<String,Object> logout(HttpServletRequest r){auth.logout(r.getHeader("Authorization"));return ApiResponse.ok(null);}
    @GetMapping("/health") public Map<String,Object> health(){db.queryForObject("SELECT COUNT(*) FROM cf_users",Integer.class);return ApiResponse.ok(Map.of("status","UP","business","yiqichifan","schema",2));}
}
