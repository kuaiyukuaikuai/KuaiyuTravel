package com.kuaiyukuaikuai.kuaiyutravel.test.poi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.BlogService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
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

import static com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * 快鱼旅行 - 结合 Spring AI 的探店博客高逼真度生成器 (多线程+极强容错终极版)
 * 核心特性：AI生成小红书文案、复用POI真实图片、点赞同步Redis、JSON强制清洗与兜底机制
 */
@Slf4j
@SpringBootTest
public class BlogDataGeneratorTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PoiService poiService;
    @Autowired
    private BlogService blogService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入干净的基底大模型
    @Autowired
    @Qualifier("generatorChatClient")
    private ChatClient chatClient;

    // 注入全局高并发 IO 线程池
    @Autowired
    @Qualifier("aiThreadPool")
    private ExecutorService aiThreadPool;

    private static final Random RANDOM = new Random();

    /**
     * 定义大模型结构化输出的实体类
     */
    @Data
    public static class AIBlogResponse {
        @JsonProperty("title")
        private String title;
        @JsonProperty("content")
        private String content;
    }

    @Test
    public void generateFakeAIBlogsAsync() {
        // 1. 获取真实外键依赖
        List<User> users = userService.list(new LambdaQueryWrapper<User>().select(User::getId));
        // 注意：这里需要把 images 查出来，供博客复用配图
        List<Poi> pois = poiService.list(new LambdaQueryWrapper<Poi>().select(Poi::getId, Poi::getName, Poi::getTypeId, Poi::getImages));

        if (users.isEmpty() || pois.isEmpty()) {
            log.error("警告：用户表或POI表为空，无法生成数据！");
            return;
        }

        log.info("准备为库中 {} 个地点生成博客，每个地点随机 1~3 篇，已开启多线程并发请求...", pois.size());

        List<CompletableFuture<Blog>> futures = new ArrayList<>();
        // 实例化结构化输出转换器
        BeanOutputConverter<AIBlogResponse> converter = new BeanOutputConverter<>(AIBlogResponse.class);

        // 2. 遍历所有地点，派发多线程任务
        for (Poi targetPoi : pois) {
            // 每个地点随机生成 1 到 3 篇博客
            int blogCount = RANDOM.nextInt(3) + 1;

            for (int i = 0; i < blogCount; i++) {
                CompletableFuture<Blog> future = CompletableFuture.supplyAsync(() -> {
                    return generateSingleBlog(targetPoi, users, converter);
                }, aiThreadPool);

                futures.add(future);
            }
        }

        log.info("所有 AI 博客撰写任务已下发至线程池，请耐心等待...");

        // 3. 阻塞等待所有任务完成并收集结果
        List<Blog> blogList = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. 批量保存入库 & 同步 Redis 点赞数据
        if (!blogList.isEmpty()) {
            blogService.saveBatch(blogList);
            log.info("成功向 MySQL 压入 {} 篇 AI 探店博客！开始同步点赞数据至 Redis...", blogList.size());

            // 提取所有用户的 ID 集合，供点赞随机使用
            List<Long> allUserIds = users.stream().map(User::getId).collect(Collectors.toList());
            syncLikesToRedis(blogList, allUserIds);

            log.info("🎉 探店博客生成与 Redis 点赞数据同步圆满完成！");
        } else {
            log.warn("未能成功生成任何博客，请检查网络或 AI 接口限流状况。");
        }
    }

    /**
     * 原子的单篇博客生成方法 (由子线程执行)
     */
    private Blog generateSingleBlog(Poi targetPoi, List<User> users, BeanOutputConverter<AIBlogResponse> converter) {
        try {
            Blog blog = new Blog();
            // 随机绑定用户和当前地点
            blog.setUserId(users.get(RANDOM.nextInt(users.size())).getId());
            blog.setPoiId(targetPoi.getId());

            String typeName = getPoiTypeStr(targetPoi.getTypeId());

            // ================= 1. 调用 AI 生成文案 =================
            String promptString = """
                    你是一个拥有十万粉丝的旅游/探店博主。请为目的地【{poiName}】(这是一个{poiType}) 写一篇打卡笔记。
                    要求：
                    1. 标题要吸引人，适合小红书风格（带emoji）。
                    2. 正文字数在 150-250 字之间，分段清晰，语气真实自然，分享具体的游玩或用餐体验。
                    {format}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("poiName", targetPoi.getName());
            template.add("poiType", typeName);
            template.add("format", converter.getFormat()); // 注入严格的 JSON 输出指令

            String responseText = chatClient.prompt(template.create()).call().content();

            // ================= 【核心修复1：强制清洗 JSON】 =================
            // 剥离大模型可能携带的 Markdown 代码块或前后废话文字
            if (responseText != null) {
                int start = responseText.indexOf("{");
                int end = responseText.lastIndexOf("}");
                if (start != -1 && end != -1 && start <= end) {
                    responseText = responseText.substring(start, end + 1);
                }
            }

            // 尝试反序列化，如果解析失败则给一个空对象供下方兜底使用
            AIBlogResponse aiResponse = new AIBlogResponse();
            try {
                if (StringUtils.hasText(responseText)) {
                    AIBlogResponse parsed = converter.convert(responseText);
                    if (parsed != null) {
                        aiResponse = parsed;
                    }
                }
            } catch (Exception e) {
                log.warn("AI返回的内容JSON解析失败，将触发兜底机制。原文内容: {}", responseText);
            }

            blog.setTitle(aiResponse.getTitle());
            blog.setContent(aiResponse.getContent());

            // ================= 【核心修复2：数据防线与兜底】 =================
            // 防止 AI 漏写字段导致数据库报错 Field 'title' doesn't have a default value
            if (!StringUtils.hasText(blog.getTitle())) {
                blog.setTitle("打卡宝藏目的地【" + targetPoi.getName() + "】，绝绝子！✨");
                log.debug("AI漏写标题或解析失败，已触发兜底机制");
            }
            if (!StringUtils.hasText(blog.getContent())) {
                blog.setContent("今天来到了心心念念的" + targetPoi.getName() + "，环境和服务都超级棒！体验感直接拉满，价格也十分公道，强烈推荐大家周末带朋友一起来玩哦，闭眼入不踩雷！");
                log.debug("AI漏写内容或解析失败，已触发兜底机制");
            }

            // ================= 2. 复用 POI 的真实图片 =================
            blog.setImages(extractRandomImagesFromPoi(targetPoi.getImages()));

            // ================= 3. 模拟长尾流量模型 =================
            int maxLikes = Math.min(users.size(), 80);
            int randSeed = RANDOM.nextInt(100);
            // 70%的笔记是普通笔记(少量赞)，30%的笔记是爆款(较多赞)
            int likeCount = (randSeed < 70) ? RANDOM.nextInt(Math.min(10, maxLikes) + 1) : RANDOM.nextInt(maxLikes);

            blog.setLiked(likeCount);
            blog.setComments(RANDOM.nextInt(10));

            log.debug("成功生成博客 -> 地点: 【{}】", targetPoi.getName());

            // 稍作停顿，防止触发 API 速率限制 (平滑并发尖刺)
            Thread.sleep(150);

            return blog;

        } catch (Exception e) {
            log.warn("为地点【{}】生成博客时发生异常: {}", targetPoi.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 从 POI 的全量图片中随机抽取 1-4 张作为博客配图
     */
    private String extractRandomImagesFromPoi(String poiImages) {
        if (!StringUtils.hasText(poiImages)) {
            // 如果该 POI 没有图片，给一张默认的系统占位图防止前端裂开
            return "https://kuaiyutravel.oss-cn-beijing.aliyuncs.com/poi-images/default-blog.jpg";
        }

        List<String> imageList = Arrays.asList(poiImages.split(","));
        // 打乱图片顺序，让同一个地点的不同博客配图有差异
        Collections.shuffle(imageList);

        // 随机决定拿几张图 (1 ~ 4张，但不超过 POI 实际拥有的图数)
        int fetchCount = RANDOM.nextInt(Math.min(4, imageList.size())) + 1;

        return String.join(",", imageList.subList(0, fetchCount));
    }

    /**
     * 将数据库中的 liked 数量，用真实 user_id 填充到 Redis 的 ZSet 中
     */
    private void syncLikesToRedis(List<Blog> savedBlogs, List<Long> allUserIds) {
        for (Blog blog : savedBlogs) {
            int likeCount = blog.getLiked();
            if (likeCount <= 0) continue;

            // 随机挑选指定数量的真实用户来模拟“点赞者”
            Collections.shuffle(allUserIds);
            List<Long> likerIds = allUserIds.stream().limit(likeCount).collect(Collectors.toList());

            String redisKey = BLOG_LIKED_KEY + blog.getId();

            for (Long likerId : likerIds) {
                stringRedisTemplate.opsForZSet().add(redisKey, likerId.toString(), System.currentTimeMillis());
            }
        }
    }

    /**
     * 分类映射解析
     */
    private String getPoiTypeStr(Long typeId) {
        if (typeId == null) return "热门打卡地";
        return switch (typeId.intValue()) {
            case 1 -> "特色美食";
            case 2 -> "热门景点";
            case 3 -> "精选酒店";
            case 4 -> "休闲娱乐";
            default -> "宝藏目的地";
        };
    }
}