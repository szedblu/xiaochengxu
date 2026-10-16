package com.tencent.wxcloudrun.api;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/food")
public class FoodController {
    private final AuthService auth;
    private final FoodService food;
    public FoodController(AuthService auth,FoodService food){this.auth=auth;this.food=food;}
    private String user(HttpServletRequest r){return auth.require(r.getHeader("Authorization"));}
    private Map<String,Object> ok(Object value){return ApiResponse.ok(value);}
    @GetMapping("/bootstrap") public Map<String,Object> bootstrap(HttpServletRequest r,@RequestParam(required=false)String ownerId){return ok(food.bootstrap(user(r),ownerId));}
    @PutMapping("/profile") public Map<String,Object> profile(HttpServletRequest r,@RequestBody Map<String,Object> data){food.profile(user(r),data);return ok(null);}
    @GetMapping("/users/search") public Map<String,Object> search(HttpServletRequest r,@RequestParam String account){return ok(food.search(user(r),account));}
    @PutMapping("/friends/{id}") public Map<String,Object> friend(HttpServletRequest r,@PathVariable String id){food.friend(user(r),id,true);return ok(null);}
    @DeleteMapping("/friends/{id}") public Map<String,Object> removeFriend(HttpServletRequest r,@PathVariable String id){food.friend(user(r),id,false);return ok(null);}
    @PostMapping("/lines") public Map<String,Object> quantity(HttpServletRequest r,@RequestBody Map<String,Object> data){food.quantity(user(r),data);return ok(null);}
    @PostMapping("/orders") public Map<String,Object> submit(HttpServletRequest r,@RequestBody Map<String,Object> data){food.submit(user(r),data);return ok(null);}
    @GetMapping("/orders") public Map<String,Object> history(HttpServletRequest r,@RequestParam(required=false)String ownerId,@RequestParam(defaultValue="0")int offset){return ok(food.history(user(r),ownerId,offset));}
    @PostMapping("/recipes") public Map<String,Object> recipe(HttpServletRequest r,@RequestBody Map<String,Object> data){food.saveRecipe(user(r),null,data);return ok(null);}
    @PutMapping("/recipes/{id}") public Map<String,Object> editRecipe(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Object> data){food.saveRecipe(user(r),id,data);return ok(null);}
    @DeleteMapping("/recipes/{id}") public Map<String,Object> deleteRecipe(HttpServletRequest r,@PathVariable String id){food.deleteRecipe(user(r),id);return ok(null);}
    @DeleteMapping("/week") public Map<String,Object> clear(HttpServletRequest r){food.clear(user(r));return ok(null);}
    @PostMapping("/feedback") public Map<String,Object> feedback(HttpServletRequest r,@RequestBody Map<String,Object> data){food.feedback(user(r),data);return ok(null);}
    @GetMapping("/feedback") public Map<String,Object> feedbacks(HttpServletRequest r,@RequestParam(defaultValue="false")boolean mine,@RequestParam(defaultValue="0")int offset){return ok(food.feedbacks(user(r),mine,offset));}
    @GetMapping("/images/{id}") public ResponseEntity<byte[]> image(@PathVariable String id){
        Map<String,Object> image=food.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)image.get("mime"))).header("X-Content-Type-Options","nosniff").header("Cache-Control","public,max-age=86400").body((byte[])image.get("bytes"));
    }
}
