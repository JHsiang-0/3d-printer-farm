package com.example.farm.common.exception;

import com.example.farm.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("业务异常提示: {}", e.getMessage());
        return Result.failed(e.getMessage());
    }

    /**
     * 请求方法不支持（405）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String[] supportedMethods = e.getSupportedMethods();
        String supported = supportedMethods == null ? "N/A" : String.join(",", supportedMethods);
        log.warn("请求方法不支持: method={}, uri={}, supported={}, detail={}",
                e.getMethod(), request.getRequestURI(), supported, e.getMessage());
        return Result.failed("请求方法不支持，当前方法=" + e.getMethod() + "，支持方法=" + supported);
    }

    /**
     * 未知系统异常。
     */
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统内部异常: ", e);
        return Result.failed("系统开小差了，请稍后再试");
    }
}
