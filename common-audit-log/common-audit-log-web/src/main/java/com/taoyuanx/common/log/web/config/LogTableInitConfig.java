package com.taoyuanx.common.log.web.config;

import com.taoyuanx.common.audit.log.runtime.autoconfigure.AuditLogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * db初始化配置
 *
 *
 * @author lianglei78
 * @date 2026/3/30 19:59
 */
@Configuration
@ConditionalOnProperty(prefix = "db.init", name = "enabled", havingValue = "true")
public class LogTableInitConfig {
    @Autowired
    private AuditLogProperties auditLogProperties;

    @Value("classpath:init/mysql/init.sql")
    private Resource mysqlInitScript;


    @Value("classpath:init/h2/init.sql")
    private Resource h2InitScript;


    @Value("classpath:init/sqlite/init.sql")
    private Resource sqliteInitScript;



    @Value("classpath:init/mysql/init-noLogDetail.sql")
    private Resource mysqlNoDetailInitScript;


    @Value("classpath:init/h2/init-noLogDetail.sql")
    private Resource h2NoDetailInitScript;


    @Value("classpath:init/sqlite/init-noLogDetail.sql")
    private Resource sqliteNoDetailInitScript;

    @Bean
    @Profile("mysql")
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        if(auditLogProperties.getEnableLogDetailTable()){
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(mysqlInitScript));
        }else {
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(mysqlNoDetailInitScript));
        }

        return initializer;
    }




    @Bean
    @Profile("h2")
    public DataSourceInitializer h2DataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        if(auditLogProperties.getEnableLogDetailTable()){
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(h2InitScript));
        }else {
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(h2NoDetailInitScript));
        }
        return initializer;
    }


    @Bean
    @Profile("sqlite")
    public DataSourceInitializer sqlliteDataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        if(auditLogProperties.getEnableLogDetailTable()){
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(sqliteInitScript));
        }else {
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(sqliteNoDetailInitScript));
        }
        return initializer;
    }
}
