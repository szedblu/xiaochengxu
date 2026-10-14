package com.tencent.wxcloudrun.api;

import java.util.Map;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
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
    public ResponseEntity<?> business(ApiError e, HttpServletRequest request) {
        LOG.warn("API rejected: path={}, status={}", request.getRequestURI(), e.status, SafeLogs.exception(e));
        return error(e.status, e.getMessage());
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate(DuplicateKeyException e) {
        LOG.warn("API duplicate key", SafeLogs.exception(e));
        return error(409, "账号已被使用，请换一个账号");
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<?> badRequest(Exception e) {
        LOG.warn("API invalid request", SafeLogs.exception(e));
        return error(400, "请求参数不正确");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e, HttpServletRequest request) {
        Throwable cause = e;
        for (int i = 0; i < 16 && cause.getCause() != null; i++) cause = cause.getCause();
        String location = e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0].toString();
        LOG.error("API operation failed: path={}, exception={}, cause={}, location={}", request.getRequestURI(),
                e.getClass().getSimpleName(), cause.getClass().getSimpleName(), location, SafeLogs.exception(e));
        if (cause instanceof SQLException) {
            SQLException sql = (SQLException) cause;
            LOG.error("Database operation failed: sqlState={}, vendorCode={}", sql.getSQLState(), sql.getErrorCode());
            if (sql.getErrorCode() == 1146 || sql.getErrorCode() == 1049)
                return error(500, "业务数据库或表未就绪，请检查 MYSQL_DATABASE 配置");
            if (sql.getErrorCode() == 1366)
                return error(500, "数据库字符编码不兼容，请检查 utf8mb4 配置");
        }
        if (e instanceof DataAccessException) return error(500, "数据库操作失败，请查看服务运行日志");
        return error(500, "服务暂时不可用，请稍后重试");
    }
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("code", status, "message", message));
    }
}
