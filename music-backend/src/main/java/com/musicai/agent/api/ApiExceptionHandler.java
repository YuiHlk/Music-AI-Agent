package com.musicai.agent.api;

import com.musicai.agent.application.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/** 将应用异常映射为稳定且不泄露内部细节的 REST 错误 envelope。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 将认证失败转换为统一的 401 响应。
     *
     * @param exception 认证异常
     * @param request HTTP 请求
     * @return 401 错误响应
     */
    @ExceptionHandler(InvalidAccessKeyException.class)
    ResponseEntity<ApiError> unauthorized(InvalidAccessKeyException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_KEY", exception.getMessage(), request);
    }

    /**
     * @param exception 未找到资源异常
     * @param request HTTP 请求
     * @return 404 错误响应
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * @param exception 非法参数异常
     * @param request HTTP 请求
     * @return 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", exception.getMessage(), request);
    }

    /**
     * 汇总请求体的字段错误，便于前端一次显示多个校验结果。
     *
     * @param exception 请求体字段验证异常
     * @param request HTTP 请求
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    /**
     * @param exception 查询参数或方法级验证异常
     * @param request HTTP 请求
     * @return 400 错误响应
     */
    @ExceptionHandler({ConstraintViolationException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage(), request);
    }

    /**
     * @param exception 当前状态不允许操作的异常
     * @param request HTTP 请求
     * @return 409 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", exception.getMessage(), request);
    }

    /**
     * 记录完整异常但只向客户端返回固定消息，避免泄露内部实现细节。
     * @param exception 未处理异常
     * @param request HTTP 请求
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled API error on {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The server could not complete the request", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                            HttpServletRequest request) {
        String safeMessage = message == null || message.isBlank() ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, safeMessage,
                request.getRequestURI(), Instant.now()));
    }

    /**
     * @param status HTTP 状态码
     * @param code 稳定机器错误码
     * @param message 安全的客户端消息
     * @param path 请求路径
     * @param timestamp 错误时间
     */
    record ApiError(int status, String code, String message, String path, Instant timestamp) {
    }
}
