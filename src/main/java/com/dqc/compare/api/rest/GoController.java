package com.dqc.compare.api.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 直达链接中转（邮件工单链接用）。
 *
 * <p>邮件链接若直接指向 /#tickets?id=N，URL 片段不会发给服务器：
 * 未登录时会被登录流程丢掉，登录后无法定位。改为经 /go 中转：
 * 查询参数随登录重定向保留，最终 302 到 /#tickets?id=N，由前端定位并高亮。</p>
 */
@RestController
public class GoController {

    @GetMapping("/go")
    public ResponseEntity<Void> go(@RequestParam(defaultValue = "console") String tab,
                                   @RequestParam(required = false) Long id) {
        StringBuilder target = new StringBuilder("/#").append(tab);
        if (id != null) {
            target.append("?id=").append(id);
        }
        return ResponseEntity.status(302).location(URI.create(target.toString())).build();
    }
}
