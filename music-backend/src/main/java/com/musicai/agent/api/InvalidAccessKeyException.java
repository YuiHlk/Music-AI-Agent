package com.musicai.agent.api;

/** 表示访问密钥认证失败，由 REST 异常处理器映射为 HTTP 401。 */
public class InvalidAccessKeyException extends RuntimeException {

    /** 创建不暴露密钥内容的认证失败异常。 */
    public InvalidAccessKeyException() {
        super("Invalid project access key");
    }
}
