package com.kuaiyukuaikuai.kuaiyutravel.test.poi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.BlogComments;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogCommentsService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 快鱼旅行 - 结合 Spring AI 的博客多级评论高逼真度生成器
 * 核心特性：多线程生成、严格对齐博客 comment 数量、真实层级关系绑定、Redis Set 同步
 */
@Slf4j
@SpringBootTest
public class BlogCommentsDataGeneratorTest {

    @Autowired
    private UserService userService;
    @Autowired
    private BlogService blogService;
    @Autowired
    private BlogCommentsService blogCommentsService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("generatorChatClient")
    private ChatClient chatClient;

    @Autowired
    @Qualifier("aiThreadPool")
    private ExecutorService aiThreadPool;

    private static final Random RANDOM = new Random();
    private static final String COMMENT_LIKED_KEY = "blog:comment:liked:";

    /**
     * AI 返回的结构化 JSON 模型
     */
    @Data
    public static class AICommentResponse {
        @JsonProperty("comments")
        private List<String> comments;
    }

    /**
     * 架构师特制：用于跨线程传输分层数据的包装类
     */
    @Data
    public static class CommentTaskResult {
        private List<BlogComments> level1Comments = new ArrayList<>();
        private List<BlogComments> level2Comments = new ArrayList<>();
    }

    @Test
    public void generateFakeCommentsAsync() {
        // ================= 1. 获取基础外键依赖 =================
        List<Long> userIds = userService.list(new LambdaQueryWrapper<User>().select(User::getId))
                .stream().map(User::getId).collect(Collectors.toList());

        // 查询出所有 comments 数量大于 0 的博客（需要提供标题和内容给AI做参考）
        List<Blog> blogs = blogService.list(new LambdaQueryWrapper<Blog>()
                .select(Blog::getId, Blog::getTitle, Blog::getContent, Blog::getComments)
                .gt(Blog::getComments, 0));

        if (userIds.isEmpty() || blogs.isEmpty()) {
            log.error("警告：用户表为空，或没有任何有评论数量的探店博客，终止生成！");
            return;
        }

        log.info("发现 {} 篇需要生成评论的博客，已开启多线程 AI 派发...", blogs.size());

        List<CompletableFuture<CommentTaskResult>> futures = new ArrayList<>();
        BeanOutputConverter<AICommentResponse> converter = new BeanOutputConverter<>(AICommentResponse.class);

        // ================= 2. 多线程生成无主键实体 =================
        for (Blog targetBlog : blogs) {
            CompletableFuture<CommentTaskResult> future = CompletableFuture.supplyAsync(() -> {
                return generateCommentsForBlog(targetBlog, userIds, converter);
            }, aiThreadPool);
            futures.add(future);
        }

        log.info("AI 评论水军已出发，正在疯狂打字中，请耐心等待...");

        // 收集所有线程的结果
        List<CommentTaskResult> allResults = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<BlogComments> globalL1Comments = new ArrayList<>();
        List<BlogComments> globalL2Comments = new ArrayList<>();

        for (CommentTaskResult res : allResults) {
            globalL1Comments.addAll(res.getLevel1Comments());
            globalL2Comments.addAll(res.getLevel2Comments());
        }

        // ================= 3. 架构核心：分层落库与外键绑定 =================
        if (!globalL1Comments.isEmpty()) {
            // 第一步：先保存一级评论，获取真实的自增 ID
            blogCommentsService.saveBatch(globalL1Comments);
            log.info("成功向 MySQL 压入 {} 条【一级评论】", globalL1Comments.size());

            // 将生成好的一级评论按 blog_id 分组，供二级评论寻找父亲
            Map<Long, List<BlogComments>> l1Map = globalL1Comments.stream()
                    .collect(Collectors.groupingBy(BlogComments::getBlogId));

            // 第二步：给二级评论分配 parent_id
            for (BlogComments l2 : globalL2Comments) {
                List<BlogComments> parentCandidates = l1Map.get(l2.getBlogId());
                if (parentCandidates != null && !parentCandidates.isEmpty()) {
                    // 随机认一个本博客的一级评论当爸爸
                    Long parentId = parentCandidates.get(RANDOM.nextInt(parentCandidates.size())).getId();
                    l2.setParentId(parentId);
                    l2.setAnswerId(parentId);
                } else {
                    // 兜底：如果找不到爸爸，就自己当一级评论
                    l2.setParentId(0L);
                    l2.setAnswerId(0L);
                }
            }

            // 第三步：保存二级评论
            if (!globalL2Comments.isEmpty()) {
                blogCommentsService.saveBatch(globalL2Comments);
                log.info("成功向 MySQL 压入 {} 条【二级回复】", globalL2Comments.size());
            }

            // ================= 4. 合并所有评论，同步点赞到 Redis Set =================
            List<BlogComments> allSavedComments = new ArrayList<>();
            allSavedComments.addAll(globalL1Comments);
            allSavedComments.addAll(globalL2Comments);

            syncLikesToRedisSet(allSavedComments, userIds);
            log.info("🎉 探店博客多级评论生成与 Redis Set 同步圆满完成！");
        }
    }

    /**
     * 子线程执行：为一篇博客生成它所需的全部评论
     */
    private CommentTaskResult generateCommentsForBlog(Blog blog, List<Long> userIds, BeanOutputConverter<AICommentResponse> converter) {
        CommentTaskResult result = new CommentTaskResult();
        int targetCount = blog.getComments(); // 严格对齐博客表记录的数量

        try {
            String shortContent = blog.getContent() != null && blog.getContent().length() > 50
                    ? blog.getContent().substring(0, 50) : blog.getContent();

            String promptString = """
                    你是一个爱刷旅游社区的真实网友。
                    目前有一篇笔记，标题：【{title}】 内容摘要：【{content}】。
                    请模仿真实网友，为这篇笔记写出 {count} 条评论。
                    要求：
                    1. 评论有的可以是夸赞，有的是求攻略，有的是网友间互相附和。
                    2. 字数在 10 到 30 字之间，语气自然，不要带引号。
                    {format}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("title", blog.getTitle());
            template.add("content", shortContent);
            template.add("count", targetCount);
            template.add("format", converter.getFormat());

            String responseText = chatClient.prompt(template.create()).call().content();

            // ================= 强制清洗 JSON =================
            if (responseText != null) {
                int start = responseText.indexOf("{");
                int end = responseText.lastIndexOf("}");
                if (start != -1 && end != -1 && start <= end) {
                    responseText = responseText.substring(start, end + 1);
                }
            }

            AICommentResponse aiResponse = null;
            try {
                if (StringUtils.hasText(responseText)) {
                    aiResponse = converter.convert(responseText);
                }
            } catch (Exception e) {
                log.warn("AI评论解析失败，启用兜底。");
            }

            List<String> commentStrings = new ArrayList<>();
            if (aiResponse != null && aiResponse.getComments() != null) {
                commentStrings.addAll(aiResponse.getComments());
            }

            // ================= 强制对齐数量防线 =================
            // 如果 AI 生成的少了，用兜底语料补齐；多了则截断
            String[] fallbacks = {"这地方绝了！", "求问楼主多少钱呀？", "环境看着真不错", "马上去打卡~", "感谢分享，收藏了"};
            while (commentStrings.size() < targetCount) {
                commentStrings.add(fallbacks[RANDOM.nextInt(fallbacks.length)]);
            }
            if (commentStrings.size() > targetCount) {
                commentStrings = commentStrings.subList(0, targetCount);
            }
            Collections.shuffle(commentStrings); // 打乱顺序，更自然

            // ================= 分配一级/二级评论 =================
            // 策略：至少 1 个一级评论，其余 40% 概率是二级回复
            int l1Count = Math.max(1, (int) (targetCount * 0.6));

            for (int i = 0; i < targetCount; i++) {
                BlogComments comment = new BlogComments();
                comment.setUserId(userIds.get(RANDOM.nextInt(userIds.size())));
                comment.setBlogId(blog.getId());
                comment.setContent(commentStrings.get(i));
                comment.setLiked(generateRandomLikes(userIds.size()));
                comment.setStatus(0);

                if (i < l1Count) {
                    // 标记为一级评论 (暂无 DB ID)
                    comment.setParentId(0L);
                    comment.setAnswerId(0L);
                    result.getLevel1Comments().add(comment);
                } else {
                    // 标记为二级评论 (父ID留空，交由主线程分配)
                    result.getLevel2Comments().add(comment);
                }
            }

            Thread.sleep(150); // 平滑并发防止限流
            return result;

        } catch (Exception e) {
            log.warn("为博客【{}】生成评论失败: {}", blog.getTitle(), e.getMessage());
            return result; // 返回空集合，防止整个流崩溃
        }
    }

    private int generateRandomLikes(int maxUsers) {
        int max = Math.min(maxUsers, 60);
        int seed = RANDOM.nextInt(100);
        if (seed < 80) return RANDOM.nextInt(5);
        if (seed < 95) return 5 + RANDOM.nextInt(15);
        return 20 + RANDOM.nextInt(max - 20 + 1);
    }

    private void syncLikesToRedisSet(List<BlogComments> savedComments, List<Long> allUserIds) {
        for (BlogComments comment : savedComments) {
            int likeCount = comment.getLiked();
            if (likeCount <= 0) continue;

            String redisKey = COMMENT_LIKED_KEY + comment.getId();
            Collections.shuffle(allUserIds);
            List<Long> likerIds = allUserIds.stream().limit(likeCount).collect(Collectors.toList());

            // 存入 Redis Set
            String[] likerIdStrings = likerIds.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(redisKey, likerIdStrings);
        }
    }
}