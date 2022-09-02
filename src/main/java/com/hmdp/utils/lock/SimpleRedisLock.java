package com.hmdp.utils.lock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;

/**
 * 分布式锁,redis实现
 */
public class SimpleRedisLock implements ILock {
    /**
     * redis模板
     */
    private StringRedisTemplate redisTemplate;
    /**
     * 具体锁的名称
     */
    private String key;
    /**
     * 前缀
     */
    private final static String KEY_PREFIX = "lock:";
    private final static String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    /**
     * lua脚本
     */
    private final static DefaultRedisScript<Long> unlock;
    static {
        unlock = new DefaultRedisScript<>();
        //从classpath加载
        unlock.setLocation(new ClassPathResource("unlock.lua"));
    }

    /**
     * 构造函数传入参数
     *
     * @param redisTemplate
     * @param key
     */
    public SimpleRedisLock(StringRedisTemplate redisTemplate, String key) {
        this.redisTemplate = redisTemplate;
        this.key = key;
    }

    @Override
    public boolean tryLock(Long second) {
        String value = ID_PREFIX + Thread.currentThread().getId() + "";
        /*设置key,并且给定过期时间*/
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, value, Duration.ofSeconds(10)));
    }

    @Override
    public void unlock() {
//        String value = ID_PREFIX + Thread.currentThread().getId() + "";
//        String s = redisTemplate.opsForValue().get(KEY_PREFIX + key);
//        if (value.equals(s))
//            redisTemplate.delete(KEY_PREFIX + key);


        String value = ID_PREFIX + Thread.currentThread().getId() + "";
        redisTemplate.execute(
                unlock, //脚本
                Collections.singletonList(KEY_PREFIX + key), //keys
                value //arg
        );
    }
}
