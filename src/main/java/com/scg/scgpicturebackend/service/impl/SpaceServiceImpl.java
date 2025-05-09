package com.scg.scgpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scg.scgpicturebackend.exception.BusinessException;
import com.scg.scgpicturebackend.exception.ErrorCode;
import com.scg.scgpicturebackend.exception.ThrowUtils;
import com.scg.scgpicturebackend.model.dto.space.SpaceAddRequest;
import com.scg.scgpicturebackend.model.dto.space.SpaceQueryRequest;
import com.scg.scgpicturebackend.model.entity.Picture;
import com.scg.scgpicturebackend.model.entity.Space;
import com.scg.scgpicturebackend.model.entity.User;
import com.scg.scgpicturebackend.model.enums.SpaceLevelEnum;
import com.scg.scgpicturebackend.model.vo.PictureVO;
import com.scg.scgpicturebackend.model.vo.SpaceVO;
import com.scg.scgpicturebackend.model.vo.SpaceVO;
import com.scg.scgpicturebackend.model.vo.UserVO;
import com.scg.scgpicturebackend.service.SpaceService;
import com.scg.scgpicturebackend.mapper.SpaceMapper;
import com.scg.scgpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author 53233
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-05-07 13:53:29
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{


    @Resource
    private UserService userService;

    //spring提供的编程式事务管理器
    @Resource
    private TransactionTemplate transactionTemplate;

    //使用concurrentHashMap作为锁
    Map<Long,Object> lockMap = new ConcurrentHashMap<>();

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        //1.填充参数默认值
        //转换实体类和DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        /*设置默认值*/
        if(StrUtil.isBlank(space.getSpaceName())){
            space.setSpaceName("默认空间");
        }
        if(space.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        /*填充容量和大小*/
        this.fillSpaceBySpaceLevel(space);

        //2.校验参数
        this.validSpace(space,true);

        //3.校验权限 非管理员只能创建普通空间
        Long userId = loginUser.getId();
        space.setUserId(userId);

        if(SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"非管理员只能创建普通空间");
        }

        //4.控制同一个用户只能创建一个私有空间 加锁
        /*锁方案1：（有内存泄露风险 因为常量池没有那么容易被释放，一直有个空间占据）
        相同值的对象是有同一个存储空间的 使用intern 这样的话不同的string对象但是值一样可以得到同一个数据*/
        //String lock = String.valueOf(userId).intern();

        /*锁方案2： 使用concurrenthashmap 锁他的对象 然后使用finally释放资源*/
        //使用concurrentmap的api特性，如果id存在返回id值，如果id不存在创建value为指定的值
        Object lock = lockMap.computeIfAbsent(userId, k -> new Object());

        //根据用户id 为相同用户加锁
        synchronized (lock){
            try{
                //把操作封装到事务中
                Long newSpaceId = transactionTemplate.execute(status ->{
                    //根据userid去space表判断用户是否已经创建过空间
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, userId)
                            .exists(); // exists比count快 因为count是扫全表

                    //如果已有空间 不能再创建
                    ThrowUtils.throwIf(exists,ErrorCode.OPERATION_ERROR,"用户已经创建过空间");
                    boolean save = this.save(space);
                    ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"保存空间信息到数据库失败");

                    //返回新写入的数据id
                    return space.getId();
                });
                return newSpaceId;

            }finally {
                lockMap.remove(userId);
            }
        }
    }

    @Override
    public void validSpace(Space space,boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);

        /*创建时才校验空间名*/
        if(add){
            // 空间名不能为空
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名不能为空");
            }

            if (spaceLevelEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }

        //修改数据时，对空间名和级别进行校验
        if(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名过长");
        }
        if(spaceLevel !=null && spaceLevelEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        /*关联查询用户信息 根据分页查询到的userid 去数据库查询出这些user的详细信息*/
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2 转换成map kv的形式
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        /*关联完整的空间信息和用户信息 然后返回给spaceVOPage*/
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        //重新填充进分页的数据中
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        //queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    //根据空间的等级 填充对应的空间和可存放数据的条数
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());

        if(spaceLevelEnum != null){
            long maxCount = spaceLevelEnum.getMaxCount();
            if(space.getMaxCount() == null){
                space.setMaxCount(maxCount);
            }

            long maxSize = spaceLevelEnum.getMaxSize();
            if(space.getMaxSize() == null){
                space.setMaxSize(maxSize);
            }
        }
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        //仅本人或管理员可编码
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}




