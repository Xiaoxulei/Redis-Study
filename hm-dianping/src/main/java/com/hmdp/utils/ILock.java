package com.hmdp.utils;

/**
 * @Author: xuxiaolei
 * @Description: TODO: ILock
 * @CreatTime: 2025/07/30 15:56
 **/
public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
