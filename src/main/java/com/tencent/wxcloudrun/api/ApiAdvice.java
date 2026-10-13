package com.tencent.wxcloudrun.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ApiAdvice.class);
    @ExceptionHandler(ApiError.class)
    public ResponseEntity<?> business(ApiError e) { return error(e.status, e.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate() { return error(409, "账号已被使用，请换一个账号"); }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<?> badRequest(Exception e) { return error(400, "请求参数不正确"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        LOG.error("API operation failed: {}", e.getClass().getSimpleName());
        return error(500, "服务暂时不可用，请稍后重试");
    }
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("code", status, "message", message));
    }
}
