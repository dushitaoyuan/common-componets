package com.taoyuanx.common.log.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * H2 数据库初始化配置
 * 在应用启动时自动执行 H2 建表脚本
 *
 * @author taoyuan
 * @date 2025/5/20
 */
@Configuration
@Profile("h2")
@ConditionalOnProperty(prefix = "db.init", name = "enabled", havingValue = "true")
public class H2DatabaseConfig {

    @Value("classpath:init/h2/init.sql")
    private org.springframework.core.io.Resource h2InitScript;

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(h2InitScript));
        return initializer;
    }
}