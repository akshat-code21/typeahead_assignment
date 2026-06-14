package com.assignment.typeahead.config;

import com.assignment.typeahead.cache.ConsistentHashRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Configuration
@EnableScheduling
public class AppConfig {

    @Value("${typeahead.cache.node-count:4}")
    private int nodeCount;

    @Value("${typeahead.cache.virtual-nodes:150}")
    private int virtualNodes;

    @Bean
    public ConsistentHashRouter<String> consistentHashRouter() {
        List<String> nodes = IntStream.range(0, nodeCount)
                .mapToObj(i -> "cache-node-" + i)
                .collect(Collectors.toList());
        return new ConsistentHashRouter<>(nodes, virtualNodes);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders("*");
            }
        };
    }
}
