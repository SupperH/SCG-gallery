
# 1. 项目初始化
工具包：
mybatisPlus
hutool
knife4j 自动生成接口文档   访问： http://localhost:9999/api/doc.html

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
