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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import com.kuaiyukuaikuai.kuaiyutravel.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客服务实现类
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowService followService;
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 查询热门博客
     *
     * @param current 当前页码
     * @return 热门博客列表
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
     * 根据ID查询博客
     *
     * @param id 博客ID
     * @return 博客详情
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

    /**
     * 判断博客是否被点赞
     *
     * @param blog 博客信息
     */
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

    /**
     * 查询博客用户信息
     *
     * @param blog 博客信息
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 点赞博客
     *
     * @param id 博客ID
     * @return 操作结果
     */
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

    /**
     * 查询博客点赞用户
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 1.1 判断用户是否为空
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
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

    /**
     * 保存博客 (RabbitMQ 异步推流版)
     *
     * @param blog 博客信息
     * @return 保存结果
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        blog.setUserId(user.getId());

        // 2. 保存探店博文到数据库
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }

        // 3. 异步推送笔记给粉丝 (核心改造)
        // 封装要发送的消息体（博主ID和刚刚保存的博客ID）
        Map<String, Object> message = new HashMap<>();
        message.put("userId", user.getId());
        message.put("blogId", blog.getId());

        // 将任务丢给 RabbitMQ 的博客交换机
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BLOG_EXCHANGE,
                RabbitMQConfig.BLOG_ROUTING_KEY,
                message
        );

        // 4. 立刻返回id给前端
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注用户的博客
     *
     * @param max    最大ID
     * @param offset 偏移量
     * @return 博客列表
     */
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

    /**
     * 根据地点id查询博客
     *
     * @param poiId   地点id
     * @param current 当前页码
     * @return 博客列表
     */
    @Override
    public Result queryBlogByPoiId(Integer current, Long poiId) {
        // 深度分页防御机制
        // 如果用户恶意传入非常大的页码（比如大于 100），直接返回空列表
        if (current > 100) {
            // 返回空数组，不去给 MySQL 数据库施加压力
            return Result.ok(Collections.emptyList());
        }
        // 1.根据地点id查询博客
        Page<Blog> page = query()
                .eq("poi_id", poiId)
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        // 2.封装并返回
        return Result.ok(blogs);
    }
}