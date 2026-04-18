package com.kuaiyukuaikuai.kuaiyutravel.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
@MapperScan("com.kuaiyukuaikuai.kuaiyutravel.mapper")
public class MybatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();

        // 1. 数据源
        sessionFactory.setDataSource(dataSource);

        // 2. 实体类包名
        sessionFactory.setTypeAliasesPackage("com.kuaiyukuaikuai.kuaiyutravel.entity");

        // 3. XML映射路径
        sessionFactory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml")
        );

        // 👇👇👇 核心新增：手动配置全局表前缀 tb_ 👇👇👇
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setTablePrefix("tb_"); // 告诉它所有表都有 tb_
        globalConfig.setDbConfig(dbConfig);

        sessionFactory.setGlobalConfig(globalConfig);
        // 👆👆👆 核心新增结束 👆👆👆

        return sessionFactory.getObject();
    }
}