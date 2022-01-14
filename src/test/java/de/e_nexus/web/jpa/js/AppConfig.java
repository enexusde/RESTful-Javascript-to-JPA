/*     ____  _____________________      __       __                  _____           _       __     __             _
 *    / __ \/ ____/ ___/_  __/ __/_  __/ /      / /___ __   ______ _/ ___/__________(_)___  / /_   / /_____       (_)___  ____ _
 *   / /_/ / __/  \__ \ / / / /_/ / / / /  __  / / __ `/ | / / __ `/\__ \/ ___/ ___/ / __ \/ __/  / __/ __ \     / / __ \/ __ `/
 *  / _, _/ /___ ___/ // / / __/ /_/ / /  / /_/ / /_/ /| |/ / /_/ /___/ / /__/ /  / / /_/ / /_   / /_/ /_/ /    / / /_/ / /_/ /
 * /_/ |_/_____//____//_/ /_/  \__,_/_/   \____/\__,_/ |___/\__,_//____/\___/_/  /_/ .___/\__/   \__/\____/  __/ / .___/\__,_/
 * Copyright 2019 by Peter Rader (e-nexus.de)                                     /_/                       /___/_/ 
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, 
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package de.e_nexus.web.jpa.js;

import java.sql.DriverManager;
import org.h2.jdbcx.JdbcConnectionPool;
import java.sql.SQLException;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPoolBackwardsCompat;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@EnableTransactionManagement
@ComponentScan("de.e_nexus.web.jpa.js")
public class AppConfig extends WebMvcConfigurerAdapter {

	@Bean
	protected LocalContainerEntityManagerFactoryBean emf(DataSource dataSource) throws SQLException {
		final LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setPersistenceProviderClass(org.hibernate.jpa.HibernatePersistenceProvider.class);
		emf.setPackagesToScan(AppConfig.class.getPackage().getName());
		emf.setDataSource(dataSource);
		final StandardDialectResolver resolver = new StandardDialectResolver();
		try {
			final Dialect dialect = resolver.resolveDialect(new DatabaseMetaDataDialectResolutionInfoAdapter(dataSource.getConnection().getMetaData()));
			emf.getJpaPropertyMap().put(Environment.DIALECT, dialect.getClass().getCanonicalName());
		} catch (SQLException databaseProblem) {
			databaseProblem.printStackTrace();
		}
		emf.getJpaPropertyMap().put(Environment.SHOW_SQL, true);
		emf.getJpaPropertyMap().put(Environment.HBM2DDL_AUTO, "update");
		return emf;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**").addResourceLocations("classpath:/META-INF/resources/");
	}

	/**
	 * The datasource to use.
	 */
	@Bean
	protected DataSource dataSource() {
		return JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");
	}

	/**
	 * The transaction-manager to be used in transactions.
	 * <p>
	 * This includes the {@link @Transactional Transactional}-annotation.
	 */
	@Bean
	protected PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
		final EntityManagerFactory factory = entityManagerFactory.getObject();
		final JpaTransactionManager manager = new JpaTransactionManager(factory);
		return manager;
	}

}
