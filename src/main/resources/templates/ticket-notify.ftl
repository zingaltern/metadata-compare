<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"/><title>复核工单通知</title></head>
<body style="font-family: -apple-system, 'Segoe UI', Arial, sans-serif; color:#222; padding:16px;">
<div style="max-width:640px; margin:auto; border:1px solid #e2e2e2; border-radius:8px; overflow:hidden;">
    <div style="background:#c0392b; color:#fff; padding:14px 18px; font-size:16px; font-weight:600;">
        ${appName} · 待复核工单
    </div>
    <div style="padding:18px;">
        <p style="margin:0 0 12px;">检测到元数据比对严重问题，已自动创建复核工单，请及时处理。</p>
        <table style="width:100%; border-collapse:collapse; font-size:14px;">
            <tr><td style="padding:6px 8px; background:#f7f7f7; width:120px;">工单编号</td><td style="padding:6px 8px;">${ticket.ticketNo}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">严重程度</td><td style="padding:6px 8px;">${ticket.severity!''}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">涉及表</td><td style="padding:6px 8px;">${ticket.tableName!''}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">涉及字段</td><td style="padding:6px 8px;">${ticket.fieldName!'(整表)'}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7; vertical-align:top;">问题描述</td><td style="padding:6px 8px;">${ticket.message!''}</td></tr>
            <tr><td style="padding:6px 8px; background:#f7f7f7;">当前状态</td><td style="padding:6px 8px;">${ticket.status!''}</td></tr>
        </table>
        <p style="margin:16px 0 0; font-size:12px; color:#888;">
            请登录系统于「复核工单」模块处理（PUT /api/tickets/{id}/review）。
        </p>
    </div>
</div>
</body>
</html>
