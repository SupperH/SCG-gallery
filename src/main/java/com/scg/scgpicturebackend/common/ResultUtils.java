package com.scg.scgpicturebackend.common;

import com.scg.scgpicturebackend.exception.ErrorCode;

//响应结果工具类
public class ResultUtils {

    //成功
    public static <T> BaseResponse<T> success(T data){
        return new BaseResponse<>(0,data,"ok");
    }

    //失败 带错误码
    public static BaseResponse<?> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

    //失败带错误码 错误信息
    public static BaseResponse<?> error(int code, String message){
        return new BaseResponse<>(code,message);
    }

    //失败带错误码 错误信息
    public static BaseResponse<?> error(ErrorCode errorCode, String message){
        return new BaseResponse<>(errorCode.getCode(),null,message);
    }
}
