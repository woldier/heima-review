package com.hmdp.test;

import com.hmdp.service.impl.ShopServiceImpl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisTest {

    @Autowired
    ShopServiceImpl shopService;

    @Test
    public void test1() throws InterruptedException {
        shopService.saveShop2Redis(1L,200L);
    }

}
