package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }

    /**
     * 业务异常处理
     * @param e
     * @return
     */
    @ExceptionHandler(BizException.class)
    public Result handleBizException(BizException e) {
        log.info(e.getErrorMsg());
        return Result.fail(e.getErrorMsg());
    }

}
