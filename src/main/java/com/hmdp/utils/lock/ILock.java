package com.hmdp.utils.lock;

/**
 * 锁
 */
public interface ILock {
    /**
     * 获取锁
     * @param second
     * @return
     */
    boolean tryLock(Long second);

    /**
     * 释放锁
     */
    void unlock();
}
