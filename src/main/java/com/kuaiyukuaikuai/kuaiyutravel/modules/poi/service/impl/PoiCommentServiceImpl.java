package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiCommentMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiCommentService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.vo.PoiCommentVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PoiCommentServiceImpl extends ServiceImpl<PoiCommentMapper, PoiComment> implements PoiCommentService {

    @Resource
    private PoiMapper poiMapper;
    
    @Resource
    private UserService userService; // 注入用户服务，用于查头像昵称

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result saveComment(PoiComment poiComment) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        poiComment.setUserId(userId);
        
        // 设置默认满分，防止前端没传
        if (poiComment.getScore() == null) {
            poiComment.setScore(5);
        }

        // 2. 保存评价内容入库
        save(poiComment);

        // 3. 原子操作：将 tb_poi 表里的 comments 数量 + 1
        poiMapper.incrCommentCount(poiComment.getPoiId());

        return Result.ok();
    }

    @Override
    public Result queryCommentPage(Long poiId, Integer current, Integer size) {
        // 1. 健壮性校验复用全局常量
        if (current == null || current < 1) current = 1;
        if (size == null || size <= 0) size = SystemConstants.DEFAULT_PAGE_SIZE;

        // 2. 分页查出评价记录
        Page<PoiComment> page = lambdaQuery()
                .eq(PoiComment::getPoiId, poiId)
                .orderByDesc(PoiComment::getCreateTime) // 按最新评价排序
                .page(new Page<>(current, size));

        List<PoiComment> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(page);
        }

        // 3. 提取所有的评价者 ID (去重)
        List<Long> userIds = records.stream()
                .map(PoiComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 4. 批量查询用户表，转为 Map 方便 O(1) 查找
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        // 5. 遍历评价记录，组装给前端的 VO 视图对象
        List<PoiCommentVO> voList = records.stream().map(record -> {
            PoiCommentVO vo = new PoiCommentVO();
            vo.setCommentId(record.getId()); // 评价ID
            vo.setContent(record.getContent()); // 评价内容
            vo.setScore(record.getScore()); // 评分
            vo.setCreateTime(record.getCreateTime()); // 评价时间
            
            // 从 Map 匹配用户信息，赋值到 UserDTO 继承过来的属性上
            User user = userMap.get(record.getUserId());
            if (user != null) {
                vo.setId(user.getId()); // 这是用户的ID
                vo.setNickName(user.getNickName()); // 用户昵称
                vo.setIcon(user.getIcon()); // 用户头像
            }
            return vo;
        }).collect(Collectors.toList());

        // 6. 把拼装好的 VO 列表塞回一个新的 Page 对象中返回
        Page<PoiCommentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        
        return Result.ok(voPage);
    }
}