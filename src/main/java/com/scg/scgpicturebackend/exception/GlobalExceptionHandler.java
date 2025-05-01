package com.scg.scgpicturebackend.exception;

import com.scg.scgpicturebackend.common.BaseResponse;
import com.scg.scgpicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理器
@RestControllerAdvice //环绕切面，可用在方法前后调用一些方法 aop用
@Slf4j
public class GlobalExceptionHandler {

    /*只要在项目中 任何一个方法抛出了注解指定的异常 都会被这个方法 环绕切面捕获到*/
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e){
        log.error("BusinessException",e);
        return ResultUtils.error(e.getCode(),e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException e){
        log.error("RuntimeException",e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"系统错误");
    }

}
