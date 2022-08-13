package com.hmdp.utils;

public class RedisConstants {
    /**
     * 验证码域
     */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /**
     * 验证码过期时间
     */
    public static final Long LOGIN_CODE_TTL = 2L;
    /**
     * 登录用户域
     */
    public static final String LOGIN_USER_KEY = "login:token:";
    /**
     * 登录用户过期时间
     */
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
