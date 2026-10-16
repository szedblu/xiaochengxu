package com.tencent.wxcloudrun.api;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MediaController {
    private final MediaService media;
    public MediaController(MediaService media){this.media=media;}
    @GetMapping("/api/media/{id}") public ResponseEntity<byte[]> image(@PathVariable String id){
        Map<String,Object> value=media.media(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)value.get("mime"))).header("X-Content-Type-Options","nosniff").header("Cache-Control","public,max-age=86400").body((byte[])value.get("bytes"));
    }
}
