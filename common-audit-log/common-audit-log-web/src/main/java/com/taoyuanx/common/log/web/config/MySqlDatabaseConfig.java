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
 * MySQL 数据库初始化配置
 * 在应用启动时自动执行 MySQL 建表脚本
 *
 * @author taoyuan
 * @date 2025/5/20
 */
@Configuration
@Profile("mysql")
@ConditionalOnProperty(prefix = "db.init", name = "enabled", havingValue = "true")
public class MySqlDatabaseConfig {

    @Value("classpath:init/mysql/init.sql")
    private org.springframework.core.io.Resource mysqlInitScript;

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(mysqlInitScript));
        return initializer;
    }
}