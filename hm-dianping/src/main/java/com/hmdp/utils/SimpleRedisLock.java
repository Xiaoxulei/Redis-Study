package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static com.hmdp.utils.RedisConstants.LOCK_KEY_PREFIX;

/**
 * @Author: xuxiaolei
 * @Description: TODO: SimpleRedisLock
 * @CreatTime: 2025/07/30 15:57
 **/
public class SimpleRedisLock implements ILock {

    /*private static final String LOCK_KEY_PREFIX = "lock:";*/
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 指定脚本位置
        UNLOCK_SCRIPT.setResultType(Long.class); // 设置返回类型！
    }
    //线程ID作为锁标识不够安全
    //线程ID在分布式系统不同 JVM 中不是唯一标识，最好用 UUID 或 线程ID + JVM唯一ID 作为锁的 value，解锁时判断是否为自己加的锁。
    private String lockValue; // 用于标识当前线程的锁

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        //拼接字符串时，Java 会自动调用 .toString() 方法（隐式调用）；
        this.lockValue = UUID.randomUUID().toString() + "-" + Thread.currentThread().getId();
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //获取锁
    @Override
    public boolean tryLock(long timeoutSec) {
        /*long treadId = Thread.currentThread().getId();*/
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY_PREFIX + name, lockValue, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock() {
        //调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_KEY_PREFIX + name),
                lockValue);
    }
    /*@Override
    public void unlock() {
        //获取线程标识
        String threadId = LOCK_KEY_PREFIX + Thread.currentThread().getId();
        //获取redis标识
        String redisId = stringRedisTemplate.opsForValue().get(lockValue);
        if(threadId.equals(redisId)){
            stringRedisTemplate.delete(LOCK_KEY_PREFIX + name);
        }

    }*/
}
