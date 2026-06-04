package com.liu.eemrsagent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Configuration
public class AgentDataSourceConfig {

    private final Environment environment;
    private final String externalConfigPath;

    public AgentDataSourceConfig(
            Environment environment,
            @Value("${agent.datasource.external-config:../eemrs-server-master/src/main/resources/application.yml}") String externalConfigPath
    ) {
        this.environment = environment;
        this.externalConfigPath = externalConfigPath;
    }

    @Bean
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        DbSettings dbSettings = resolveDbSettings(dataSourceProperties);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(dbSettings.url());
        dataSource.setUsername(dbSettings.username());
        dataSource.setPassword(dbSettings.password());
        dataSource.setDriverClassName(dbSettings.driverClassName());
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(10000);
        return dataSource;
    }

    private DbSettings resolveDbSettings(DataSourceProperties dataSourceProperties) {
        String agentUrl = environment.getProperty("AGENT_DB_URL");
        String agentUsername = environment.getProperty("AGENT_DB_USERNAME");
        String agentPassword = environment.getProperty("AGENT_DB_PASSWORD");
        if (hasText(agentUrl) || hasText(agentUsername) || hasText(agentPassword)) {
            return new DbSettings(
                    firstText(agentUrl, dataSourceProperties.getUrl()),
                    firstText(agentUsername, dataSourceProperties.getUsername(), "root"),
                    firstText(agentPassword, dataSourceProperties.getPassword(), ""),
                    firstText(dataSourceProperties.getDriverClassName(), "com.mysql.cj.jdbc.Driver")
            );
        }

        Properties external = loadExternalEemrsProperties();
        return new DbSettings(
                firstText(
                        external.getProperty("spring.datasource.url"),
                        dataSourceProperties.getUrl(),
                        "jdbc:mysql://localhost:3306/eemrs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                ),
                firstText(external.getProperty("spring.datasource.username"), dataSourceProperties.getUsername(), "root"),
                firstText(external.getProperty("spring.datasource.password"), dataSourceProperties.getPassword(), ""),
                firstText(external.getProperty("spring.datasource.driver-class-name"), dataSourceProperties.getDriverClassName(), "com.mysql.cj.jdbc.Driver")
        );
    }

    private Properties loadExternalEemrsProperties() {
        Path path = Path.of(externalConfigPath);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path).normalize();
        }
        if (!Files.exists(path)) {
            Path rootRelative = Path.of("").toAbsolutePath()
                    .resolve("eemrs-server-master/src/main/resources/application.yml")
                    .normalize();
            path = rootRelative;
        }
        if (!Files.exists(path)) {
            return new Properties();
        }

        Resource resource = new FileSystemResource(path);
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties properties = yaml.getObject();
        return properties == null ? new Properties() : properties;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DbSettings(String url, String username, String password, String driverClassName) {
    }
}
