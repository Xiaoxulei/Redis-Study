package com.hmdp;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private RedisIdWorker  redisIdWorker;
    @Resource
    private UserServiceImpl userServiceImpl;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    void test() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = ()->{
            for(int i=0;i<100;i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for(int i=0;i<300;i++){
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
    @Test
    void testSaveShop()throws InterruptedException {

        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
        /*cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY + 1L,1,Shop.class,20L,this::getById,)*/

    }
    @Resource
    private PasswordEncoder passwordEncoder;
    @Test
    void testInsertUser(){
        User user = new User();
        user.setPhone("18798364357");
        user.setPassword(passwordEncoder.encode("xuxiaolei"));
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>();
        queryWrapper.eq(User::getPhone,"18798364357");
        userServiceImpl.update(user,queryWrapper);
    }
}
