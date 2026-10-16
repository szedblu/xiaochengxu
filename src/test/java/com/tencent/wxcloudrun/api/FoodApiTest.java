package com.tencent.wxcloudrun.api;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:food;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.public-url=https://example.test"})
@AutoConfigureMockMvc
class FoodApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired FoodService food;
    @BeforeEach void clean() {
        for(String table:List.of("cf_feedback","cf_orders","cf_commands","cf_lines","cf_meals","cf_recipes","cf_images","cf_friends","cf_profiles","cf_upload_parts","cf_uploads","cf_media","cf_sessions","cf_users"))db.update("DELETE FROM "+table);
        db.update("INSERT INTO cf_recipes(id,name,category,kcal,unit) VALUES('rice','米饭','主食',116,'100g')");
    }
    JsonNode request(MockHttpServletRequestBuilder req,String token,Object body,int status)throws Exception {
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.contentType("application/json").content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray()).path("data");
    }
    String login(String suffix)throws Exception {
        String token=request(post("/api/auth/cloud").header("X-WX-SOURCE","other").header("X-WX-APPID","wxd16f6433e9bfc102").header("X-WX-ENV","prod-d8gvg5147dbf7cb35").header("X-WX-OPENID","food_openid_"+suffix),null,null,200).path("token").asText();
        request(get("/api/food/bootstrap"),token,null,200);return token;
    }
    String id(String token)throws Exception{return request(get("/api/food/bootstrap"),token,null,200).path("user").path("id").asText();}
    Map<String,Object> qty(String owner,int delta){return new LinkedHashMap<>(Map.of("ownerId",owner,"date",Input.today().toString(),"meal","l","dishId","rice","delta",delta,"commandId",UUID.randomUUID().toString()));}
    @Test void cloudHeadersSessionsAndRetiredRoutes()throws Exception {
        request(post("/api/auth/cloud"),null,null,401);
        request(post("/api/auth/cloud").header("X-WX-SOURCE","other").header("X-WX-APPID","wrong").header("X-WX-ENV","prod-d8gvg5147dbf7cb35").header("X-WX-OPENID","someone"),null,null,403);
        String token=login("identity");String owner=id(token);
        assertEquals(owner,id(login("identity")));
        for(String route:List.of("/api/bootstrap","/api/me","/api/weights/2026-10-16","/api/posts","/api/ledger","/api/checkins/2026-10-16","/api/auth/wechat"))
            mvc.perform(get(route)).andExpect(status().isNotFound());
        request(post("/api/auth/logout"),token,null,200);
        request(get("/api/food/bootstrap"),token,null,401);
        String expired=login("expired");
        db.update("UPDATE cf_sessions SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(10)));
        request(get("/api/food/bootstrap"),expired,null,401);
        request(get("/api/health"),null,null,200);
    }
    @Test void mediaChunksAndNewProfileOwnershipWorkWithoutRetiredServices()throws Exception {
        String a=login("media-a"),b=login("media-b");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        byte[] bytes=Arrays.copyOf(out.toByteArray(),50000);
        String upload=request(post("/api/media/uploads"),a,Map.of("size",bytes.length,"parts",2),200).path("uploadId").asText();
        String path="/api/media/uploads/"+upload;
        request(post(path+"/complete"),b,null,404);
        request(post(path+"/complete"),a,null,400);
        for(int index=0;index<2;index++)request(put(path+"/parts/"+index),a,Map.of("base64",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,index*49152,Math.min((index+1)*49152,bytes.length)))),200);
        String url=request(post(path+"/complete"),a,null,200).path("url").asText();
        mvc.perform(get(url.replace("https://example.test",""))).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        request(put("/api/food/profile"),b,Map.of("avatar",url),400);
        request(put("/api/food/profile"),a,Map.of("avatar",url,"account","meal-account"),200);
        request(put("/api/food/profile"),b,Map.of("account","meal-account"),409);
        request(put("/api/food/profile"),a,Map.of("openid","forged"),400);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM cf_upload_parts",Integer.class));
    }
    @Test void loginAccountFriendSharingAndRevocation()throws Exception {
        request(get("/api/food/bootstrap"),null,null,401);
        String a=login("a"),b=login("b"),aId=id(a),bId=id(b);
        JsonNode initial=request(get("/api/food/bootstrap"),a,null,200);
        assertEquals(0,initial.path("friends").size());assertEquals(0,initial.path("menu").size());
        request(put("/api/food/profile"),b,Map.of("name","小南","account","nannan","goal",1800,"since",Input.today().toString()),200);
        assertEquals(bId,request(get("/api/food/users/search").param("account","nannan"),a,null,200).path("id").asText());
        request(get("/api/food/bootstrap").param("ownerId",bId),a,null,403);
        request(put("/api/food/friends/"+bId),a,null,200);
        assertEquals(aId,request(get("/api/food/bootstrap"),b,null,200).path("friends").get(0).path("id").asText());
        request(get("/api/food/bootstrap").param("ownerId",bId),a,null,200);
        request(put("/api/food/friends/"+aId),a,null,400);
        request(delete("/api/food/friends/"+bId),a,null,200);
        request(post("/api/food/lines"),b,qty(aId,1),403);
    }
    @Test void concurrentDeltaCommandsDoNotLoseUpdatesAndCannotBeReused()throws Exception {
        String a=login("a"),b=login("b"),owner=id(a),actor=id(b);
        request(put("/api/food/friends/"+actor),a,null,200);
        Map<String,Object> one=qty(owner,1);
        request(post("/api/food/lines"),a,one,200);request(post("/api/food/lines"),a,one,200);
        Map<String,Object> changed=new LinkedHashMap<>(one);changed.put("delta",-1);
        request(post("/api/food/lines"),a,changed,409);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate=new CountDownLatch(1);
            Future<?> x=pool.submit(()->{try{gate.await();food.quantity(owner,qty(owner,1));}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> y=pool.submit(()->{try{gate.await();food.quantity(actor,qty(owner,1));}catch(Exception e){throw new RuntimeException(e);}});
            gate.countDown();x.get(15,TimeUnit.SECONDS);y.get(15,TimeUnit.SECONDS);
        } finally{pool.shutdownNow();}
        assertEquals(3,db.queryForObject("SELECT qty FROM cf_lines",Integer.class));
        request(post("/api/food/lines"),b,qty(owner,-1),200);
        assertEquals(2,db.queryForObject("SELECT qty FROM cf_lines",Integer.class));
        for(int i=0;i<2;i++)request(post("/api/food/lines"),a,qty(owner,-1),200);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM cf_lines",Integer.class));
    }
    @Test void submittingValidatesVersionAndPreservesSnapshotsHistoryAndAuthor()throws Exception {
        String a=login("a"),b=login("b"),owner=id(a),bId=id(b);
        request(put("/api/food/friends/"+bId),a,null,200);
        request(post("/api/food/lines"),a,qty(owner,1),200);
        Map<String,Object> submit=new LinkedHashMap<>(Map.of("ownerId",owner,"date",Input.today().toString(),"meal","l","revision",0,"commandId",UUID.randomUUID().toString()));
        request(post("/api/food/orders"),b,submit,409);
        submit.put("revision",1);request(post("/api/food/orders"),b,submit,200);request(post("/api/food/orders"),b,submit,200);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM cf_orders",Integer.class));
        request(post("/api/food/lines"),a,qty(owner,1),200);
        JsonNode snapshot=request(get("/api/food/orders").param("ownerId",owner),b,null,200).get(0);
        assertEquals(1,snapshot.path("lines").get(0).path("qty").asInt());assertEquals(116,snapshot.path("kcal").asInt());
        assertEquals(bId,db.queryForObject("SELECT submitted_by FROM cf_orders",String.class));
        request(delete("/api/food/week"),a,null,200);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM cf_lines",Integer.class));
        assertEquals(1,request(get("/api/food/orders"),a,null,200).size());
        request(delete("/api/food/friends/"+bId),a,null,200);
        request(get("/api/food/orders").param("ownerId",owner),b,null,403);
    }
    @Test void collaboratorRecipeStaysVisibleAndOrderSnapshotSurvivesEditsAndDeletion()throws Exception {
        String a=login("a"),b=login("b"),owner=id(a),bob=id(b);
        request(put("/api/food/friends/"+bob),a,null,200);
        request(post("/api/food/recipes"),b,Map.of("name","好友的炖汤","cat","菜品","kcal",50,"unit","份","img",""),200);
        String recipe=db.queryForObject("SELECT id FROM cf_recipes WHERE owner_id=?",String.class,bob);
        Map<String,Object> line=qty(owner,1);line.put("dishId",recipe);
        request(post("/api/food/lines"),b,line,200);
        JsonNode visible=request(get("/api/food/bootstrap"),a,null,200).path("dishes");
        assertTrue(java.util.stream.StreamSupport.stream(visible.spliterator(),false).anyMatch(d->d.path("id").asText().equals(recipe)));
        line.put("commandId",UUID.randomUUID().toString());request(post("/api/food/lines"),a,line,200);
        request(post("/api/food/orders"),a,Map.of("ownerId",owner,"date",Input.today().toString(),"meal","l","revision",2,"commandId",UUID.randomUUID().toString()),200);
        request(put("/api/food/recipes/"+recipe),b,Map.of("name","改名后的炖汤","cat","菜品","kcal",100,"unit","份","img",""),200);
        assertEquals(3,db.queryForObject("SELECT revision FROM cf_meals",Integer.class));
        request(delete("/api/food/recipes/"+recipe),b,null,200);
        JsonNode record=request(get("/api/food/orders"),a,null,200).get(0);
        assertEquals("好友的炖汤",record.path("lines").get(0).path("name").asText());assertEquals(100,record.path("kcal").asInt());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM cf_lines",Integer.class));
    }
    @Test void persistedRecipeImageIsServedAsBinaryAndUnknownImagesAreRejected()throws Exception {
        byte[] bytes=new byte[]{1,2,3,4};
        db.update("INSERT INTO cf_images(id,mime,content) VALUES('seed-rice','image/jpeg',?)",bytes);
        mvc.perform(get("/api/food/images/seed-rice")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(bytes));
        request(get("/api/food/images/missing"),null,null,404);
    }
    @Test void recipeOwnershipAndFeedbackVisibility()throws Exception {
        String a=login("a"),b=login("b"),owner=id(a);
        Map<String,Object> recipe=Map.of("name","青椒肉丝","cat","菜品","kcal",180,"unit","份","img","");
        request(post("/api/food/recipes"),a,recipe,200);
        String recipeId=db.queryForObject("SELECT id FROM cf_recipes WHERE owner_id=?",String.class,owner);
        request(put("/api/food/recipes/"+recipeId),b,recipe,403);
        request(delete("/api/food/recipes/rice"),a,null,403);
        request(delete("/api/food/recipes/"+recipeId),a,null,200);
        Map<String,Object> feedback=Map.of("content","希望能增加更多早餐菜谱","type","功能建议","contact","private-contact","commandId",UUID.randomUUID().toString());
        request(post("/api/food/feedback"),a,feedback,200);request(post("/api/food/feedback"),a,feedback,200);
        JsonNode visible=request(get("/api/food/feedback"),b,null,200);
        assertEquals(1,visible.size());assertFalse(visible.get(0).has("contact"));assertTrue(visible.get(0).has("author"));assertTrue(visible.get(0).has("at"));
        assertEquals(0,request(get("/api/food/feedback").param("mine","true"),b,null,200).size());
        assertEquals("private-contact",request(get("/api/food/feedback").param("mine","true"),a,null,200).get(0).path("contact").asText());
    }
}
