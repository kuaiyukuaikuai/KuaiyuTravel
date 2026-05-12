package com.kuaiyukuaikuai.kuaiyutravel.test.poi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiCommentService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 快鱼旅行 - 结合 Spring AI 大模型的高逼真度评价生成器 (Bean注入解耦版)
 */
@Slf4j
@SpringBootTest
public class PoiCommentDataGeneratorTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PoiService poiService;
    @Autowired
    private PoiCommentService poiCommentService;

    // 【重构核心 1】：直接注入配置好的 ChatClient，无需再手动 Builder
    @Autowired
    @Qualifier("generatorChatClient")
    private ChatClient chatClient;

    // 【重构核心 2】：按名称注入全局共享的 AI 线程池
    @Autowired
    @Qualifier("aiThreadPool")
    private ExecutorService aiThreadPool;

    private static final Random RANDOM = new Random();

    @Test
    public void generateAIFakeCommentsAsync() {
        // 从数据库拉取真实的 用户 和 目的地 基础数据
        List<User> users = userService.list(new LambdaQueryWrapper<User>().select(User::getId));
        List<Poi> pois = poiService.list(new LambdaQueryWrapper<Poi>().select(Poi::getId, Poi::getName, Poi::getTypeId));

        if (users.isEmpty() || pois.isEmpty()) {
            log.error("缺乏基础数据，终止生成！");
            return;
        }

        int commentsPerPoi = 5;
        log.info("准备为库中 {} 个地点，每个生成 {} 条评价...", pois.size(), commentsPerPoi);

        List<CompletableFuture<PoiComment>> futures = new ArrayList<>();

        for (Poi targetPoi : pois) {
            for (int i = 0; i < commentsPerPoi; i++) {
                // 使用注入的全局线程池 aiThreadPool 提交任务
                CompletableFuture<PoiComment> future = CompletableFuture.supplyAsync(() -> {
                    return generateSingleComment(targetPoi, users);
                }, aiThreadPool);

                futures.add(future);
            }
        }

        log.info("所有 AI 任务已下发至线程池，正在飞速生成中...");

        List<PoiComment> comments = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!comments.isEmpty()) {
            poiCommentService.saveBatch(comments);
            log.info("🎉 壮举完成！成功向数据库压入了 {} 条评价！", comments.size());
        }
    }

    /**
     * 原子的单条评价生成方法
     * 注意：这里去掉了 ChatClient 传参，直接使用本类的成员变量
     */
    private PoiComment generateSingleComment(Poi targetPoi, List<User> users) {
        try {
            PoiComment comment = new PoiComment();

            comment.setUserId(users.get(RANDOM.nextInt(users.size())).getId());
            comment.setPoiId(targetPoi.getId());

            int score = RANDOM.nextInt(100) < 80 ? (RANDOM.nextInt(2) + 4) : (RANDOM.nextInt(3) + 1);
            comment.setScore(score);

            String typeName = getPoiTypeStr(targetPoi.getTypeId());

            String promptText = String.format(
                    "你现在是一名真实的用户，在旅游平台(类似大众点评或小红书)上发表评价。\n" +
                            "你刚刚去过的地点名称是：【%s】。\n" +
                            "该地点的类型是：【%s】。\n" +
                            "你给这个地点的打分是：%d 星（满分5星）。\n" +
                            "要求：\n" +
                            "1. 根据地点名称和类型，发挥想象力写一段符合该地点的真实体验评论。\n" +
                            "2. 如果评分高，就多写夸赞的话；如果评分低，就写出具体哪里不好。\n" +
                            "3. 字数控制在 40 - 80 字之间，语气自然，使用年轻人的口吻，不要有机械感。\n" +
                            "4. 直接输出评论的正文内容，绝对不要包含任何多余的解释、前言或括号备注。",
                    targetPoi.getName(), typeName, score
            );

            // 直接调用本类注入的 chatClient
            String aiGeneratedContent = chatClient.prompt(promptText).call().content();
            aiGeneratedContent = aiGeneratedContent.trim().replaceAll("^\"|\"$", "");

            comment.setContent(aiGeneratedContent);

            log.debug("成功生成评价 -> 地点: 【{}】 评分: {}", targetPoi.getName(), score);
            return comment;

        } catch (Exception e) {
            log.warn("为地点【{}】生成评价时发生网络或限流异常: {}", targetPoi.getName(), e.getMessage());
            return null;
        }
    }

    private String getPoiTypeStr(Long typeId) {
        if (typeId == null) return "未知类型打卡地";
        return switch (typeId.intValue()) {
            case 1 -> "特色美食/餐厅";
            case 2 -> "热门景点/风景名胜";
            case 3 -> "精选酒店/住宿";
            case 4 -> "休闲娱乐/按摩洗浴";
            default -> "旅游打卡地";
        };
    }
}