package com.kuaiyukuaikuai.kuaiyutravel.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
// 使用通配符扫描 modules 下所有子模块的 mapper 包
@MapperScan("com.kuaiyukuaikuai.kuaiyutravel.modules.*.mapper")
public class MybatisConfig {

    /**
     * MyBatis-Plus 分页拦截器。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页拦截器，并指定数据库类型为 MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自定义 SqlSessionFactory，注入分页拦截器并配置全局表前缀。
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();

        // 1. 数据源
        sessionFactory.setDataSource(dataSource);

        // 2. 实体类包名
        sessionFactory.setTypeAliasesPackage("com.kuaiyukuaikuai.kuaiyutravel.modules.my.mapper.entity");

        // 3. XML映射路径
        sessionFactory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml")
        );

        // 手动配置全局表前缀 tb_
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setTablePrefix("tb_"); // 告诉它所有表都有 tb_
        globalConfig.setDbConfig(dbConfig);
        sessionFactory.setGlobalConfig(globalConfig);

        sessionFactory.setPlugins(mybatisPlusInterceptor);

        return sessionFactory.getObject();
    }
}