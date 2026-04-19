package com.kuaiyukuaikuai.kuaiyutravel;

import com.kuaiyukuaikuai.kuaiyutravel.dto.LoginFormDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants.LOGIN_USER_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户登出功能测试
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LogoutTest {

    private MockMvc mockMvc;

    @Resource
    private WebApplicationContext webApplicationContext;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String testToken;
    private final String testPhone = "13800138000";
    private final String testCode = "123456";

    @BeforeEach
    void setUp() {
        // 初始化 MockMvc
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 清理测试数据
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        cleanupTestData();
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        // 删除测试验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + testPhone);
        // 如果有token，也删除
        if (testToken != null) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + testToken);
        }
    }

    /**
     * 准备测试环境：先登录获取token
     */
    private void prepareLogin() throws Exception {
        // 1. 先在Redis中设置验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + testPhone, testCode, 2, TimeUnit.MINUTES);

        // 2. 构造登录请求
        LoginFormDTO loginForm = new LoginFormDTO();
        loginForm.setPhone(testPhone);
        loginForm.setCode(testCode);

        // 3. 执行登录
        Result result = userService.login(loginForm, null);
        
        // 4. 验证登录成功并获取token
        assertNotNull(result, "登录返回结果不能为空");
        assertTrue(result.getSuccess(), "登录应该成功");
        
        testToken = (String) result.getData();
        assertNotNull(testToken, "登录应该返回token");
        System.out.println("登录成功，获取到token: " + testToken);

        // 5. 验证token已存入Redis
        String tokenKey = LOGIN_USER_KEY + testToken;
        Boolean exists = stringRedisTemplate.hasKey(tokenKey);
        assertTrue(Boolean.TRUE.equals(exists), "token应该存在于Redis中");
    }

    /**
     * 测试1：正常登出流程
     */
    @Test
    @Order(1)
    @DisplayName("测试正常登出流程")
    void testNormalLogout() throws Exception {
        // 1. 先登录
        prepareLogin();

        // 2. 验证token在Redis中存在
        String tokenKey = LOGIN_USER_KEY + testToken;
        Boolean existsBefore = stringRedisTemplate.hasKey(tokenKey);
        assertTrue(Boolean.TRUE.equals(existsBefore), "登出前token应该存在于Redis中");

        // 3. 执行登出请求
        MvcResult mvcResult = mockMvc.perform(post("/user/logout")
                        .header("authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // 4. 验证响应
        String responseContent = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("登出响应: " + responseContent);
        
        Result result = objectMapper.readValue(responseContent, Result.class);
        assertTrue(result.getSuccess(), "登出应该成功");

        // 5. 验证token已从Redis中删除
        Boolean existsAfter = stringRedisTemplate.hasKey(tokenKey);
        assertFalse(Boolean.TRUE.equals(existsAfter), "登出后token应该从Redis中删除");

        System.out.println("✓ 正常登出测试通过");
    }

    /**
     * 测试2：未携带token登出
     */
    @Test
    @Order(2)
    @DisplayName("测试未携带token登出")
    void testLogoutWithoutToken() throws Exception {
        // 执行登出请求，不携带token（会被拦截器拦截，返回401）
        MvcResult mvcResult = mockMvc.perform(post("/user/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())  // 401
                .andDo(print())
                .andReturn();

        System.out.println("✓ 未携带token登出测试通过（被拦截器拦截）");
    }

    /**
     * 测试3：携带空token登出
     */
    @Test
    @Order(3)
    @DisplayName("测试携带空token登出")
    void testLogoutWithEmptyToken() throws Exception {
        // 执行登出请求，携带空token（会被拦截器拦截，返回401）
        MvcResult mvcResult = mockMvc.perform(post("/user/logout")
                        .header("authorization", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())  // 401
                .andDo(print())
                .andReturn();

        System.out.println("✓ 携带空token登出测试通过（被拦截器拦截）");
    }

    /**
     * 测试4：使用无效token登出
     */
    @Test
    @Order(4)
    @DisplayName("测试使用无效token登出")
    void testLogoutWithInvalidToken() throws Exception {
        String invalidToken = "invalid-token-12345";

        // 执行登出请求，使用无效token（会被拦截器拦截，返回401）
        MvcResult mvcResult = mockMvc.perform(post("/user/logout")
                        .header("authorization", invalidToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())  // 401
                .andDo(print())
                .andReturn();

        System.out.println("✓ 使用无效token登出测试通过（被拦截器拦截）");
    }

    /**
     * 测试5：重复登出（幂等性测试）
     */
    @Test
    @Order(5)
    @DisplayName("测试重复登出")
    void testRepeatedLogout() throws Exception {
        // 1. 先登录
        prepareLogin();

        // 2. 第一次登出
        Result result1 = userService.logout(testToken);
        assertTrue(result1.getSuccess(), "第一次登出应该成功");

        // 3. 第二次登出（同一token）
        Result result2 = userService.logout(testToken);
        assertTrue(result2.getSuccess(), "第二次登出也应该成功（幂等性）");

        // 4. 验证token确实已被删除
        String tokenKey = LOGIN_USER_KEY + testToken;
        Boolean exists = stringRedisTemplate.hasKey(tokenKey);
        assertFalse(Boolean.TRUE.equals(exists), "token应该不存在于Redis中");

        System.out.println("✓ 重复登出测试通过");
    }

    /**
     * 测试6：直接调用Service层登出方法
     */
    @Test
    @Order(6)
    @DisplayName("测试Service层登出方法")
    void testServiceLayerLogout() throws Exception {
        // 1. 先登录
        prepareLogin();

        // 2. 验证token存在
        String tokenKey = LOGIN_USER_KEY + testToken;
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey)), "登出前token应该存在");

        // 3. 直接调用service层登出方法
        Result result = userService.logout(testToken);
        
        // 4. 验证返回结果
        assertNotNull(result, "返回结果不能为空");
        assertTrue(result.getSuccess(), "登出应该成功");

        // 5. 验证token已被删除
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey)), "登出后token应该被删除");

        System.out.println("✓ Service层登出方法测试通过");
    }

    // 用于JSON序列化的ObjectMapper（简化版，实际项目中应注入）
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
        new com.fasterxml.jackson.databind.ObjectMapper();
}
