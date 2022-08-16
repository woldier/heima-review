package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一id生成器
 * id结构如下
 *    64   (数字位恒0)     |          63-32 (时间从某个时间开始的秒数)       |        31-0(数字自增)
 *    这里我们以2022-1-1-00:00:00为基准
 */
@Slf4j
@Component
public class RedisIdWorker {
    private final long BEGIN_TIME = 1640995200L;

    private final StringRedisTemplate redisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId(String key){

        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        second = second - BEGIN_TIME;
        //2.获取自增数
        //2.1生成当天日期yyyy:MM:dd
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = redisTemplate.opsForValue().increment("inc:" + key + ":" + date);
        //3.拼接返回
        return second <<32 | increment;
    }



    /**
     *
     * 得到一个基准时间
     * @param args
     */
    public static void main(String[] args) {
        long second = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        log.info(""+second);
    }
}
