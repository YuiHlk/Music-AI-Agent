package com.musicai.agent.application;

/**
 * 表示应用层按标识查询项目、任务或产物时未找到目标资源。
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向调用方的错误说明
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
