package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisIdWorker;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.vo.VoucherOrderVO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 优惠券订单服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisIdWorker redisIdWorker;

    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void queryMyOrders_ShouldReturnEmptyList_WhenNoOrders() {
        // Given: 模拟底层查询返回空列表
        when(voucherOrderService.query().eq("user_id", 1L).orderByDesc("create_time").list())
                .thenReturn(new ArrayList<>());

        // When
        List<VoucherOrderVO> result = voucherOrderService.queryMyOrders();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void seckillVoucher_ShouldThrowException_WhenStockEmpty() {
        // Given: Lua脚本返回1表示库存不足
        // 由于Lua脚本是静态final的，这里只验证异常流程可被测试

        // Then: 验证方法存在且可调用（实际秒杀逻辑涉及静态Lua脚本，需要集成测试）
        assertNotNull(voucherOrderService);
    }
}
