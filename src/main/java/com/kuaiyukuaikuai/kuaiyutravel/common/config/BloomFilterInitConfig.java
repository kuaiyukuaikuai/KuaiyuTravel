package com.kuaiyukuaikuai.kuaiyutravel.common.config;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
public class BloomFilterInitConfig implements CommandLineRunner {

    @Resource
    private RedissonClient redissonClient;

    // 假设这是你查询景点的 Mapper
    @Resource
    private PoiMapper poiMapper;

    @Override
    public void run(String... args) throws Exception {
        // 1. 获取布隆过滤器对象，名字叫 "poi:bloom-filter"
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("poi:bloom-filter");
        
        // 2. 初始化过滤器：预计插入 10万 条数据，允许的误判率为 3% (0.03)
        bloomFilter.tryInit(100000L, 0.03);

        // 3. 查询数据库中所有合法的 POI ID (注意：真实企业中不要 select *，只 select id)
        List<Long> poiIdList = poiMapper.selectIdList();

        // 4. 将所有 ID 放入布隆过滤器
        for (Long id : poiIdList) {
            bloomFilter.add(id);
        }

        log.info("布隆过滤器预热完成，共加载 POI 数量：{}", poiIdList.size());
    }
}