package com.hmdp.utils;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * redis 封装
 */
@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;
    /**
     * 工作线程池
     */
    private static final ExecutorService CACHE_REBUILD_SERVICE = Executors.newFixedThreadPool(10);
    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    /*-----------------------------------------------------*/

    /**
     * 设置key
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time , TimeUnit timeUnit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    public void set(String key, Object value){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    /**
     * 设置key 并且给定逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time , TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        /*设置过期时间为现在的多少秒之后*/
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        this.set(key,redisData);
    }

    /**
     * 缓存穿透get
     * @param keyPrefix key前缀
     * @param id key值
     * @param type 返回类型
     * @param dbCallback  查询数据库的回调函数
     * @param time 过期时间TTL
     * @param timeUnit 时间单位
     * @param <R> 返回泛型
     * @param <ID> id/key 的泛型
     * @return
     * @throws BizException
     */
    public  <R,ID> R queryByIdWithCacheThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time , TimeUnit timeUnit
    ) throws BizException {
        String  REGIN = keyPrefix + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        /*2.redis存在直接返回,并设置刷新时间*/
        if (cache!=null) {
            if ("".equals(cache))
                /*表明为空缓存,防止击穿抛出异常*/
                throwShopInfoNE();
            /*刷新有效期*/
            redisTemplate.expire(REGIN,time, timeUnit);
            return JSONUtil.toBean(cache, type);
        }

        /*3.redis不在查询数据库*/
        //Shop shop = this.getById(id);
        R r = dbCallback.apply(id);
        /*4.数据库不存在*/
        if (r==null) {
            /*设置空缓存防止内存穿透,并且抛出异常*/
            redisTemplate.opsForValue().set(REGIN,"");
            redisTemplate.expire(REGIN,time,timeUnit);
            throwShopInfoNE();
        }

        /*5.数据库存在设置到redis并返回*/
        String shopAsString = JSONUtil.toJsonStr(r);
        redisTemplate.opsForValue().set(REGIN,shopAsString);
        /*设置过期时间*/
        redisTemplate.expire(REGIN,time,timeUnit);
        return r;
    }
    private void throwShopInfoNE() throws BizException {
        throw new BizException("商户不存在");
    }



    public   <R,ID> R queryByIdWithLogicExpire(  String keyPrefix,String lockPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time ,Long lockTime, TimeUnit timeUnit) {
        String  REGIN = keyPrefix + id;
        String  LOCK_REGIN = lockPrefix + id;
        R r =null;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        if(cache!=null){
            /*2.检查过期时间,若没有过期直接返回*/
            RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
            /*获取过期时间*/
            LocalDateTime expireTime = redisData.getExpireTime();
            /*获取商户数据*/
            r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
            LocalDateTime now = LocalDateTime.now();
            if(now.isBefore(expireTime))/*未过期直接返回*/
                return r;
        }
        /*过期进行数据的重载*/
        /*3.获取锁*/
        boolean lock = getLock(LOCK_REGIN,lockTime);
        /*3.1获取不成功,使用原始数据*/
        if (lock) {
            // TODO 这里还应该再检测一次是否过期
            CACHE_REBUILD_SERVICE.submit(()->{
                try {
                    /*查询数据库*/
                    R r1 = dbCallback.apply(id);
                    /*设置逻辑过期时间*/
                    this.setWithLogicalExpire(REGIN,r1,time,timeUnit);
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    /*释放锁*/
                    unLock(LOCK_REGIN);
                }
            });
        }
        return r;
    }


    /**
     * 缓存击穿+缓存穿透, mutex实现
     * @throws BizException
     */
    public   <R,ID> R  queryByIdWithCacheMutex( String keyPrefix,String lockPrefix, ID id, Class<R> type, Function<ID,R> dbCallback, Long time ,Long lockTime, TimeUnit timeUnit) throws BizException {
        String  REGIN = keyPrefix + id;
        String  LOCK_REGIN = lockPrefix + id;
        /*1.从redis查询*/
        String cache = redisTemplate.opsForValue().get(REGIN);
        R r = null;
        /*2.redis存在直接返回,并设置刷新时间*/
        if (cache!=null) {
            if ("".equals(cache))
                /*表明为空缓存,防止击穿抛出异常*/
                throwShopInfoNE();
            /*刷新有效期*/
            redisTemplate.expire(REGIN,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            r = JSONUtil.toBean(cache, type);
            return r;
        }
        try {
            /*3.redis不在查询数据库*/
            /*3.1获取锁*/
            if(! getLock(LOCK_REGIN,lockTime)){
                /*3.3失败则睡眠,再重新调用本方法*/
                Thread.sleep(50);
                return queryByIdWithCacheMutex(keyPrefix,lockPrefix,id,type,dbCallback,time,lockTime,timeUnit);
            }
            /*3.2获取成功查询数据库*/
            /*3.2.1在查询数据库之前,我们应该再次查询redis 看看数据是否存在 存在则无需重建缓存*/
            /*3.2.1.1从redis查询*/
            cache = redisTemplate.opsForValue().get(REGIN);
            /*3.2.1.2.redis存在直接返回,并设置刷新时间*/
            if (cache!=null) {

                if ("".equals(cache))
                    /*表明为空缓存,防止击穿抛出异常*/
                    throwShopInfoNE();
                /*刷新有效期*/
                redisTemplate.expire(REGIN,time, timeUnit);
                r = JSONUtil.toBean(cache, type);
                return r;
            }
            r = dbCallback.apply(id);
            /*4.数据库不存在*/
            if (r==null) {
                /*设置空缓存防止内存穿透,并且抛出异常*/
                redisTemplate.opsForValue().set(REGIN,"");
                redisTemplate.expire(REGIN,time, timeUnit);
                throwShopInfoNE();

            }
            /*5.数据库存在设置到redis并返回*/
            String shopAsString = JSONUtil.toJsonStr(r);
            redisTemplate.opsForValue().set(REGIN,shopAsString);
            /*5.1设置过期时间*/
            redisTemplate.expire(REGIN,time, timeUnit);
        } catch (InterruptedException e) {
            throw  new RuntimeException();
        } finally {
            /*6.释放锁*/
            unLock(LOCK_REGIN);
        }
        return r;
    }



    /**
     * 获取锁
     * @param key
     * @return
     */
    public boolean getLock(String key,Long time){
        /*得到锁并设置有效时间*/
        return redisTemplate.opsForValue().setIfAbsent(key, "1", time, TimeUnit.SECONDS).booleanValue();

    }

    /**
     * 释放锁
     * @param key
     */
    public void unLock(String key){
        redisTemplate.delete(key);
    }



}
