package com.example.farm.common.api;

/**
 * API 统一响应状态码枚举
 */
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "系统异常"),
    VALIDATE_FAILED(400, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),

    // 你可以在这里继续追加农场相关的业务状态码
    PRINTER_OFFLINE(10001, "打印机离线"),
    PRINTER_BUSY(10002, "打印机正忙");

    private final long code;
    private final String message;

    ResultCode(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}