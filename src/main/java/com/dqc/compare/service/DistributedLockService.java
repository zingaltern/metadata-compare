package com.dqc.compare.service;

import com.dqc.compare.entity.AppLock;
import com.dqc.compare.mapper.AppLockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 基于数据库表的分布式锁（跨 H2 / MySQL 兼容）。
 *
 * <p>获取锁 = INSERT 唯一键；冲突时若锁已过期则 UPDATE 接管；释放 = 仅 owner 匹配的 DELETE。
 * 锁带 TTL，实例崩溃后过期自动恢复。注意：运行时长超过 TTL 时锁会被其他实例接管，
 * 因此 TTL 应显著大于单次比对的最长运行时长（默认 1 小时）。</p>
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private final AppLockMapper lockMapper;

    public DistributedLockService(AppLockMapper lockMapper) {
        this.lockMapper = lockMapper;
    }

    public boolean tryAcquire(String key, String owner, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plus(ttl);
        try {
            lockMapper.deleteExpired(now);
        } catch (Exception e) {
            log.debug("清理过期锁失败（忽略）：{}", e.getMessage());
        }
        AppLock lock = new AppLock();
        lock.setLockKey(key);
        lock.setOwner(owner);
        lock.setAcquiredAt(now);
        lock.setExpiresAt(expires);
        try {
            lockMapper.insert(lock);
            log.debug("获取分布式锁成功：{} owner={}", key, owner);
            return true;
        } catch (DuplicateKeyException e) {
            int taken = lockMapper.takeOverIfExpired(key, owner, now, expires);
            if (taken > 0) {
                log.debug("接管已过期的分布式锁：{} owner={}", key, owner);
                return true;
            }
            return false;
        }
    }

    public void release(String key, String owner) {
        try {
            lockMapper.release(key, owner);
        } catch (Exception e) {
            log.warn("释放分布式锁失败：{} -> {}", key, e.getMessage());
        }
    }
}
