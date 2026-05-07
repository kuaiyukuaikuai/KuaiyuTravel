package com.kuaiyukuaikuai.kuaiyutravel;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 组团模块-测试数据生成器
 */
@SpringBootTest(classes = KuaiyuTravelApplication.class) // 注意替换为你的启动类名
public class GroupDataGeneratorTest {

    @Resource
    private GroupService groupService;
    @Resource
    private GroupMemberService groupMemberService;

    @Test
    public void generateGroupData() {
        // 准备一个 List 来装载数据，用于最后批量插入
        List<Group> groupList = new ArrayList<>();

        // 模拟一些真实的标题前缀
        String[] titlePrefixes = {"五一", "国庆", "周末", "年假", "说走就走"};
        String[] destinations = {"川西", "新疆", "西藏", "大理", "三亚", "长白山"};
        String[] themes = {"自驾捡人", "徒步搭子", "吃货组团", "摄影轻奢", "纯玩无购物"};

        System.out.println("开始生成测试数据...");

        // 循环生成 50 条数据
        for (int i = 0; i < 50; i++) {
            Group group = new Group();

            // 1. 随机拼接真实的标题 (例如: "五一川西自驾捡人")
            String title = RandomUtil.randomEle(titlePrefixes) + 
                           RandomUtil.randomEle(destinations) + 
                           RandomUtil.randomEle(themes);
            group.setTitle(title);

            // 2. 生成唯一的组团编号
            String dateStr = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
            String randomStr = RandomUtil.randomString(4).toUpperCase();
            group.setGroupNo("KY-" + dateStr + "-" + randomStr + i); // 加上 i 防止同一毫秒内随机串重复

            // 3. 随机业务数据
            group.setIntroduction("这是一条由系统自动生成的测试组团信息。目的地：" + RandomUtil.randomEle(destinations));
            group.setLeaderId(1L); // 写死一个存在的用户ID作为团长
            
            // 人数上限 4~10 人之间随机
            group.setMaxPeople(RandomUtil.randomInt(4, 11)); 
            group.setCurrentPeople(1); // 初始只有团长1人
            
            // 出发时间：未来 1~30 天内随机
            int randomDaysToAdd = RandomUtil.randomInt(1, 31);
            group.setStartTime(LocalDateTime.now().plusDays(randomDaysToAdd));
            
            // 游玩天数 2~15天，预算 1000~10000元 (注意乘以100转为分)
            group.setDays(RandomUtil.randomInt(2, 16));
            group.setBudget(RandomUtil.randomInt(1000, 10000) * 100);
            
            // 状态：0-招募中
            group.setStatus(0);

            // 加入到集合中
            groupList.add(group);
        }

        // 4. 【核心】调用 MyBatis-Plus 的批量保存方法，极其高效
        // 第二个参数 100 表示每 100 条 flush 一次到数据库，防止内存溢出
        boolean success = groupService.saveBatch(groupList, 100);
        System.out.println("第一阶段：50个组团基本信息已入库。");

        List<GroupMember> memberList = new ArrayList<>();
        for (Group group : groupList) {
            GroupMember member = new GroupMember();
            member.setGroupId(group.getId()); // 获取刚刚生成的团ID
            member.setUserId(group.getLeaderId()); // 关联团长ID
            member.setRole(0); // 0-团长角色
            member.setJoinTime(LocalDateTime.now());
            memberList.add(member);
        }

        // 4. 第四阶段：批量保存成员信息
        groupMemberService.saveBatch(memberList);
        System.out.println("第二阶段：50个团长的成员关联关系已建立！");
    }
}