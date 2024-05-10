package io.littlehorse.usertasks;

import io.littlehorse.usertasks.properties.TenantOIDCProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TenantOIDCProperties.class)
public class UserTasksApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserTasksApplication.class, args);
	}

}
