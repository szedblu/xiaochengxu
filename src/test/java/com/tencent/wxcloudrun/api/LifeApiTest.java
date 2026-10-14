package com.tencent.wxcloudrun.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:life;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "app.wechat-app-secret=test-only", "app.public-url=https://example.test"
})
@AutoConfigureMockMvc
class LifeApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockBean WechatClient wechat;
    @BeforeEach void resetDatabase() {
        for (String table : new String[] {"likes", "friends", "sessions", "checkins", "schedules", "weights", "ledger", "media", "posts", "users"}) db.update("DELETE FROM qy_" + table);
        when(wechat.exchange(anyString())).thenAnswer(call -> "openid_" + call.getArgument(0));
    }
    private JsonNode request(MockHttpServletRequestBuilder builder, String token, Object body, int status) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType("application/json").content(json.writeValueAsBytes(body));
        MvcResult result = mvc.perform(builder).andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
    private MockHttpServletRequestBuilder cloudLogin(String code) {
        return post("/api/auth/cloud").header("X-WX-SOURCE", "wx_client")
                .header("X-WX-APPID", "wxd16f6433e9bfc102").header("X-WX-ENV", "prod-d8gvg5147dbf7cb35")
                .header("X-WX-OPENID", "openid_" + code);
    }
    private String login(String code) throws Exception { return request(cloudLogin(code), null, null, 200).path("token").asText(); }
    private JsonNode state(String token) throws Exception { return request(get("/api/bootstrap"), token, null, 200); }
    private String id(String token) throws Exception { return state(token).path("meId").asText(); }

    @Test void verifiedWechatIdentityAndRevocableSessions() throws Exception {
        request(get("/api/bootstrap").header("X-WX-OPENID", "forged"), null, null, 401);
        when(wechat.exchange("invalid")).thenThrow(new ApiError(401, "凭证无效"));
        request(post("/api/auth/wechat"), null, Map.of("code", "invalid"), 410);
        request(post("/api/auth/wechat"), null, Map.of("code", 123), 400);
        String alice = login("alice");
        JsonNode initial = state(alice);
        assertEquals(0, initial.path("weights").size());
        assertEquals(0, initial.path("posts").size());
        assertTrue(initial.path("profile").path("heightCm").isNull());
        assertEquals(id(alice), id(login("alice")));
        request(post("/api/auth/logout"), alice, null, 200);
        request(get("/api/bootstrap"), alice, null, 401);
        String other = login("bob");
        db.update("UPDATE qy_sessions SET expires_at=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
        request(get("/api/bootstrap"), other, null, 401);
        verifyNoInteractions(wechat);
    }
    @Test void cloudLoginRequiresGatewaySourceAppAndEnvironment() throws Exception {
        request(post("/api/auth/cloud"), null, Map.of("openid", "forged"), 401);
        request(post("/api/auth/cloud").header("X-WX-OPENID", "forged"), null, null, 401);
        request(post("/api/auth/cloud").header("X-WX-SOURCE", " "), null, null, 401);
        request(post("/api/auth/cloud").header("X-WX-SOURCE", "other").header("X-WX-OPENID", "forged"), null, null, 403);
        request(post("/api/auth/cloud").header("X-WX-SOURCE", "wx_client").header("X-WX-APPID", "wrong")
                .header("X-WX-ENV", "prod-d8gvg5147dbf7cb35").header("X-WX-OPENID", "forged"), null, null, 403);
        request(post("/api/auth/cloud").header("X-WX-SOURCE", "wx_client").header("X-WX-APPID", "wxd16f6433e9bfc102")
                .header("X-WX-ENV", "wrong-env").header("X-WX-OPENID", "forged"), null, null, 403);
        String token = request(cloudLogin("trusted"), null, Map.of("openid", "ignored-body-identity"), 200).path("token").asText();
        assertEquals("openid_trusted", db.queryForObject("SELECT openid FROM qy_users WHERE id=?", String.class, id(token)));
        verifyNoInteractions(wechat);
    }
    @Test void acceptsPlatformOtherSourceOnlyWithCompleteMatchingIdentity() throws Exception {
        MockHttpServletRequestBuilder valid = post("/api/auth/cloud").header("X-WX-SOURCE", "other")
                .header("X-WX-APPID", "wxd16f6433e9bfc102").header("X-WX-ENV", "prod-d8gvg5147dbf7cb35")
                .header("X-WX-OPENID", "openid_platform_user");
        String token = request(valid, null, null, 200).path("token").asText();
        assertEquals("openid_platform_user", db.queryForObject("SELECT openid FROM qy_users WHERE id=?", String.class, id(token)));
        request(post("/api/auth/cloud").header("X-WX-SOURCE", "other")
                .header("X-WX-APPID", "wxd16f6433e9bfc102").header("X-WX-ENV", "prod-d8gvg5147dbf7cb35"), null, null, 401);
        request(post("/api/auth/cloud").header("X-WX-SOURCE", "other")
                .header("X-WX-APPID", "wxd16f6433e9bfc102").header("X-WX-ENV", "prod-d8gvg5147dbf7cb35")
                .header("X-WX-OPENID", " "), null, null, 401);
        verifyNoInteractions(wechat);
    }
    @Test void personalProfileValidationAndUniqueAccounts() throws Exception {
        String a = login("a"), b = login("b");
        request(put("/api/me"), a, Map.of("name", "柒", "account", "qiyu", "intro", "我的简介", "heightCm", 170.5, "birthday", "2000-02-29"), 200);
        JsonNode p = state(a).path("profile");
        assertEquals("柒", p.path("name").asText()); assertEquals(170.5, p.path("heightCm").asDouble());
        assertEquals("2000-02-29", p.path("birthday").asText());
        request(put("/api/me"), b, Map.of("account", "qiyu"), 409);
        request(put("/api/me"), a, Map.of("heightCm", 0), 400);
        request(put("/api/me"), a, Map.of("birthday", "2025-02-29"), 400);
        request(put("/api/me"), a, Map.of("birthday", Input.today().plusDays(1).toString()), 400);
        request(put("/api/me"), a, Map.of("openid", "another"), 400);
        request(put("/api/me"), a, Map.of("intro", ""), 200);
        JsonNode found = request(get("/api/users/search").param("account", "qiyu"), b, null, 200);
        assertEquals(id(a), found.path("id").asText()); assertFalse(found.has("openid")); assertFalse(found.has("heightCm"));
    }
    @Test void mutualFriendshipAndOnlySelfOrFriendLikes() throws Exception {
        String a = login("a"), b = login("b"), c = login("c"), aid = id(a), bid = id(b);
        request(post("/api/posts"), a, Map.of("content", java.util.List.of("测试动态")), 200);
        String post = state(a).path("posts").get(0).path("id").asText();
        assertEquals(0, state(b).path("posts").size());
        request(put("/api/posts/" + post + "/like"), b, Map.of("liked", true), 403);
        request(put("/api/friends/" + bid), a, null, 200);
        request(put("/api/friends/" + bid), a, null, 200);
        assertEquals(1, state(a).path("friendIds").size());
        assertEquals(aid, state(b).path("friendIds").get(0).asText());
        assertEquals(1, state(b).path("posts").size());
        request(put("/api/posts/" + post + "/like"), a, Map.of("liked", true), 200);
        request(put("/api/posts/" + post + "/like"), b, Map.of("liked", true), 200);
        request(put("/api/posts/" + post + "/like"), b, Map.of("liked", true), 200);
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM qy_likes", Integer.class));
        request(put("/api/posts/" + post + "/like"), c, Map.of("liked", true), 403);
        request(put("/api/friends/" + aid), a, null, 400);
        request(delete("/api/friends/" + bid), a, null, 200);
        assertEquals(0, state(b).path("friendIds").size());
        assertEquals(0, state(b).path("posts").size());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM qy_likes", Integer.class));
    }
    @Test void todayOnlyWeightAndCheckinAndPrivateRecords() throws Exception {
        String a = login("a"), b = login("b"), today = Input.today().toString();
        request(put("/api/weights/" + today), a, Map.of("value", 68.9), 200);
        request(put("/api/weights/" + today), a, Map.of("value", 69.1), 200);
        assertEquals(1, state(a).path("weights").size()); assertEquals(69.1, state(a).path("weights").get(0).path("value").asDouble());
        request(put("/api/weights/" + Input.today().minusDays(1)), a, Map.of("value", 70), 400);
        request(put("/api/weights/" + Input.today().plusDays(1)), a, Map.of("value", 70), 400);
        request(put("/api/weights/" + today), a, Map.of("value", "NaN"), 400);
        request(put("/api/weights/" + today), a, Map.of("value", 70.123), 400);
        request(put("/api/checkins/" + today), a, null, 200);
        request(put("/api/checkins/" + today), a, null, 200);
        assertEquals(1, state(a).path("checkins").size());
        request(put("/api/checkins/" + Input.today().minusDays(1)), a, null, 400);
        assertEquals(0, state(b).path("weights").size()); assertEquals(0, state(b).path("checkins").size());
        request(delete("/api/weights/" + today), b, null, 200); assertEquals(1, state(a).path("weights").size());
        request(put("/api/goal"), a, Map.of("value", 64.5), 200); assertEquals(64.5, state(a).path("goalWeight").asDouble());
        request(delete("/api/checkins/" + today), a, null, 200); assertEquals(0, state(a).path("checkins").size());
    }
    @Test void schedulesAndLedgerAreValidatedAndIsolated() throws Exception {
        String a = login("a"), b = login("b");
        request(post("/api/schedules"), a, Map.of("title", "生日", "type", "anniversary", "date", Input.today().plusDays(5).toString()), 200);
        request(post("/api/schedules"), a, Map.of("title", " ", "type", "schedule", "date", Input.today().toString()), 400);
        request(post("/api/ledger"), a, Map.of("type", "expense", "category", "food", "amount", 25.05, "date", Input.today() + "T12:30:00"), 200);
        request(post("/api/ledger"), a, Map.of("type", "income", "category", "salary", "amount", 5000, "date", Input.today() + "T09:00:00"), 200);
        request(post("/api/ledger"), a, Map.of("type", "expense", "category", "salary", "amount", 10, "date", Input.today() + "T09:00:00"), 400);
        request(post("/api/ledger"), a, Map.of("type", "income", "category", "salary", "amount", 0, "date", Input.today() + "T09:00:00"), 400);
        assertEquals(2, state(a).path("ledger").size()); assertEquals(1, state(a).path("schedules").size());
        assertEquals(0, state(b).path("ledger").size()); assertEquals(0, state(b).path("schedules").size());
    }
    @Test void persistentImagesHaveOwnershipAndRealContentValidation() throws Exception {
        String a = login("a"), b = login("b");
        request(post("/api/media"), a, Map.of("base64", "invalid"), 400);
        request(post("/api/media"), a, Map.of("base64", Base64.getEncoder().encodeToString(new byte[] {(byte)255,(byte)216,(byte)255,1})), 400);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ImageIO.write(image, "png", buffer);
        String url = request(post("/api/media"), a, Map.of("base64", Base64.getEncoder().encodeToString(buffer.toByteArray())), 200).path("url").asText();
        mvc.perform(get(url.replace("https://example.test", ""))).andExpect(status().isOk()).andExpect(content().bytes(buffer.toByteArray())).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        request(put("/api/me"), a, Map.of("avatar", url), 200);
        request(put("/api/me"), b, Map.of("avatar", url), 400);
        request(post("/api/posts"), b, Map.of("content", java.util.List.of("他人的图片"), "cover", url), 400);
        request(post("/api/posts"), a, Map.of("content", java.util.List.of("自己的图片"), "cover", url), 200);
        assertEquals(url, state(a).path("profile").path("avatar").asText());
    }
}
