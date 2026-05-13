package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.VoucherMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.List;

@Component
public class TravelAiTools {

    @Resource
    private PoiMapper poiMapper;

    // 🚀 核心修复：直接注入底层的 Mapper
    @Resource
    private VoucherMapper voucherMapper;

    // 🚀 1. 新增：注入底层的 GroupMapper
    @Resource
    private GroupMapper groupMapper;


    // ==========================================
    // 🚀 核心改造：真实数据库查券工具
    // ==========================================
    @Tool(description = "当用户询问某个具体景点/酒店，或者询问是否有优惠时，调用此工具查询实时优惠券。入参必须是景点名称(poiName)")
    public String checkCouponsTool(String poiName) {
        System.out.println("🤖 AI 正在查询真实数据库的优惠券库存 -> 景点：" + poiName);

        // 1. 根据大模型传过来的景点名称，去数据库模糊匹配，查出真实的 poi_id
        LambdaQueryWrapper<Poi> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Poi::getName, poiName).last("LIMIT 1");
        Poi poi = poiMapper.selectOne(wrapper);

        if (poi == null) {
            return "未找到该景点的精确信息，无法查询优惠券。";
        }

        // 2. 修复：直接通过 voucherMapper 调用自定义方法，完美拿到 List
        List<Voucher> vouchers = voucherMapper.queryVoucherOfPoi(poi.getId());

        if (vouchers != null && !vouchers.isEmpty()) {
            // 3. 取出第一张可用的优惠券（这里只做演示，你也可以遍历组装多个）
            Voucher voucher = vouchers.get(0);

            // 4. 将真实的券ID、标题、金额塞入特定格式的字符串中
            // 假设 payValue 是分，这里除以 100 换算成元（根据你的实际业务逻辑调整）
            long payPrice = voucher.getPayValue() != null ? voucher.getPayValue() / 100 : 0;

            return String.format("已找到真实的优惠券。请将此标记原样输出给用户：[COUPON_CARD|%d|%s|%d|%s]",
                    voucher.getId(), voucher.getTitle(), payPrice, poi.getName());
        } else {
            return "很抱歉，" + poiName + " 目前没有可用的优惠券，或已经被抢空了。";
        }
    }



    // ==========================================
    // 🚀 新增：招募中组团查询工具
    // ==========================================
    @Tool(description = "当用户寻找旅游组团、拼车、捡人，或者问有没有团可以加入时调用此工具。入参 keyword 为目的地或关键词（如果没有明确目的地请传空字符串）。")
    public String searchGroupsTool(String keyword) {
        System.out.println("🤖 AI 正在查询拼团信息 -> 关键词：" + keyword);

        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        // 核心业务逻辑：只能推荐状态为 0（招募中）的团
        wrapper.eq(Group::getStatus, 0);

        // 如果用户说了具体地点（如“川西”），则进行模糊匹配
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Group::getTitle, keyword).or().like(Group::getIntroduction, keyword));
        }

        // 按最新发布的排在前面，最多推荐 3 个，避免撑爆大模型上下文
        wrapper.orderByDesc(Group::getCreateTime).last("LIMIT 3");

        List<Group> groups = groupMapper.selectList(wrapper);

        if (groups != null && !groups.isEmpty()) {
            StringBuilder sb = new StringBuilder("找到了以下招募中的组团，请向用户热情推荐并原样输出对应的卡片标记：\n");
            for (Group g : groups) {
                // 数据库里存的是分，这里换算成元
                long budgetYuan = g.getBudget() != null ? g.getBudget() / 100 : 0;

                // 告诉大模型具体信息，并要求它吐出特定的前端渲染暗号
                sb.append(String.format("标题：%s，目前人数：%d/%d，预算：%d元。请输出标记：[GROUP_CARD|%s|%s|%d/%d|%d]\n",
                        g.getTitle(), g.getCurrentPeople(), g.getMaxPeople(), budgetYuan,
                        g.getGroupNo(), g.getTitle(), g.getCurrentPeople(), g.getMaxPeople(), budgetYuan));
            }
            sb.append("\n【重要指令】：在推荐完上述卡片后，请附上一句话提示用户：如果没有找到合适的，您也可以直接点击进入组团大厅，自己发起一个组团当团长哦！");
            return sb.toString();
        } else {
            // 如果没找到，反向引导用户自己建团
            return "很抱歉，目前没有找到与 '" + keyword + "' 相关的招募中的组团。请强烈鼓励用户：“目前暂时没有去这个地方的拼团，不如您自己发起一个组团当团长吧！去组团大厅点击【创建组团】即可一键发布，很快就会有小伙伴加入您的！”";
        }
    }
}