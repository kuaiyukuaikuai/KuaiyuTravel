package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.tools.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.VoucherMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.List;

/**
 * AI 旅游工具类，提供优惠券查询、组团查询等功能。
 *
 * <p>通过 Spring AI 的 @Tool 注解将方法暴露给大模型调用，
 * 实现景点优惠券检索和招募中组团信息查询。</p>
 */
@Slf4j
@Component
public class TravelAiTools {

    @Resource
    private PoiMapper poiMapper;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private GroupMapper groupMapper;

    /**
     * 查询指定景点的实时优惠券信息。
     *
     * <p>根据景点名称模糊匹配 POI，再查询关联的优惠券列表。
     * 返回格式化的卡片标记字符串，供前端渲染。</p>
     *
     * @param poiName 景点名称
     * @return 优惠券信息或查询结果提示
     */
    @Tool(description = "当用户询问某个具体景点/酒店，或者询问是否有优惠时，调用此工具查询实时优惠券。入参必须是景点名称(poiName)")
    public String checkCouponsTool(String poiName) {
        log.info("AI 查询优惠券库存，景点：{}", poiName);

        // 根据景点名称模糊匹配 POI
        LambdaQueryWrapper<Poi> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Poi::getName, poiName).last("LIMIT 1");
        Poi poi = poiMapper.selectOne(wrapper);

        if (poi == null) {
            return "未找到该景点的精确信息，无法查询优惠券。";
        }

        List<Voucher> vouchers = voucherMapper.queryVoucherOfPoi(poi.getId());

        if (vouchers != null && !vouchers.isEmpty()) {
            Voucher voucher = vouchers.get(0);

            // payValue 单位为分，转换为元
            long payPrice = voucher.getPayValue() != null ? voucher.getPayValue() / 100 : 0;

            return String.format("已找到真实的优惠券。请将此标记原样输出给用户：[COUPON_CARD|%d|%s|%d|%s]",
                    voucher.getId(), voucher.getTitle(), payPrice, poi.getName());
        } else {
            return "很抱歉，" + poiName + " 目前没有可用的优惠券，或已经被抢空了。";
        }
    }

    /**
     * 查询招募中的旅游组团信息。
     *
     * <p>仅查询状态为 0（招募中）的组团，按创建时间倒序排列，最多返回 3 条。
     * 返回格式化的卡片标记字符串，供前端渲染。</p>
     *
     * @param keyword 目的地或关键词，无明确目的地时传空字符串
     * @return 组团信息列表或空结果提示
     */
    @Tool(description = "当用户寻找旅游组团、拼车、捡人，或者问有没有团可以加入时调用此工具。入参 keyword 为目的地或关键词（如果没有明确目的地请传空字符串）。")
    public String searchGroupsTool(String keyword) {
        log.info("AI 查询拼团信息，关键词：{}", keyword);

        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        // 仅推荐状态为 0（招募中）的团
        wrapper.eq(Group::getStatus, 0);

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Group::getTitle, keyword).or().like(Group::getIntroduction, keyword));
        }

        // 限制返回数量，避免上下文过长
        wrapper.orderByDesc(Group::getCreateTime).last("LIMIT 3");

        List<Group> groups = groupMapper.selectList(wrapper);

        if (groups != null && !groups.isEmpty()) {
            StringBuilder sb = new StringBuilder("找到了以下招募中的组团，请向用户热情推荐并原样输出对应的卡片标记：\n");
            for (Group g : groups) {
                // 预算单位由分转换为元
                long budgetYuan = g.getBudget() != null ? g.getBudget() / 100 : 0;

                sb.append(String.format("标题：%s，目前人数：%d/%d，预算：%d元。请输出标记：[GROUP_CARD|%s|%s|%d/%d|%d]\n",
                        g.getTitle(), g.getCurrentPeople(), g.getMaxPeople(), budgetYuan,
                        g.getGroupNo(), g.getTitle(), g.getCurrentPeople(), g.getMaxPeople(), budgetYuan));
            }
            sb.append("\n【重要指令】：在推荐完上述卡片后，请附上一句话提示用户：如果没有找到合适的，您也可以直接点击进入组团大厅，自己发起一个组团当团长哦！");
            return sb.toString();
        } else {
            return "很抱歉，目前没有找到与 '" + keyword + "' 相关的招募中的组团。请强烈鼓励用户：\"目前暂时没有去这个地方的拼团，不如您自己发起一个组团当团长吧！去组团大厅点击【创建组团】即可一键发布，很快就会有小伙伴加入您的！\"";
        }
    }
}
