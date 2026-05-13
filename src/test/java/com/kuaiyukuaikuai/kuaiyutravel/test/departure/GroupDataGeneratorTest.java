package com.kuaiyukuaikuai.kuaiyutravel.test.departure;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 快鱼旅行 - 结合 Spring AI 的组团/找搭子高逼真度数据生成器
 * 核心特性：自动计算真实人数、防重复入团、严格主外键一致性、团号自动生成
 */
@Slf4j
@SpringBootTest
public class GroupDataGeneratorTest {

    @Autowired
    private UserService userService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    @Qualifier("generatorChatClient")
    private ChatClient chatClient;

    @Autowired
    @Qualifier("aiThreadPool")
    private ExecutorService aiThreadPool;

    private static final Random RANDOM = new Random();

    /**
     * AI 返回的单个组团结构
     */
    @Data
    public static class AIGroup {
        @JsonProperty("title")
        private String title;
        @JsonProperty("introduction")
        private String introduction;
        @JsonProperty("days")
        private Integer days;
        @JsonProperty("budget")
        private Integer budget; // 预算(分)
        @JsonProperty("max_people")
        private Integer maxPeople; // 人数上限
    }

    /**
     * AI 返回的外层包装
     */
    @Data
    public static class AIGroupResponse {
        @JsonProperty("groups")
        private List<AIGroup> groups;
    }

    /**
     * 架构师特制：跨线程传输包装类，保存预分配的用户列表
     */
    @Data
    public static class GroupTaskResult {
        private Group group;
        private List<Long> memberUserIds = new ArrayList<>(); // 包含团长和成员的ID列表
    }

    @Test
    public void generateFakeGroupsAsync() {
        // 1. 获取全局用户池，用于分配团长和成员
        List<Long> allUserIds = userService.list(new LambdaQueryWrapper<User>().select(User::getId))
                .stream().map(User::getId).collect(Collectors.toList());

        if (allUserIds.isEmpty()) {
            log.error("警告：用户表为空，无法创建组团数据！");
            return;
        }

        int taskCount = 10; // 派发 10 个线程任务，每个任务生成 5 个团，共约 50 个优质组团
        log.info("开始生成旅行找搭子数据，已开启多线程并发...");

        List<CompletableFuture<List<GroupTaskResult>>> futures = new ArrayList<>();
        BeanOutputConverter<AIGroupResponse> converter = new BeanOutputConverter<>(AIGroupResponse.class);

        // 2. 多线程派发生成任务
        for (int i = 0; i < taskCount; i++) {
            CompletableFuture<List<GroupTaskResult>> future = CompletableFuture.supplyAsync(() -> {
                return generateGroups(allUserIds, converter);
            }, aiThreadPool);
            futures.add(future);
        }

        log.info("任务已下发至 AI 领队，正在规划精彩的自驾/徒步/旅行招募贴...");

        // 收集所有组团数据
        List<GroupTaskResult> allResults = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (allResults.isEmpty()) {
            log.warn("未能生成任何组团数据。");
            return;
        }

        // ================= 3. 架构核心：两段式级联落库 =================

        // 提取所有 Group 主表实体
        List<Group> groupList = allResults.stream().map(GroupTaskResult::getGroup).collect(Collectors.toList());

        // 【第一段落库】：保存组团信息。MyBatis-Plus 的 ASSIGN_ID 会自动生成并回写 ID
        groupService.saveBatch(groupList);
        log.info("成功向 MySQL 压入 {} 个高品质组团招募帖！", groupList.size());

        // 【第二段处理】：根据生成的 Group ID，组装成员列表
        List<GroupMember> memberList = new ArrayList<>();

        for (GroupTaskResult result : allResults) {
            Group savedGroup = result.getGroup();
            List<Long> assignedUsers = result.getMemberUserIds();

            for (int i = 0; i < assignedUsers.size(); i++) {
                GroupMember member = new GroupMember();
                member.setGroupId(savedGroup.getId()); // 拿到刚刚落库生成的主键
                member.setUserId(assignedUsers.get(i));
                
                // 第 0 个固定是团长 (Role=0)，后面的都是普通成员 (Role=1)
                member.setRole(i == 0 ? 0 : 1); 
                memberList.add(member);
            }
        }

        // 【第二段落库】：保存组团成员明细
        if (!memberList.isEmpty()) {
            groupMemberService.saveBatch(memberList);
            log.info("成功向 MySQL 压入 {} 条组团成员记录（包含团长与成员）！", memberList.size());
        }

        log.info("🎉 找搭子组团模块数据生成圆满完成！");
    }

    /**
     * 子线程执行：生成 5 个组团策略，并【预分配】好车上的座位（用户ID）
     */
    private List<GroupTaskResult> generateGroups(List<Long> allUserIds, BeanOutputConverter<AIGroupResponse> converter) {
        List<GroupTaskResult> results = new ArrayList<>();
        
        // 随机一个出发省份/主题，让大模型生成的数据不单调
        String[] topics = {"川西自驾", "新疆大环线", "西藏阿里", "三亚海岛游", "长白山滑雪", "云南大理丽江", "城市周末剧本杀/露营"};
        String currentTopic = topics[RANDOM.nextInt(topics.length)];

        try {
            String promptString = """
                    你是“快鱼旅行”的高级户外领队。请策划 5 个高质量的【{topic}】相关的找搭子/组团招募贴。
                    
                    要求：
                    1. title 要吸引年轻人（如：川西7日自驾捡人、新疆北疆拼车差2女等）。
                    2. introduction 详细说明行程亮点、费用包含情况和对队友的要求（字数100字左右）。
                    3. days 游玩天数，在 1 到 15 之间。
                    4. budget 预计人均预算，单位是【分】！例如 5000元 必须输出数字 500000。
                    5. max_people 人数上限，在 4 到 15 之间。
                    {format}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("topic", currentTopic);
            template.add("format", converter.getFormat());

            String responseText = chatClient.prompt(template.create()).call().content();

            // 强制清洗 JSON
            if (responseText != null) {
                int start = responseText.indexOf("{");
                int end = responseText.lastIndexOf("}");
                if (start != -1 && end != -1 && start <= end) {
                    responseText = responseText.substring(start, end + 1);
                }
            }

            AIGroupResponse aiResponse = null;
            try {
                if (StringUtils.hasText(responseText)) {
                    aiResponse = converter.convert(responseText);
                }
            } catch (Exception e) {
                log.warn("AI返回格式解析失败，启动兜底...");
            }

            // ================= 校验与组装 =================
            if (aiResponse != null && aiResponse.getGroups() != null && !aiResponse.getGroups().isEmpty()) {
                for (AIGroup ag : aiResponse.getGroups()) {
                    results.add(buildGroupTaskResult(ag, allUserIds));
                }
            } else {
                throw new RuntimeException("AI生成的组团数量为空");
            }
            
            Thread.sleep(150); // 防限流
            
        } catch (Exception e) {
            log.warn("生成组团任务失败: {}。触发架构师代码级兜底！", e.getMessage());
            results.add(buildGroupTaskResult(buildFallbackAIGroup(currentTopic), allUserIds));
        }

        return results;
    }

    /**
     * 架构师核心算法：将 AI 生成的数据转为实体，并为其防抖动、防重复地预分配真实用户
     */
    private GroupTaskResult buildGroupTaskResult(AIGroup ag, List<Long> allUserIds) {
        GroupTaskResult result = new GroupTaskResult();
        Group g = new Group();
        
        // 1. 基础属性注入
        g.setTitle(ag.getTitle());
        g.setIntroduction(ag.getIntroduction());
        g.setDays(ag.getDays() != null ? ag.getDays() : 5);
        g.setBudget(ag.getBudget() != null ? ag.getBudget() : 300000);
        
        // 人数上限防范逻辑
        int maxPeople = ag.getMaxPeople() != null ? ag.getMaxPeople() : 6;
        g.setMaxPeople(maxPeople);
        
        // 生成预计出发时间：未来 3 到 30 天内
        g.setStartTime(LocalDateTime.now().plusDays(RANDOM.nextInt(28) + 3));
        
        // 生成团队编号 KY-yyyyMMdd-XXXX
        String dateStr = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        String randomStr = RandomUtil.randomString(4).toUpperCase();
        g.setGroupNo("KY-" + dateStr + "-" + randomStr);

        // ================= 2. 模拟真实拼团人数（核心） =================
        // 打乱用户池，保证每次抽取的用户都是随机的
        List<Long> shuffledUsers = new ArrayList<>(allUserIds);
        Collections.shuffle(shuffledUsers);
        
        // 团长：抽取第 1 个人
        Long leaderId = shuffledUsers.get(0);
        g.setLeaderId(leaderId);
        result.getMemberUserIds().add(leaderId); // 索引0存团长
        
        // 普通成员：随机决定当前已经有几个人上车了 (0 到 maxPeople - 1)
        // 必须确保不要超过系统里的实际用户总数
        int maxExtra = Math.min(maxPeople - 1, shuffledUsers.size() - 1); 
        int extraMembersCount = RANDOM.nextInt(maxExtra + 1);
        
        for (int i = 1; i <= extraMembersCount; i++) {
            result.getMemberUserIds().add(shuffledUsers.get(i)); // 存普通成员
        }
        
        // 当前总人数 = 团长(1) + 普通成员
        g.setCurrentPeople(1 + extraMembersCount);
        
        // 根据人数设定状态：如果满了就变成 "1-已成团"
        g.setStatus(g.getCurrentPeople().equals(g.getMaxPeople()) ? 1 : 0);

        result.setGroup(g);
        return result;
    }

    /**
     * 架构师级兜底：动态生成补位数据
     */
    private AIGroup buildFallbackAIGroup(String topic) {
        AIGroup ag = new AIGroup();
        ag.setTitle("【" + topic + "】周末纯玩寻搭子，有车缺人");
        ag.setIntroduction("费用AA，主要为了拍照打卡，性格随和的来。");
        ag.setDays(3);
        ag.setBudget(150000); // 1500元
        ag.setMaxPeople(5);
        return ag;
    }
}