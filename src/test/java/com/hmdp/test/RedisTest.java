package com.hmdp.test;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.CacheClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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


    @Test
    public void test1(){
        //cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY,new Long(1), Shop.class, e -> shopService.getById(e),CACHE_SHOP_TTL,LOCK_SHOP_TTL, TimeUnit.MINUTES);
    }
}
