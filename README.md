
# 1. 项目初始化
工具包：
- mybatisPlus
- hutool
- knife4j 自动生成接口文档   访问： http://localhost:9999/api/doc.html

**项目基础代码** 
请求响应体封装

    封装前端传参和后端返回参数的实体类，便于传输数据  
    com.scg.scgpicturebackend.common

异常封装 

    封装异常类，便于处理异常自定义错误码，对错误进行收敛，便于前端统一处理
    定义全局异常处理器
    com.scg.scgpicturebackend.exception

**解决跨域**

只在浏览器出现，是浏览器的同源策略，如果发送请求的网址和接收请求的网址是相同的 否则就会有跨域问题

 解决跨域问题的方法：

    1.后端来支持跨域
    2.代理 nginx 第三方工具等方法
    3.直接用croscheck注解
    4.全局跨域配置 com.scg.scgpicturebackend.config.CorsConfig


# 2. 用户模块
注册，登录，获取当前登录用户，注销，权限控制，用户管理

使用mybatisX插件生成实体类等代码 

- 定义user表的枚举类
- 定义请求接受类和请求封装类 dto
- 设置要返回给前端哪些数据（脱敏） vo
- 权限控制 配合aop和注解类实现 authInterceptor+AuthCheck
- 分页 使用mybatisplus分页 
-  **天坑： 3.5.9的mybatisplus要额外安装分页插件和配置一个配置类**
-  **天坑： 要注意 前端和后端的long精度不一样 如果直接传的话可能会丢失精度 因为这里的user id是assign_id策略 生成的long比较大 可能前端没有这么高的精度 所以要定义一个配置类JsonConfig来对long进行转换**

