package com.sprintmodus.common_lib.tenant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link TenantRoutingDataSource} for services that read tenant databases. Import it explicitly
 * ({@code @Import(TenantDataSourceConfiguration.class)}); this library is not component-scanned.
 * <p>
 * There is no master DataSource here on purpose: only auth-service may touch the master database, so it defines that
 * one itself.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantDataSourceProperties.class)
public class TenantDataSourceConfiguration {

	@Bean
	TenantDatabaseNameResolver tenantDatabaseNameResolver() {
		return new TenantDatabaseNameResolver();
	}

	@Bean(destroyMethod = "close")
	TenantRoutingDataSource tenantDataSource(TenantDatabaseNameResolver resolver, TenantDataSourceProperties properties) {
		return TenantRoutingDataSource.pooled(resolver, properties);
	}

}
