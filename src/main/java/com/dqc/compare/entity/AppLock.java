package com.dqc.compare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分布式运行锁（app_lock 表）。
 *
 * <p>用于多实例部署时保证同一任务的比对在同一时刻只有一个实例执行，
 * 避免结果 / 工单重复入库。锁带过期时间，实例崩溃后可自动恢复。</p>
 */
@Data
@TableName("app_lock")
public class AppLock {

    @TableId(type = IdType.INPUT)
    private String lockKey;
    private String owner;
    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;
}
