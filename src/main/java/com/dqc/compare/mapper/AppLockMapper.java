package com.dqc.compare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dqc.compare.entity.AppLock;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AppLockMapper extends BaseMapper<AppLock> {

    /** 清理已过期的锁（获取锁前的机会性清理）。 */
    @Update("DELETE FROM app_lock WHERE expires_at < #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** 锁已过期时接管（无条件更新到新 owner）。返回 1 表示接管成功。 */
    @Update("UPDATE app_lock SET owner = #{owner}, acquired_at = #{now}, expires_at = #{expires} "
            + "WHERE lock_key = #{key} AND expires_at < #{now}")
    int takeOverIfExpired(@Param("key") String key, @Param("owner") String owner,
                          @Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);

    /** 释放锁（仅 owner 本人可释放，防止误删他人锁）。 */
    @Delete("DELETE FROM app_lock WHERE lock_key = #{key} AND owner = #{owner}")
    int release(@Param("key") String key, @Param("owner") String owner);
}
