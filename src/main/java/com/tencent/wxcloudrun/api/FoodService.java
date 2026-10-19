package com.tencent.wxcloudrun.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String publicUrl;
    public FoodService(JdbcTemplate db, ObjectMapper json, @Value("${app.public-url}") String url) {
        this.db=db; this.json=json; this.publicUrl=url.replaceAll("/+$", "");
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Object decode(String value) {
        try { return json.readValue(value, new TypeReference<Object>() {}); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void ensure(String user) {
        db.update("INSERT INTO cf_profiles(user_id,since_date) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",user,Input.today());
    }
    private void pairLock(String a,String b) {
        List<String> ids=new ArrayList<>(new HashSet<>(List.of(a,b))); Collections.sort(ids);
        for(String id:ids) {
            if(db.queryForList("SELECT id FROM cf_users WHERE id=? FOR UPDATE",String.class,id).isEmpty()) throw new ApiError(404,"用户不存在");
        }
    }
    private String owner(String actor,String requested,boolean lock) {
        String owner=requested==null||requested.isBlank()?actor:requested;
        if(lock) pairLock(actor,owner);
        if(!actor.equals(owner) && db.queryForObject("SELECT COUNT(*) FROM cf_friends WHERE user_id=? AND friend_id=?",Integer.class,owner,actor)==0)
            throw new ApiError(403,"好友关系已解除，请返回自己的菜单");
        if(lock) { ensure(actor); ensure(owner); db.queryForObject("SELECT revision FROM cf_profiles WHERE user_id=? FOR UPDATE",Long.class,owner); }
        return owner;
    }
    private Map<String,Object> person(String id) {
        return db.queryForObject("SELECT u.id,u.name,u.account,u.avatar,p.since_date,p.goal FROM cf_users u JOIN cf_profiles p ON p.user_id=u.id WHERE u.id=?",(r,i)-> {
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",r.getString("id"));m.put("name",r.getString("name"));m.put("account",r.getString("account"));
            m.put("avatar",r.getString("avatar"));m.put("since",r.getString("since_date"));m.put("goal",r.getInt("goal"));return m;
        },id);
    }
    private List<Map<String,Object>> dishes(String actor,String owner) {
        return db.query("SELECT * FROM cf_recipes WHERE active=TRUE AND (owner_id IS NULL OR owner_id=? OR owner_id=? OR id IN (SELECT recipe_id FROM cf_lines WHERE owner_id=?)) ORDER BY category,name,id",(r,i)-> {
            Map<String,Object> d=new LinkedHashMap<>();String image=r.getString("image_id");
            d.put("id",r.getString("id"));d.put("name",r.getString("name"));d.put("cat",r.getString("category"));
            d.put("kcal",r.getInt("kcal"));d.put("unit",r.getString("unit"));
            d.put("img",image==null?r.getString("image_url"):publicUrl+"/api/food/images/"+image);
            d.put("editable",actor.equals(r.getString("owner_id")));return d;
        },actor,owner,owner);
    }
    @Transactional
    public Map<String,Object> bootstrap(String actor,String requested) {
        String owner=owner(actor,requested,true);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("user",person(actor));out.put("owner",person(owner));out.put("ownerId",owner);
        out.put("friends",db.query("SELECT u.id,u.name,u.account,u.avatar,f.joined_at FROM cf_friends f JOIN cf_users u ON u.id=f.friend_id WHERE f.user_id=? ORDER BY f.joined_at,u.id",(r,i)-> {
            Map<String,Object> f=new LinkedHashMap<>();f.put("id",r.getString("id"));f.put("name",r.getString("name"));
            f.put("account",r.getString("account"));f.put("avatar",r.getString("avatar"));f.put("at",r.getTimestamp("joined_at").toLocalDateTime().toString());return f;
        },actor));
        out.put("dishes",dishes(actor,owner));
        Map<String,Object> menu=new LinkedHashMap<>(),submitted=new LinkedHashMap<>(),revisions=new LinkedHashMap<>();
        LocalDate start=Input.today().minusDays(90),end=Input.today().plusDays(90);
        db.query("SELECT meal_date,meal_key,revision,submitted_revision,submitted_at FROM cf_meals WHERE owner_id=? AND meal_date BETWEEN ? AND ?", r->{
            String date=r.getString("meal_date"), meal=r.getString("meal_key"), key=date+"_"+meal;
            revisions.put(key,r.getLong("revision"));
            if(r.getTimestamp("submitted_at")!=null) submitted.put(key,Map.of("ts",r.getTimestamp("submitted_at").getTime(),"revision",r.getLong("submitted_revision")));
        },owner,start,end);
        db.query("SELECT meal_date,meal_key,recipe_id,qty FROM cf_lines WHERE owner_id=? AND meal_date BETWEEN ? AND ?",r->{
            String date=r.getString("meal_date"),meal=r.getString("meal_key");
            @SuppressWarnings("unchecked") Map<String,List<Map<String,Object>>> day=(Map<String,List<Map<String,Object>>>)menu.computeIfAbsent(date,k->new LinkedHashMap<>());
            day.computeIfAbsent(meal,k->new ArrayList<>()).add(Map.of("id",r.getString("recipe_id"),"qty",r.getInt("qty")));
        },owner,start,end);
        out.put("menu",menu);out.put("submitted",submitted);out.put("revisions",revisions);
        out.put("serverToday",Input.today().toString());
        return out;
    }
    @Transactional
    public void profile(String actor,Map<String,Object> data) {
        pairLock(actor,actor);ensure(actor);
        Map<String,Object> core=new LinkedHashMap<>();
        for(String key:List.of("name","account","avatar")) if(data.containsKey(key)) core.put(key,data.get(key));
        for (Map.Entry<String,Object> entry : core.entrySet()) {
            String key=entry.getKey();
            String value=Input.text(core,key,key.equals("name")?20:key.equals("account")?30:500,true);
            if (key.equals("account") && !value.matches("[\\p{L}\\p{N}_-]{2,30}")) throw new ApiError(400,"账号需为2至30位文字、数字、下划线或短横线");
            if (key.equals("avatar")) {
                String prefix=publicUrl+"/api/media/";
                if(!value.startsWith(prefix)||db.queryForObject("SELECT COUNT(*) FROM cf_media WHERE id=? AND user_id=?",Integer.class,value.substring(prefix.length()),actor)==0)
                    throw new ApiError(400,"请先上传自己的头像");
            }
            db.update("UPDATE cf_users SET "+key+"=? WHERE id=?",value,actor);
        }
        if(!Set.of("name","account","avatar","since","goal").containsAll(data.keySet())) throw new ApiError(400,"不支持的资料字段");
        if(data.containsKey("since")) {
            LocalDate date=Input.date(data.get("since"));if(date.isAfter(Input.today()))throw new ApiError(400,"开始日期不能晚于今天");
            db.update("UPDATE cf_profiles SET since_date=? WHERE user_id=?",date,actor);
        }
        if(data.containsKey("goal")) db.update("UPDATE cf_profiles SET goal=? WHERE user_id=?",Input.decimal(data.get("goal"),500,6000,0).intValueExact(),actor);
    }
    @Transactional
    public Map<String,Object> search(String actor,String account) {
        if(account==null||account.isBlank()||account.length()>30)throw new ApiError(400,"请输入好友账号");
        List<String> ids=db.queryForList("SELECT u.id FROM cf_users u JOIN cf_profiles p ON p.user_id=u.id WHERE u.account=?",String.class,account.trim());
        return ids.isEmpty()?null:person(ids.get(0));
    }
    @Transactional
    public void friend(String actor,String friend,boolean add) {
        if(actor.equals(friend))throw new ApiError(400,"不能添加自己");
        pairLock(actor,friend);ensure(actor);
        if(db.queryForObject("SELECT COUNT(*) FROM cf_profiles WHERE user_id=?",Integer.class,friend)==0)throw new ApiError(404,"该用户尚未登录一起吃饭");
        if(add) {
            db.update("INSERT INTO cf_friends(user_id,friend_id) VALUES(?,?) ON DUPLICATE KEY UPDATE friend_id=VALUES(friend_id)",actor,friend);
            db.update("INSERT INTO cf_friends(user_id,friend_id) VALUES(?,?) ON DUPLICATE KEY UPDATE friend_id=VALUES(friend_id)",friend,actor);
        } else db.update("DELETE FROM cf_friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)",actor,friend,friend,actor);
    }
    private boolean command(String actor,Map<String,Object> data,String operation) {
        String id=Input.text(data,"commandId",36,true);
        if(!id.matches("[a-zA-Z0-9_-]{16,36}"))throw new ApiError(400,"请求标识无效");
        Map<String,Object> canonical=new TreeMap<>(data);canonical.put("operation",operation);
        String payload=encode(canonical);
        List<String> previous=db.queryForList("SELECT payload FROM cf_commands WHERE actor_id=? AND command_id=?",String.class,actor,id);
        if(!previous.isEmpty()) {
            if(!payload.equals(previous.get(0)))throw new ApiError(409,"请求标识已用于其他操作");
            return false;
        }
        db.update("INSERT INTO cf_commands(actor_id,command_id,payload) VALUES(?,?,?)",actor,id,payload);return true;
    }
    private String meal(Map<String,Object> data) {
        String key=Input.text(data,"meal",1,true);if(!Set.of("b","l","d").contains(key))throw new ApiError(400,"餐次无效");return key;
    }
    private LocalDate date(Map<String,Object> data) {
        LocalDate date=Input.date(data.get("date"));
        if(date.isBefore(Input.today().minusDays(90))||date.isAfter(Input.today().plusDays(90)))throw new ApiError(400,"请选择前后90天内的餐次");return date;
    }
    private Map<String,Object> mealRow(String owner,LocalDate date,String meal) {
        db.update("INSERT INTO cf_meals(owner_id,meal_date,meal_key) VALUES(?,?,?) ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id)",owner,date,meal);
        return db.queryForMap("SELECT revision,submitted_revision FROM cf_meals WHERE owner_id=? AND meal_date=? AND meal_key=? FOR UPDATE",owner,date,meal);
    }
    @Transactional
    public void quantity(String actor,Map<String,Object> data) {
        String owner=owner(actor,Input.text(data,"ownerId",36,false),true),meal=meal(data),dish=Input.text(data,"dishId",36,true);LocalDate date=date(data);
        int delta=Input.decimal(data.get("delta"),-1,1,0).intValueExact();if(delta==0)throw new ApiError(400,"数量变化无效");
        if(data.containsKey("selectOnly") && !(data.get("selectOnly") instanceof Boolean))throw new ApiError(400,"选菜参数无效");
        boolean selectOnly=Boolean.TRUE.equals(data.get("selectOnly"));
        if(selectOnly && delta!=1)throw new ApiError(400,"选菜只能增加新菜品");
        if(!command(actor,data,"quantity"))return;
        mealRow(owner,date,meal);
        if(db.queryForObject("SELECT COUNT(*) FROM cf_recipes WHERE id=? AND active=TRUE AND (owner_id IS NULL OR owner_id=? OR owner_id=? OR id IN (SELECT recipe_id FROM cf_lines WHERE owner_id=?))",Integer.class,dish,actor,owner,owner)==0)
            throw new ApiError(404,"菜品已删除或无权限");
        List<Integer> old=db.queryForList("SELECT qty FROM cf_lines WHERE owner_id=? AND meal_date=? AND meal_key=? AND recipe_id=?",Integer.class,owner,date,meal,dish);
        // Selecting a recipe adds it once. Only the order editor changes existing quantities.
        if(selectOnly && !old.isEmpty())return;
        int qty=(old.isEmpty()?0:old.get(0))+delta;
        if(qty<0||qty>20)throw new ApiError(400,"单品数量需为0至20份");
        if(qty==0)db.update("DELETE FROM cf_lines WHERE owner_id=? AND meal_date=? AND meal_key=? AND recipe_id=?",owner,date,meal,dish);
        else db.update("INSERT INTO cf_lines(owner_id,meal_date,meal_key,recipe_id,qty) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE qty=VALUES(qty)",owner,date,meal,dish,qty);
        db.update("UPDATE cf_meals SET revision=revision+1 WHERE owner_id=? AND meal_date=? AND meal_key=?",owner,date,meal);
    }
    @Transactional
    public void submit(String actor,Map<String,Object> data) {
        String owner=owner(actor,Input.text(data,"ownerId",36,false),true),meal=meal(data);LocalDate date=date(data);
        if(!command(actor,data,"submit"))return;
        Map<String,Object> row=mealRow(owner,date,meal);long revision=((Number)row.get("revision")).longValue();
        long expected=Input.decimal(data.get("revision"),0,Long.MAX_VALUE,0).longValueExact();
        if(expected!=revision)throw new ApiError(409,"好友已修改本餐，请刷新确认后再提交");
        if(revision==((Number)row.get("submitted_revision")).longValue())return;
        List<Map<String,Object>> lines=db.query("SELECT l.recipe_id,l.qty,r.name,r.kcal,r.unit,r.image_id,r.image_url FROM cf_lines l JOIN cf_recipes r ON r.id=l.recipe_id WHERE l.owner_id=? AND l.meal_date=? AND l.meal_key=? ORDER BY l.recipe_id",(r,i)-> {
            String img=r.getString("image_id");
            return Map.of("id",r.getString("recipe_id"),"name",r.getString("name"),"qty",r.getInt("qty"),"kcal",r.getInt("kcal"),"unit",r.getString("unit"),"img",img==null?r.getString("image_url"):publicUrl+"/api/food/images/"+img);
        },owner,date,meal);
        if(lines.isEmpty() && ((Number)row.get("submitted_revision")).longValue()<0)throw new ApiError(400,"本餐还没有选菜");
        int total=lines.stream().mapToInt(l->((Number)l.get("qty")).intValue()*((Number)l.get("kcal")).intValue()).sum();
        db.update("INSERT INTO cf_orders(id,owner_id,meal_date,meal_key,revision,submitted_by,submitted_name,snapshot,total_kcal) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),owner,date,meal,revision,actor,person(actor).get("name"),encode(lines),total);
        db.update("UPDATE cf_meals SET submitted_revision=revision,submitted_at=CURRENT_TIMESTAMP WHERE owner_id=? AND meal_date=? AND meal_key=?",owner,date,meal);
    }
    @Transactional
    public List<Map<String,Object>> history(String actor,String requested,int offset) {
        String owner=owner(actor,requested,true);if(offset<0||offset>100000)throw new ApiError(400,"分页无效");
        return db.query("SELECT * FROM cf_orders WHERE owner_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",(r,i)-> {
            Map<String,Object> o=new LinkedHashMap<>();o.put("id",r.getString("id"));o.put("date",r.getString("meal_date"));o.put("meal",r.getString("meal_key"));
            o.put("revision",r.getLong("revision"));o.put("author",r.getString("submitted_name"));o.put("at",r.getTimestamp("created_at").toLocalDateTime().toString());
            o.put("kcal",r.getInt("total_kcal"));o.put("lines",decode(r.getString("snapshot")));return o;
        },owner,offset);
    }
    @Transactional
    public void saveRecipe(String actor,String id,Map<String,Object> data) {
        pairLock(actor,actor);ensure(actor);
        String name=Input.text(data,"name",30,true),cat=Input.text(data,"cat",10,true),unit=Input.text(data,"unit",10,true),img=Input.text(data,"img",500,false);
        int kcal=Input.decimal(data.get("kcal"),1,5000,0).intValueExact();
        if(!Set.of("主食","菜品","水果","饮品").contains(cat)||!Set.of("100g","份","杯","个").contains(unit))throw new ApiError(400,"分类或单位无效");
        if(!img.isBlank()) {
            String prefix=publicUrl+"/api/media/";
            if(!img.startsWith(prefix)||db.queryForObject("SELECT COUNT(*) FROM cf_media WHERE id=? AND user_id=?",Integer.class,img.substring(prefix.length()),actor)==0)
                throw new ApiError(400,"请先上传自己的图片");
        }
        if(id==null)id=UUID.randomUUID().toString();
        else if(db.queryForObject("SELECT COUNT(*) FROM cf_recipes WHERE id=? AND owner_id=? AND active=TRUE",Integer.class,id,actor)==0)throw new ApiError(403,"只能编辑自己的菜谱");
        if(db.queryForObject("SELECT COUNT(*) FROM cf_recipes WHERE active=TRUE AND owner_id=? AND name=? AND id<>?",Integer.class,actor,name,id)>0)throw new ApiError(409,"已有同名菜品");
        // Submitted snapshots stay immutable; pending meals are marked changed when recipe details change.
        db.update("INSERT INTO cf_recipes(id,owner_id,name,category,kcal,unit,image_url) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),category=VALUES(category),kcal=VALUES(kcal),unit=VALUES(unit),image_url=VALUES(image_url)",id,actor,name,cat,kcal,unit,img);
        db.update("UPDATE cf_meals SET revision=revision+1 WHERE (owner_id,meal_date,meal_key) IN (SELECT owner_id,meal_date,meal_key FROM cf_lines WHERE recipe_id=?)",id);
    }
    @Transactional
    public void deleteRecipe(String actor,String id) {
        pairLock(actor,actor);
        if(db.update("UPDATE cf_recipes SET active=FALSE WHERE id=? AND owner_id=? AND active=TRUE",id,actor)==0)throw new ApiError(403,"只能删除自己的菜谱");
        db.update("UPDATE cf_meals SET revision=revision+1 WHERE (owner_id,meal_date,meal_key) IN (SELECT owner_id,meal_date,meal_key FROM cf_lines WHERE recipe_id=?)",id);
        db.update("DELETE FROM cf_lines WHERE recipe_id=?",id);
    }
    @Transactional
    public void clear(String actor) {
        owner(actor,actor,true);
        LocalDate today=Input.today();LocalDate start=today.minusDays((today.getDayOfWeek().getValue()+1)%7);
        db.update("UPDATE cf_meals SET revision=revision+1 WHERE owner_id=? AND meal_date BETWEEN ? AND ?",actor,start,start.plusDays(6));
        db.update("DELETE FROM cf_lines WHERE owner_id=? AND meal_date BETWEEN ? AND ?",actor,start,start.plusDays(6));
        // Order history is retained even when the editable menu is cleared.
    }
    @Transactional
    public void feedback(String actor,Map<String,Object> data) {
        pairLock(actor,actor);ensure(actor);if(!command(actor,data,"feedback"))return;
        String type=Input.text(data,"type",10,true),content=Input.text(data,"content",300,true),contact=Input.text(data,"contact",30,false);
        if(!Set.of("功能建议","遇到问题","菜谱相关","其他").contains(type)||content.length()<5)throw new ApiError(400,"请选择类型并填写至少5个字");
        db.update("INSERT INTO cf_feedback(id,user_id,author_name,feedback_type,content,contact) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,person(actor).get("name"),type,content,contact);
    }
    public List<Map<String,Object>> feedbacks(String actor,boolean mine,int offset) {
        if(offset<0||offset>100000)throw new ApiError(400,"分页无效");
        String where=mine?" WHERE user_id=?":"";
        Object[] args=mine?new Object[]{actor,offset}:new Object[]{offset};
        return db.query("SELECT * FROM cf_feedback"+where+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",(r,i)-> {
            Map<String,Object> f=new LinkedHashMap<>();boolean own=actor.equals(r.getString("user_id"));
            f.put("id",r.getString("id"));f.put("author",r.getString("author_name"));f.put("mine",own);f.put("type",r.getString("feedback_type"));f.put("content",r.getString("content"));
            f.put("at",r.getTimestamp("created_at").toLocalDateTime().toString());if(own)f.put("contact",r.getString("contact"));return f;
        },args);
    }
    public Map<String,Object> image(String id) {
        List<Map<String,Object>> found=db.query("SELECT mime,content FROM cf_images WHERE id=?",(r,i)->Map.of("mime",r.getString(1),"bytes",r.getBytes(2)),id);
        if(found.isEmpty())throw new ApiError(404,"图片不存在");return found.get(0);
    }
}
