package com.hmdp.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * 可重入锁
 */

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class RedissonTest {

    @Autowired
    RedissonClient redissonClient;
    RLock lock;
    @Before
    public void setUp(){
        lock = redissonClient.getLock("order");
    }
    @Test
    public void method1(){
        boolean b = lock.tryLock();
        if(!b){
            log.error("获取锁失败");
            return;
        }
        try {
            log.info("获取锁成功......................1");
            //调用业务2
            method2();
            log.info("开始执行业务......................1");
        }
        finally {
            log.warn("准备释放锁......................1");
            lock.unlock();
        }

    }

    public void method2(){
        boolean b = lock.tryLock();
        if(!b){
            log.error("获取锁失败");
            return;
        }
        try {
            log.info("获取锁成功......................2");
            //调用业务2
            log.info("开始执行业务......................2");
        }
        finally {
            log.warn("准备释放锁......................2");
            lock.unlock();
        }
    }
}
