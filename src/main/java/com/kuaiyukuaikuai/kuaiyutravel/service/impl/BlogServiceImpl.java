package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.dto.ScrollResult;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Follow;
import com.kuaiyukuaikuai.kuaiyutravel.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.BlogMapper;
import com.kuaiyukuaikuai.kuaiyutravel.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.service.FollowService;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 0
 * @since 2026-04-17
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowService followService;

    /**
     * 查询热门博客
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询博客
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //1.查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        //2.查询用户
        queryBlogUser(blog);
        //3.查询blog是否被用户点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 判断是否已经点过赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();

        // 2.判断是否已经点过赞
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果未点赞，则点赞
            // 3.1.保存数据到数据库
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 保存用户到Redis
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，则取消点赞
            // 4.1.数据库删除数据
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.Redis删除用户
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 1.1 判断用户是否为空
        if (top5 == null || top5.isEmpty()) { //isEmpty()空集合返回true null表示空指针,Redis key 不存在，返回 null
            return Result.ok(Collections.emptyList());// 返回一个空的集合
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream() // 将 Set<String> 转为流
                .map(Long::valueOf) // 每个 String 通过 Long.valueOf() 转为 Long,map()用于将流中的每个元素转换为另一种形式。
                .collect(Collectors.toList()); // 收集为 List<Long>
        String idStr = StrUtil.join(",", ids);

        // 3.根据用户id查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记
        for (Follow follow : follows) {
            String key = "feed:" + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();

        // 2.查询收件箱
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 3.解析数据
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 3.1.获取id
            String blogId = typedTuple.getValue();
            ids.add(Long.valueOf(blogId));
            // 3.2.获取分数(时间戳)
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 4.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            // 5.查询blog有关的用户
            queryBlogUser(blog);
            // 6.查询blog是否被点赞
            isBlogLiked(blog);
        }
        // 7.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}