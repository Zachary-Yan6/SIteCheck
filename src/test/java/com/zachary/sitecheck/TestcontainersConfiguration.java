package com.zachary.sitecheck;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
	}

	/** Same LocalStack version as docker-compose.yml, but with presigned-URL signatures enforced like real S3. */
	@Bean
	LocalStackContainer localStackContainer() {
		return new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4"))
				.withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
	}

	/** Points the app's S3 client at the LocalStack container instead of localhost:4566. */
	@Bean
	DynamicPropertyRegistrar s3EndpointProperties(LocalStackContainer localStack) {
		return registry -> {
			registry.add("sitecheck.s3.endpoint", () -> localStack.getEndpoint().toString());
			registry.add("sitecheck.s3.presign-endpoint", () -> localStack.getEndpoint().toString());
			// No scheduled reminders during tests, and never a real call to Expo.
			registry.add("sitecheck.reminders.enabled", () -> "false");
			registry.add("sitecheck.push.expo-url", () -> "http://localhost:9/expo-not-used-in-tests");
		};
	}

}
