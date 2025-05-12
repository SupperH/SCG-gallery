package com.scg.scgpicturebackend.api.getpicturebatch;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.scg.scgpicturebackend.exception.BusinessException;
import com.scg.scgpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

@Slf4j
public class TestGetPicturePatch {
    public static void main(String[] args) {

        String fetchUrl = String.format("https://www.bizhihui.com/search.php?q=%s", "初音");
        //调用jsoup
        Document document = null;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取页面失败");
        }

        //解析内容
        Element div = document.getElementsByClass("clear").first();
        if(ObjUtil.isEmpty(div)){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取元素失败");
        }
        Elements imgElementList = div.select("img");
        for(Element imgElement : imgElementList){


        String fileUrl = imgElement.attr("src");
        if(StrUtil.isBlank(fileUrl)){
            log.info("当前链接为空，已跳过：{}",fileUrl);
            return;
        }
        //处理图片的url 防止转移或和cos存储冲突 因为腾讯云的cos接受不了很长很长的url 所以要把没意义的参数全部截断
        int questionMarkIndex = fileUrl.indexOf("?");
        if(questionMarkIndex > -1){
            fileUrl = fileUrl.substring(0,questionMarkIndex);
        }
            System.out.println(fileUrl);
        }
    }
}
