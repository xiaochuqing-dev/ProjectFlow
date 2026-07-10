package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AuthService;

@SpringBootTest(properties = "PROJECTFLOW_DATA_DIR=${java.io.tmpdir}/projectflow-embedded-test")
@ActiveProfiles("embedded")
class EmbeddedProfileConfigurationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private AuthService authService;

    @Test
    void embeddedProfileUsesH2AndDoesNotRequireRedis() {
        assertThat(environment.getProperty("spring.cache.type")).isEqualTo("simple");
        assertThat(environment.getProperty("spring.data.redis.repositories.enabled")).isEqualTo("false");
        assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).isEmpty();

        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getJdbcUrl()).startsWith("jdbc:h2:file:");
    }

    @Test
    void embeddedProfileProvidesLocalUserWithoutBearerToken() {
        AuthUser first = authService.currentUser(null);
        AuthUser second = authService.currentUser(null);

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.username()).isNotBlank();
    }
}
