package com.dqc.compare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 元数据自动化比对系统（一期）启动类。
 *
 * <p>四层架构：调度层 / 处理层 / 输出层 / 基础设施层。默认使用 H2（MySQL 兼容模式）即可直接运行，
 * 生产环境通过 {@code --spring.profiles.active=mysql} 切换到 MySQL 8.0。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.dqc.compare.mapper")
public class MetadataCompareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataCompareApplication.class, args);
    }
}
