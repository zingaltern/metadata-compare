package com.dqc.compare.rule;

import com.dqc.compare.rule.functions.HasChineseFunction;
import com.dqc.compare.rule.functions.HasTimestampFieldFunction;
import com.dqc.compare.rule.functions.DeliveryMismatchFunction;
import com.dqc.compare.rule.functions.RegexMatchFunction;
import com.dqc.compare.rule.functions.TypeCompatibleFunction;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * QLExpress 规则引擎（对应文档 4.3）。
 * <ul>
 *   <li>开启表达式追踪（isTrace），支持规则归因分析</li>
 *   <li>注册自定义函数：regexMatch / typeCompatible / hasTimestampField</li>
 *   <li>单条规则执行超时由调用方传入（默认 3 秒），超时即中断并降级为 REPORT_ONLY</li>
 * </ul>
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /** isPrecise=true 精确模式；isTrace=false 关闭 QLExpress 语法树/指令日志，避免控制台噪音。 */
    private final ExpressRunner runner = new ExpressRunner(true, false);
    /** 有界线程池：池满时 AbortPolicy 拒绝，由调用线程内联执行（放弃软超时，避免中断请求线程）。
     *  注意：QLExpress 不可中断，超时线程会继续跑完，故池大小需克制。 */
    private final ExecutorService executor = new ThreadPoolExecutor(0, 8, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "qlexpress-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public RuleEngine() {
        runner.addFunction("regexMatch", new RegexMatchFunction());
        runner.addFunction("hasChinese", new HasChineseFunction());
        runner.addFunction("typeCompatible", new TypeCompatibleFunction());
        runner.addFunction("hasTimestampField", new HasTimestampFieldFunction());
        runner.addFunction("deliveryMismatch", new DeliveryMismatchFunction());
    }

    /**
     * 在给定上下文下评估单条规则。
     *
     * @param rule      规则定义
     * @param context   变量上下文（field / prodField / modelField / soaField / specField / fileSpec / tableName）
     * @param timeoutMs 单条规则超时（毫秒）；超时则中断工作线程并降级为不命中
     * @return 评估结果（matched / error / trace 变量快照）
     */
    public RuleEvalResult evaluate(RuleDef rule, Map<String, Object> context, long timeoutMs) {
        long start = System.currentTimeMillis();
        DefaultContext<String, Object> ctx = new DefaultContext<>();
        ctx.putAll(context);

        List<String> errorList = new ArrayList<>();
        AtomicReference<Boolean> matchedRef = new AtomicReference<>(false);
        AtomicReference<String> errRef = new AtomicReference<>();

        Runnable job = () -> {
            try {
                Object r = runner.execute(rule.getCondition(), ctx, errorList, true, true);
                matchedRef.set(Boolean.TRUE.equals(r));
            } catch (Throwable t) {
                errRef.set(t.getMessage());
            }
        };

        try {
            Future<?> future = executor.submit(job);
            try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                errRef.set("RULE_TIMEOUT");
            } catch (Exception e) {
                errRef.set(e.getMessage());
            }
        } catch (RejectedExecutionException e) {
            // 线程池满：调用线程内联执行（规则引擎本身很快；放弃超时控制但保证不中断请求线程）
            job.run();
        }

        long elapsed = System.currentTimeMillis() - start;
        if (errRef.get() != null && !"RULE_TIMEOUT".equals(errRef.get())) {
            log.debug("规则[{}]评估异常(视为不命中): {}", rule.getName(), errRef.get());
        }
        return new RuleEvalResult(Boolean.TRUE.equals(matchedRef.get()), errRef.get(),
                elapsed, new LinkedHashMap<>(context));
    }
}
