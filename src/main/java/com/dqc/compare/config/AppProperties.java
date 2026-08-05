package com.dqc.compare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务配置绑定（application.yml 中 app.* 节点）。
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Input input = new Input();
    private Rules rules = new Rules();
    private Scheduler scheduler = new Scheduler();
    private Ticket ticket = new Ticket();
    private Rule rule = new Rule();
    private TableMapping tableMapping = new TableMapping();
    private Security security = new Security();

    public static class Input {
        /** 输入文件根目录（按文档 2.4 规范：production/ddm/soa/file_spec） */
        private String baseDir = "./data/input";

        public String getBaseDir() { return baseDir; }
        public void setBaseDir(String baseDir) { this.baseDir = baseDir; }
    }

    public static class Rules {
        /** 规则文件路径（文件系统路径支持热加载；classpath: 前缀仅加载不热更） */
        private String path = "./rules/compare-rules.yml";
        /** 热加载检测周期（秒） */
        private int hotReloadSeconds = 60;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getHotReloadSeconds() { return hotReloadSeconds; }
        public void setHotReloadSeconds(int hotReloadSeconds) { this.hotReloadSeconds = hotReloadSeconds; }
    }

    public static class Scheduler {
        /** 定时轮询周期（秒） */
        private int pollSeconds = 30;

        public int getPollSeconds() { return pollSeconds; }
        public void setPollSeconds(int pollSeconds) { this.pollSeconds = pollSeconds; }
    }

    public static class Ticket {
        /** 工单编号前缀 */
        private String noPrefix = "RT";

        public String getNoPrefix() { return noPrefix; }
        public void setNoPrefix(String noPrefix) { this.noPrefix = noPrefix; }
    }

    public static class Rule {
        /** 单条规则执行超时（毫秒） */
        private long timeoutMs = 3000;

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class TableMapping {
        /** 表名映射文件路径（文件系统路径支持热加载；classpath: 前缀仅加载不热更） */
        private String path = "./rules/table-mappings.yml";
        /** 热加载检测周期（秒） */
        private int hotReloadSeconds = 60;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getHotReloadSeconds() { return hotReloadSeconds; }
        public void setHotReloadSeconds(int hotReloadSeconds) { this.hotReloadSeconds = hotReloadSeconds; }
    }

    public Input getInput() { return input; }
    public void setInput(Input input) { this.input = input; }
    public Rules getRules() { return rules; }
    public void setRules(Rules rules) { this.rules = rules; }
    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public Rule getRule() { return rule; }
    public void setRule(Rule rule) { this.rule = rule; }
    public TableMapping getTableMapping() { return tableMapping; }
    public void setTableMapping(TableMapping tableMapping) { this.tableMapping = tableMapping; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public static class Security {
        /** 是否启用登录鉴权（设为 false 则全员可访问，仅建议本地演示关闭） */
        private boolean enabled = true;
        /** 登录用户名 */
        private String user = "admin";
        /** 登录密码 */
        private String password = "admin123";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
