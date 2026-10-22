package com.smartmall.agent.llm;

import com.smartmall.common.BizException;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DeepSeek 调用入口。未配置 API Key 或连续失败时自动熔断，
 * 上层 Agent 会切换到规则引擎，保证系统在无 LLM 时仍可完整运行。
 */
@Slf4j
@Service
public class LlmService {

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${llm.model:deepseek-flash}")
    private String modelName;

    @Value("${llm.temperature:0.3}")
    private Double temperature;

    @Value("${llm.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${llm.enabled:true}")
    private boolean enabled;

    @Value("${llm.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    @Value("${llm.cooldown-seconds:60}")
    private long cooldownSeconds;

    private OpenAiChatModel model;
    private volatile String activeModel;
    private volatile Double activeTemperature;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong blockedUntil = new AtomicLong();
    private final AtomicLong callCount = new AtomicLong();
    private final AtomicLong failCount = new AtomicLong();
    private volatile String lastError;

    @PostConstruct
    public void init() {
        this.activeModel = modelName;
        this.activeTemperature = temperature;
        if (!enabled) {
            log.info("LLM 已通过配置关闭，Agent 将使用规则引擎");
            return;
        }
        if (!StringUtils.hasText(apiKey)) {
            log.info("未配置 DEEPSEEK_API_KEY，Agent 将以规则引擎模式运行（功能完整，推荐理由使用模板生成）");
            return;
        }
        try {
            this.model = buildModel(modelName, temperature);
            log.info("DeepSeek 已接入：model={}, baseUrl={}", modelName, baseUrl);
        } catch (Exception e) {
            log.warn("LLM 初始化失败，降级为规则引擎: {}", e.getMessage());
            this.model = null;
            this.lastError = e.getMessage();
        }
    }

    private OpenAiChatModel buildModel(String name, Double temp) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(name)
                .temperature(temp)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(1)
                .build();
    }

    /**
     * 运行时切换模型/温度。API Key 始终只从环境变量读取，不落库。
     * 未配置 Key 时保存配置但不创建模型，配置 Key 并重启后生效。
     */
    public synchronized void reconfigure(String name, Double temp) {
        if (!enabled) {
            throw new BizException("LLM 已在配置中关闭");
        }
        String nextModel = StringUtils.hasText(name) ? name.trim() : activeModel;
        Double nextTemp = temp == null ? activeTemperature : temp;
        if (nextTemp < 0 || nextTemp > 2) {
            throw new BizException("温度必须在 0 到 2 之间");
        }
        activeModel = nextModel;
        activeTemperature = nextTemp;
        if (!StringUtils.hasText(apiKey)) {
            log.info("模型配置已更新为 {}，但未配置 API Key，暂不创建模型连接", nextModel);
            return;
        }
        this.model = buildModel(nextModel, nextTemp);
        consecutiveFailures.set(0);
        blockedUntil.set(0);
        log.info("LLM 已切换：model={}, temperature={}", nextModel, nextTemp);
    }

    public boolean isConfigured() {
        return model != null;
    }

    public boolean isAvailable() {
        if (model == null) {
            return false;
        }
        if (consecutiveFailures.get() < maxConsecutiveFailures) {
            return true;
        }
        return Instant.now().toEpochMilli() > blockedUntil.get();
    }

    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return null;
        }
        callCount.incrementAndGet();
        try {
            String answer = model.chat(systemPrompt + "\n\n" + userPrompt);
            consecutiveFailures.set(0);
            return answer == null ? null : answer.trim();
        } catch (Exception e) {
            failCount.incrementAndGet();
            lastError = e.getMessage();
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= maxConsecutiveFailures) {
                blockedUntil.set(Instant.now().plusSeconds(cooldownSeconds).toEpochMilli());
                log.warn("LLM 连续失败 {} 次，熔断 {} 秒后重试: {}", failures, cooldownSeconds, e.getMessage());
            } else {
                log.warn("LLM 调用失败（第 {} 次），降级处理: {}", failures, e.getMessage());
            }
            return null;
        }
    }

    public LlmStatus status() {
        LlmStatus status = new LlmStatus();
        status.setConfigured(model != null);
        status.setAvailable(isAvailable());
        status.setModel(activeModel);
        status.setTemperature(activeTemperature);
        status.setCallCount(callCount.get());
        status.setFailCount(failCount.get());
        status.setLastError(lastError);
        status.setMode(isAvailable() ? "llm" : "rule-engine");
        return status;
    }

    @lombok.Data
    public static class LlmStatus {
        private boolean configured;
        private boolean available;
        private String mode;
        private String model;
        private Double temperature;
        private long callCount;
        private long failCount;
        private String lastError;
    }
}
