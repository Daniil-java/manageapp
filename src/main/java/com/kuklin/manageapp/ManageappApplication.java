package com.kuklin.manageapp;

import com.kuklin.manageapp.common.telegram.config.DisabledBotsFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// То же, что @SpringBootApplication, но со своим фильтром сканирования:
// бины выключенных независимых ботов не создаются вовсе (DisabledBotsFilter, bots.disabled)
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = DisabledBotsFilter.class)
})
@EnableFeignClients
@EnableScheduling
@EnableAsync
public class ManageappApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManageappApplication.class, args);
	}

}
