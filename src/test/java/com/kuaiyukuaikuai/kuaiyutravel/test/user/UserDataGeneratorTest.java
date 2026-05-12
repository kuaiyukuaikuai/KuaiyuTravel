package com.kuaiyukuaikuai.kuaiyutravel.test.user;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.AliOssUtil;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiCommentService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
/**
 * 快鱼旅行 - 结合 Datafaker 的高逼真度数据生成器 (支持 OSS 转存与多场景语料)
 */
@Slf4j
@SpringBootTest
public class UserDataGeneratorTest {



    @Autowired
    private UserService userService;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private AliOssUtil aliOssUtil;


    private static final Faker FAKER = new Faker(Locale.CHINA);

    // 定义 IO 密集型线程池，用于并发转存用户头像
    private final ExecutorService ioThreadPool = new ThreadPoolExecutor(
            10, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    /**
     * 场景一：生成逼真的旅行平台用户，并将头像并发转存至 OSS
     */
    @Test
    public void generateFakeUsers() {
        int targetUserCount = 50;
        List<CompletableFuture<User>> futures = new ArrayList<>();

        log.info("开始生成 {} 个旅行体验官并转存头像...", targetUserCount);

        for (int i = 0; i < targetUserCount; i++) {
            CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
                User user = new User();
                // 1. 生成真实中文姓名和手机号
                user.setNickName(FAKER.name().fullName());
                user.setPhone(FAKER.phoneNumber().cellPhone());

                // 2. 调用第三方接口生成带名字的首字母头像
                try {
                    String encodedName = URLEncoder.encode(user.getNickName(), "UTF-8");
                    String rawAvatarUrl = "https://ui-avatars.com/api/?name=" + encodedName + "&background=random";

                    // 3. 实时下载并转存至自己的阿里云 OSS
                    String ossUrl = downloadAvatarAndUploadOss(rawAvatarUrl);
                    user.setIcon(ossUrl != null ? ossUrl : ""); // 兜底处理
                } catch (Exception e) {
                    user.setIcon("");
                }
                return user;
            }, ioThreadPool);

            futures.add(future);
        }

        // 阻塞主线程，等待所有用户的头像上传并组装完毕
        List<User> fakeUsers = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 批量持久化
        userService.saveBatch(fakeUsers);
        log.info("成功生成了 {} 个逼真的旅行平台用户，头像已全部纳入 OSS 存储库！", fakeUsers.size());
    }


    /**
     * 独立的头像下载与 OSS 上传原子方法
     */
    private String downloadAvatarAndUploadOss(String rawAvatarUrl) {
        try {
            ResponseEntity<byte[]> imgResp = restTemplate.getForEntity(rawAvatarUrl, byte[].class);
            if (imgResp.getStatusCode().is2xxSuccessful() && imgResp.getBody() != null) {
                String fileName = UUID.randomUUID() + ".png"; // ui-avatars 默认返回 png
                MultipartFile multipartFile = new MockMultipartFile("file", fileName, "image/png", imgResp.getBody());
                // 转存到 OSS 的 user-avatars 目录
                return aliOssUtil.upload(multipartFile, "user-avatars");
            }
        } catch (Exception e) {
            log.warn("头像转存失败，自动跳过: {}", rawAvatarUrl);
        }
        return null;
    }


}
