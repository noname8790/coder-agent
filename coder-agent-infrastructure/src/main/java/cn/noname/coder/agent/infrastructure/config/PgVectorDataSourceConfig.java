package cn.noname.coder.agent.infrastructure.config;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "coder-agent.pgvector", name = "enabled", havingValue = "true")
public class PgVectorDataSourceConfig {

    private final AgentRuntimeProperties properties;

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        AgentRuntimeProperties.Pgvector pgvector = properties.getPgvector();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pgvector.getUrl());
        config.setUsername(pgvector.getUsername());
        config.setPassword(pgvector.getPassword());
        config.setPoolName("coder-agent-pgvector");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
