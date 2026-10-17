package br.com.unifinancas.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled} - usado pelo {@link br.com.unifinancas.infrastructure.jobs.JobWorker}. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
