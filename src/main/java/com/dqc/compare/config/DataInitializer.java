package com.dqc.compare.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dqc.compare.entity.CompareTaskConfig;
import com.dqc.compare.mapper.CompareTaskConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 种子数据初始化：当 compare_task_config 为空时注入默认「每日元数据巡检」任务。
 * 使用 Java 注入而非 SQL INSERT IGNORE，以保证 H2 / MySQL 跨库兼容。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CompareTaskConfigMapper configMapper;

    public DataInitializer(CompareTaskConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public void run(String... args) {
        Long count = configMapper.selectCount(new QueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        CompareTaskConfig c = new CompareTaskConfig();
        c.setTaskName("每日元数据巡检");
        c.setEnabled(true);
        c.setCronExpression("0 0 2 * * ?");
        c.setProdDdlPath("./data/input/production/latest");
        c.setDdmPath("./data/input/ddm");
        c.setSoaPath("./data/input/soa");
        c.setFileSpecPath("./data/input/file_spec");
        c.setRecipients("");
        configMapper.insert(c);
        log.info("已注入默认比对任务配置：{}", c.getTaskName());
    }
}
