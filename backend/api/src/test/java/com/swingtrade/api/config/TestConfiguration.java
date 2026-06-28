package com.swingtrade.api.config;

import com.swingtrade.api.controller.FyersAuthController;
import com.swingtrade.api.controller.UpstoxAuthController;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    JdbcRepositoriesAutoConfiguration.class
})
@Import({UpstoxAuthController.class, FyersAuthController.class})
public class TestConfiguration {
}
