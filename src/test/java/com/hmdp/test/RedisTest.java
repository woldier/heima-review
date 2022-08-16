package com.hmdp.test;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisTest {

    @Autowired
    ShopServiceImpl shopService;
    @Autowired
    CacheClient cacheClient;
    @Autowired
    RedisIdWorker redisIdWorker;

    private  ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void test1(){
        //cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY,new Long(1), Shop.class, e -> shopService.getById(e),CACHE_SHOP_TTL,LOCK_SHOP_TTL, TimeUnit.MINUTES);
    }

    @Test
    public void test2() throws InterruptedException {
        CountDownLatch downLatch = new CountDownLatch(500);

        Runnable runnable = () ->{
            for (int i = 0; i < 100; i++) {
                System.out.println(redisIdWorker.nextId("shop"));
            }
            downLatch.countDown();
        };
        long millis = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            executorService.submit(runnable);
        }

        downLatch.await();
        long millis2 = System.currentTimeMillis();
        System.out.println(millis2-millis);

    }

    @Test
    public void test3(){
        System.out.println(redisIdWorker.nextId("shop"));

    }


}
