package com.dqc.compare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 登录鉴权（一期收尾）。
 * <ul>
 *   <li>默认启用：表单登录 + Basic Auth（便于接口调用），CSRF 关闭（内部工具、无浏览器跨站场景）。</li>
 *   <li>放行登录页、H2 控制台与静态资源；其余全部需登录。</li>
 *   <li>app.security.enabled=false 时全员可访问（仅本地演示用）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final AppProperties appProperties;
    private final Environment environment;

    public SecurityConfig(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    /**
     * 生产（mysql profile）下禁止使用默认弱口令，避免上线后遗忘修改。
     */
    @PostConstruct
    public void verifyPasswordPolicy() {
        boolean isMysqlProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("mysql");
        if (isMysqlProfile && appProperties.getSecurity().isEnabled()
                && "admin123".equals(appProperties.getSecurity().getPassword())) {
            throw new IllegalStateException(
                    "生产环境（mysql profile）禁止使用默认口令 admin123，"
                            + "请通过环境变量 APP_SECURITY_PASSWORD 或 application-mysql.yml 的 app.security.password 设置强口令后重启。");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        AppProperties.Security sec = appProperties.getSecurity();
        UserDetails user = User.withUsername(sec.getUser())
                .password(encoder.encode(sec.getPassword()))
                .roles("ADMIN")
                .build();
        if (sec.isEnabled()) {
            log.info("登录鉴权已启用。默认账号：{} / {}（请尽快在 application.yml 的 app.security 中修改）",
                    sec.getUser(), sec.getPassword());
            if ("admin123".equals(sec.getPassword())) {
                log.warn("⚠️ 当前仍使用默认弱口令 admin123！生产环境请通过环境变量 APP_SECURITY_PASSWORD 覆盖，"
                        + "或在 application.yml 的 app.security.password 中设置强口令。");
            }
        }
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!appProperties.getSecurity().isEnabled()) {
            log.warn("登录鉴权已关闭（app.security.enabled=false），所有接口与页面全员可访问！");
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login.html", "/h2-console/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .headers(headers -> headers.frameOptions(frame -> frame.disable())) // H2 控制台需嵌入框架
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                // 不强制跳回首页：登录后优先回到登录前想访问的地址（如邮件直达链接 /go?tab=tickets&id=N），
                // 无保存请求时才回首页。否则非登录态点直达链接登录后定位信息会丢失。
                .defaultSuccessUrl("/")
                .failureUrl("/login.html?error=true")
                .permitAll())
            .httpBasic(Customizer.withDefaults())
            .logout(logout -> logout.permitAll());
        return http.build();
    }
}
