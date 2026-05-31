package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.ScrollResult;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.BlogMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.FollowService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 博客服务实现类
 */
@Slf4j
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
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        if (records.isEmpty()) {
            return records;
        }

        // 批量查询用户信息，避免 N+1
        List<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查询点赞状态
        UserDTO currentUser = UserHolder.getUser();
        Set<String> likedKeys = records.stream()
                .map(blog -> "blog:liked:" + blog.getId())
                .collect(Collectors.toSet());

        records.forEach(blog -> {
            // 填充用户信息
            User user = userMap.get(blog.getUserId());
            if (user != null) {
                blog.setIcon(user.getIcon());
                blog.setName(user.getNickName());
            } else {
                blog.setIcon("");
                blog.setName("未知用户");
            }
            // 检查点赞状态
            if (currentUser != null) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score("blog:liked:" + blog.getId(), currentUser.getId().toString());
                blog.setIsLike(score != null);
            }
        });
        return records;
    }

    /**
     * 根据ID查询博客
     *
     * @param id 博客ID
     * @return 博客详情
     */
    @Override
    public Blog queryBlogById(Long id) {
        // 1.查询博客
        Blog blog = getById(id);
        if (blog == null) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND, "笔记不存在");
        }
        // 2.查询用户
        queryBlogUser(blog);
        // 3.查询blog是否被用户点赞
        isBlogLiked(blog);
        return blog;
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
        if (user == null) {
            blog.setIcon("");
            blog.setName("未知用户");
            return;
        }
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 点赞博客
     *
     * @param id 博客ID
     */
    @Override
    public void likeBlog(Long id) {
        // 1.获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
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
    }

    /**
     * 查询博客点赞用户
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 1.1 判断用户是否为空
        if (top5 == null || top5.isEmpty()) {
            return Collections.emptyList();
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        // 3.根据用户id查询用户（使用Java排序替代SQL拼接，避免注入风险）
        List<User> users = userService.listByIds(ids);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<UserDTO> userDTOS = ids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4.返回
        return userDTOS;
    }

    /**
     * 保存博客 (纯 MQ 异步推流 + AI 向量库同步版)
     *
     * @param blog 博客信息
     */
    @Override
    public void saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        blog.setUserId(user.getId());

        // 2. 保存探店博文到数据库
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "新增笔记失败");
        }

        // 3. 异步推送笔记给粉丝 (原有逻辑)
        Map<String, Object> message = new HashMap<>();
        message.put("userId", user.getId());
        message.put("blogId", blog.getId());

        // 将任务丢给 RabbitMQ 的博客交换机 (路由给粉丝推送队列)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BLOG_EXCHANGE,
                RabbitMQConfig.BLOG_ROUTING_KEY,
                message);

        // ==========================================
        // 4. 纯 MQ 方案核心改造：投递给 AI 知识库同步队列
        // ==========================================
        // 这里只发送刚保存的游记 ID，让消费者自己去查数据库，保证数据是最新的
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BLOG_EXCHANGE,
                RabbitMQConfig.AI_SYNC_ROUTING_KEY, // AI 知识库同步专用路由键
                blog.getId()
        );
    }

    /**
     * 查询关注用户的博客
     *
     * @param max    最大ID
     * @param offset 偏移量
     * @return 博客列表
     */
    @Override
    public ScrollResult queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        Long userId = user.getId();

        // 2.查询收件箱
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return new ScrollResult();
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
        // 4.根据id查询blog（使用Java排序替代SQL拼接，避免注入风险）
        List<Blog> blogList = query().in("id", ids).list();
        Map<Long, Blog> blogMap = blogList.stream().collect(Collectors.toMap(Blog::getId, b -> b));
        List<Blog> blogs = ids.stream()
                .map(blogMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // 5. 批量查询用户信息和点赞状态，避免 N+1
        fillBlogUserAndLikeInfo(blogs);
        // 7.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return scrollResult;
    }

    /**
     * 根据地点id查询博客
     *
     * @param poiId   地点id
     * @param current 当前页码
     * @return 博客列表
     */
    @Override
    public List<Blog> queryBlogByPoiId(Integer current, Long poiId) {
        // 深度分页防御机制
        // 如果用户恶意传入非常大的页码（比如大于 100），直接返回空列表
        if (current > 100) {
            // 返回空数组，不去给 MySQL 数据库施加压力
            return Collections.emptyList();
        }
        // 1.根据地点id查询博客
        Page<Blog> page = query()
                .eq("poi_id", poiId)
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        // 2. 批量查询用户信息和点赞状态，避免 N+1
        if (blogs != null && !blogs.isEmpty()) {
            fillBlogUserAndLikeInfo(blogs);
        }
        return blogs;
    }

    /**
     * 批量填充博客的用户信息和当前登录用户的点赞状态。
     *
     * <p>将逐条查询改为批量查询，避免 N+1 问题。</p>
     *
     * @param blogs 博客列表
     */
    private void fillBlogUserAndLikeInfo(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }

        // 1. 批量查询用户信息
        List<Long> userIds = blogs.stream()
                .map(Blog::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 2. 批量查询点赞状态（当前登录用户）
        UserDTO currentUser = UserHolder.getUser();
        Map<Long, Boolean> likeMap = new HashMap<>();
        if (currentUser != null) {
            for (Blog blog : blogs) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score("blog:liked:" + blog.getId(), currentUser.getId().toString());
                likeMap.put(blog.getId(), score != null);
            }
        }

        // 3. 填充信息
        blogs.forEach(blog -> {
            User user = userMap.get(blog.getUserId());
            if (user != null) {
                blog.setIcon(user.getIcon());
                blog.setName(user.getNickName());
            } else {
                blog.setIcon("");
                blog.setName("未知用户");
            }
            if (currentUser != null) {
                blog.setIsLike(likeMap.getOrDefault(blog.getId(), false));
            }
        });
    }

    /**
     * 检查是否有新动态（返回未读数量）
     */
    @Override
    public Boolean checkRedDot() {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        Long userId = user.getId();

        // 2. 获取该用户最后一次阅读动态的时间
        String timeStr = stringRedisTemplate.opsForValue().get("feed:read_time:" + userId);
        // 如果 Redis 里没有记录，说明这是新用户或者从来没点开过，默认时间给 0
        long lastReadTime = 0L;
        if (StrUtil.isNotBlank(timeStr)) {
            try {
                lastReadTime = Long.parseLong(timeStr);
            } catch (NumberFormatException e) {
                log.warn("feed read time format invalid: {}", timeStr);
            }
        }

        // 3. 统计 ZSet 收件箱中，时间戳大于 lastReadTime 的动态数量
        // lastReadTime + 1 相当于开区间 (lastReadTime, +∞)
        Long unreadCount = stringRedisTemplate.opsForZSet().count("feed:" + userId, lastReadTime + 1, Double.MAX_VALUE);

        // 4. 返回具体的未读数量（如果为null则返回0）
        return unreadCount != null && unreadCount > 0;
    }

    /**
     * 清除新动态红点（更新最后阅读时间）
     */
    @Override
    public void clearRedDot() {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        Long userId = user.getId();

        // 2. 将最后阅读时间更新为当前的最新时间戳
        stringRedisTemplate.opsForValue().set("feed:read_time:" + userId, String.valueOf(System.currentTimeMillis()));
    }
}
