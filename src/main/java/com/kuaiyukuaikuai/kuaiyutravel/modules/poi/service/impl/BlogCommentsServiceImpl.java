package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.BlogCommentsVO;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.BlogComments;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.BlogCommentsMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogCommentsService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 博客评论服务实现类
 */
@Slf4j
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增评论
     * @param blogComments 评论信息
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result saveComment(BlogComments blogComments) {
        // 1. 从UserHolder获取当前登录用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("用户未登录");
        }

        // 2. 注入用户ID
        blogComments.setUserId(userDTO.getId());

        // 3. 设置默认值
        if (blogComments.getParentId() == null) {
            blogComments.setParentId(0L);
        }
        if (blogComments.getAnswerId() == null) {
            blogComments.setAnswerId(0L);
        }

        // 4. 保存评论
        boolean success = save(blogComments);
        if (success) {
            // 5. 更新对应博客的评论数
            blogService.update()
                    .setSql("comments = comments + 1")
                    .eq("id", blogComments.getBlogId())
                    .update();
            return Result.ok();
        } else {
            return Result.fail("评论保存失败");
        }
    }

    /**
     * 查询评论列表
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 评论列表
     */
    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        // 1. 分页查询顶级评论（parentId为0或null）
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .orderByDesc("liked")
                .orderByDesc("create_time")
                .page(new Page<>(current, 10));

        // 2. 转换为VO对象
        IPage<BlogCommentsVO> voPage = page.convert(this::convertToVO);

        // 3. 优化N+1查询：批量获取子评论
        List<BlogCommentsVO> topLevelComments = voPage.getRecords();
        if (!topLevelComments.isEmpty()) {
            // 3.1 提取所有顶级评论的ID
            List<Long> topLevelIds = topLevelComments.stream()
                    .map(BlogCommentsVO::getId)
                    .collect(Collectors.toList());
            
            // 3.2 一次性查询所有子评论
            List<BlogComments> childComments = query()
                    .in("parent_id", topLevelIds)
                    .orderByAsc("create_time")
                    .list();
            
            // 3.3 转换子评论为VO
            List<BlogCommentsVO> childCommentVOs = childComments.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            
            // 3.4 按parentId分组
            Map<Long, List<BlogCommentsVO>> childCommentsMap = childCommentVOs.stream()
                    .collect(Collectors.groupingBy(BlogCommentsVO::getParentId));
            
            // 3.5 为每个顶级评论设置子评论
            topLevelComments.forEach(commentVO -> {
                commentVO.setChildren(childCommentsMap.getOrDefault(commentVO.getId(), Collections.emptyList()));
            });
        }

        // 4. 如果当前用户已登录，检查是否已点赞
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO != null) {
            // 4.1 收集所有评论ID（包括顶级评论和子评论）
            List<Long> allCommentIds = new ArrayList<>();
            topLevelComments.forEach(comment -> {
                allCommentIds.add(comment.getId());
                if (comment.getChildren() != null) {
                    comment.getChildren().forEach(child -> allCommentIds.add(child.getId()));
                }
            });
            
            // 4.2 批量检查点赞状态
            Map<Long, Boolean> likedMap = new HashMap<>();
            allCommentIds.forEach(id -> {
                likedMap.put(id, isLiked(id, userDTO.getId()));
            });
            
            // 4.3 设置点赞状态
            topLevelComments.forEach(comment -> {
                // 检查顶级评论是否已点赞
                comment.setIsLike(likedMap.getOrDefault(comment.getId(), false));
                // 检查子评论是否已点赞
                if (comment.getChildren() != null) {
                    comment.getChildren().forEach(childComment -> {
                        childComment.setIsLike(likedMap.getOrDefault(childComment.getId(), false));
                    });
                }
            });
        }

        return Result.ok(voPage);
    }

    /**
     * 评论点赞
     * @param commentId 评论ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result likeComment(Long commentId) {
        // 1. 从UserHolder获取当前登录用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("用户未登录");
        }

        Long userId = userDTO.getId();
        // 2. 构建Redis键（使用专属前缀，避免与博客点赞冲突）
        String key = "blog:comment:liked:" + commentId;

        // 3. 检查是否已点赞
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, userId.toString()))) {
            // 已点赞，取消点赞
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
            // 更新数据库点赞数
            update().setSql("liked = liked - 1").eq("id", commentId).update();
            return Result.ok("取消点赞成功");
        } else {
            // 未点赞，添加点赞
            stringRedisTemplate.opsForSet().add(key, userId.toString());
            // 更新数据库点赞数
            update().setSql("liked = liked + 1").eq("id", commentId).update();
            return Result.ok("点赞成功");
        }
    }

    /**
     * 检查用户是否已点赞
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    private boolean isLiked(Long commentId, Long userId) {
        // 使用专属前缀，避免与博客点赞冲突
        String key = "blog:comment:liked:" + commentId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, userId.toString()));
    }

    /**
     * 将评论实体转换为VO对象
     * @param comment 评论实体
     * @return 评论VO对象
     */
    private BlogCommentsVO convertToVO(BlogComments comment) {
        BlogCommentsVO vo = new BlogCommentsVO();
        // 复制基本属性
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setBlogId(comment.getBlogId());
        vo.setParentId(comment.getParentId());
        vo.setAnswerId(comment.getAnswerId());
        vo.setContent(comment.getContent());
        vo.setLiked(comment.getLiked());
        vo.setStatus(comment.getStatus());
        vo.setCreateTime(comment.getCreateTime());
        vo.setUpdateTime(comment.getUpdateTime());

        // 查询评论者信息
        UserDTO userDTO = userService.getUserDTOById(comment.getUserId());
        if (userDTO != null) {
            vo.setNickName(userDTO.getNickName());
            vo.setIcon(userDTO.getIcon());
        }

        // 查询被回复者信息
        if (comment.getAnswerId() != null && comment.getAnswerId() > 0) {
            UserDTO answerUserDTO = userService.getUserDTOById(comment.getAnswerId());
            if (answerUserDTO != null) {
                vo.setAnswerNickName(answerUserDTO.getNickName());
            }
        }

        return vo;
    }
}