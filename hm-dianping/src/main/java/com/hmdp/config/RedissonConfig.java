package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: xuxiaolei
 * @Description: TODO: RedissonConfig
 * @CreatTime: 2025/07/31 10:57
 **/
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("xuxiaolei");
        return Redisson.create(config);
    }
    /*@Bean
    public RedissonClient redissonClient1() {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6380").setPassword("xuxiaolei");
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2() {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6381").setPassword("xuxiaolei");
        return Redisson.create(config);
    }*/
}
