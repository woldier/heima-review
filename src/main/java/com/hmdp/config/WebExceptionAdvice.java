package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BizException;
import com.sun.deploy.net.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(HttpServletResponse response, RuntimeException e) {
        log.error(e.toString(), e);
        response.setStatus(405);
        return Result.fail("服务器异常");
    }

    /**
     * 业务异常处理
     * @param e
     * @return
     */
    @ExceptionHandler(BizException.class)
    public Result handleBizException(HttpServletResponse response,BizException e) {
        response.setStatus(403);
        log.info(e.getErrorMsg());
        return Result.fail(e.getErrorMsg());
    }


}
