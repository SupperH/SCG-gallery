package com.scg.scgpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.scg.scgpicturebackend.annotation.AuthCheck;
import com.scg.scgpicturebackend.common.BaseResponse;
import com.scg.scgpicturebackend.common.DeleteRequest;
import com.scg.scgpicturebackend.common.ResultUtils;
import com.scg.scgpicturebackend.constant.UserConstant;
import com.scg.scgpicturebackend.exception.BusinessException;
import com.scg.scgpicturebackend.exception.ErrorCode;
import com.scg.scgpicturebackend.exception.ThrowUtils;
import com.scg.scgpicturebackend.manager.CosManager;
import com.scg.scgpicturebackend.model.dto.picture.*;
import com.scg.scgpicturebackend.model.entity.Picture;
import com.scg.scgpicturebackend.model.entity.User;
import com.scg.scgpicturebackend.model.enums.PictureReviewStatusEnum;
import com.scg.scgpicturebackend.model.vo.PictureTagCategory;
import com.scg.scgpicturebackend.model.vo.PictureVO;
import com.scg.scgpicturebackend.service.PictureService;
import com.scg.scgpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    //上传图片 可以重新上传
    /*这个接口是选择完图片就会进行调用 然后上传到服务器把图片信息和谁上传的入库
    * 之后点击创建按钮调用的其实是edit 用来补全描述信息*/
    @PostMapping("/upload")
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request){

        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);

        return ResultUtils.success(pictureVO);

    }

    //使用url地址上传图片
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request){

        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(pictureUploadRequest.getFileUrl(), pictureUploadRequest, loginUser);

        return ResultUtils.success(pictureVO);

    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest,HttpServletRequest request){
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();

        //判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //仅本人或管理员可删除
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //操作数据库
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     *
     * @param pictureUpdateRequest
     * @return
     */
    /*这个目前没用到 管理员用的也是editPicture 考虑废除*/
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        /*注意将 list 转为 string*/
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        /*补充审核参数*/
        pictureService.fillReviewParams(oldPicture, userService.getLoginUser(request));

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        Picture picture = pictureService.getById(id);

        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(picture);

    }

    /**
     * 根据 id 获取图片（封装类） 脱敏 用户可用
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        Picture picture = pictureService.getById(id);

        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用） 图片管理功能用
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        //构建查询体
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQueryRequest);

        //执行分页查询
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), queryWrapper);

        return ResultUtils.success(picturePage);

    }

    /**
     * 分页获取图片列表（封装类）脱敏 首页展示用
     * 查询出来的条数和上面的方法一样 但是上面的不会脱敏 因为需要进行管理
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        //不允许用户一次查询超过20防止爬虫
        ThrowUtils.throwIf(size > 20 , ErrorCode.PARAMS_ERROR);

        /*前台的首页只能查询到审核通过的数据*/
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        //构建查询体 就是拼接sql在里面
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQueryRequest);

        //执行分页查询
        Page<Picture> page = pictureService.page(new Page<>(current, size), queryWrapper);

        /*把数据脱敏 然后这个方法里还会关联查询到的图片和这个图片对应的脱敏后的用户信息*/
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(page, request);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    /*这个方法也会在上传图片后点击创建调用 用来补全上传图片后的描述信息*/
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 前台的请求类 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        /*注意将 list 转为 string*/
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));

        //设置编辑时间
        picture.setEditTime(new Date());

        // 数据校验
        pictureService.validPicture(picture);

        User loginUser = userService.getLoginUser(request);

        /*补充审核参数*/
        pictureService.fillReviewParams(picture, loginUser);

        // 判断是否存在
        long id = pictureEditRequest.getId();
        /*查询图片从数据库*/
        Picture oldPicture = pictureService.getById(id);

        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //仅本人和管理员可以编辑
        if(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        /*写死了 后面要用数据库存储动态获取*/
        List<String> tagList = Arrays.asList("热门", "动漫", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("壁纸", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    //审核图片 仅管理员
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,HttpServletRequest request) {

        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        pictureService.doPictureReview(pictureReviewRequest, userService.getLoginUser(request));

        return ResultUtils.success(true);
    }

    //批量抓取并创建图片 仅管理员
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {

        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, userService.getLoginUser(request));

        return ResultUtils.success(uploadCount);
    }
}

