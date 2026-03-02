package com.example.farm.common.exception;

import com.example.farm.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理大管家
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截我们自定义的业务异常 (如：打印机离线，库存不足等)
     */
    @ExceptionHandler(value = BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        // 业务异常通常是正常的逻辑阻断，打印个普通日志即可
        log.warn("业务异常提示: {}", e.getMessage());
        return Result.failed(e.getMessage());
    }

    /**
     * 拦截所有未知的系统运行时异常 (空指针、数据库连不上等)
     */
    @ExceptionHandler(value = Exception.class)
    public Result<Object> handleException(Exception e) {
        // 系统级异常属于严重错误，必须打印完整的 error 日志供开发者排查
        log.error("系统内部异常: ", e);
        return Result.failed("系统开小差了，请稍后再试");
    }
}