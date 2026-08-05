<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"/><title>比对汇总</title></head>
<body style="font-family: -apple-system, 'Segoe UI', Arial, sans-serif; color:#222; padding:16px;">
<div style="max-width:640px; margin:auto; border:1px solid #e2e2e2; border-radius:8px; overflow:hidden;">
    <div style="background:#2c3e50; color:#fff; padding:14px 18px; font-size:16px; font-weight:600;">
        元数据比对 · 定时巡检完成
    </div>
    <div style="padding:18px;">
        <p style="margin:0 0 12px;">任务：<b>${taskName!''}</b>（执行ID：${taskId!''}）</p>
        <table style="width:100%; border-collapse:collapse; font-size:14px;">
            <tr><td style="padding:6px 8px; background:#f7f7f7; width:160px;">问题总数</td><td style="padding:6px 8px;">${totalCount!0}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7; color:#c0392b;">严重(CRITICAL)</td><td style="padding:6px 8px;">${criticalCount!0}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7; color:#d35400;">警告(WARNING)</td><td style="padding:6px 8px;">${warningCount!0}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">复核工单</td><td style="padding:6px 8px;">${ticketCount!0}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">耗时</td><td style="padding:6px 8px;">${durationMs!0} ms</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">数据源健康</td><td style="padding:6px 8px;">${sourceHealthSummary!'-'}</td></tr>
        </table>
        <#if tickets?has_content>
            <p style="margin:18px 0 8px; font-size:13px; font-weight:600;">本次新增待复核工单（${tickets?size} 张）</p>
            <table style="width:100%; border-collapse:collapse; font-size:13px;">
                <tr style="background:#f7f7f7; color:#555;">
                    <th style="padding:6px 8px; text-align:left;">工单编号</th>
                    <th style="padding:6px 8px; text-align:left;">级别</th>
                    <th style="padding:6px 8px; text-align:left;">表</th>
                    <th style="padding:6px 8px; text-align:left;">字段</th>
                    <th style="padding:6px 8px; text-align:left;">问题描述</th>
                    <th style="padding:6px 8px; text-align:left;">操作</th>
                </tr>
                <#list tickets as t>
                <tr>
                    <td style="padding:6px 8px;">${t.ticketNo}</td>
                    <td style="padding:6px 8px;">${t.severity!''}</td>
                    <td style="padding:6px 8px;">${t.tableName!''}</td>
                    <td style="padding:6px 8px;">${t.fieldName!'（整表）'}</td>
                    <td style="padding:6px 8px;">${t.message!''}</td>
                    <td style="padding:6px 8px;"><a href="${baseUrl}/go?tab=tickets&amp;id=${t.id}">查看工单</a></td>
                </tr>
                </#list>
            </table>
        <#else>
            <p style="margin:16px 0 0; font-size:12px; color:#888;">本次未产生新的复核工单。</p>
        </#if>
        <p style="margin:16px 0 0; font-size:12px; color:#888;">
            严重问题已自动创建复核工单。可在系统内「待复核工单」页查看与处理。
        </p>
    </div>
</div>
</body>
</html>
