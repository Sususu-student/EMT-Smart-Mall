package com.smartmall.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.agent.dto.AgentEvent;
import com.smartmall.agent.dto.AgentStep;
import com.smartmall.agent.dto.ExecutionLogVO;
import com.smartmall.agent.entity.AgentExecutionLog;
import com.smartmall.agent.mapper.AgentExecutionLogMapper;
import com.smartmall.agent.ws.AgentWebSocketHandler;
import com.smartmall.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLogService {

    private final AgentExecutionLogMapper logMapper;
    private final AgentWebSocketHandler webSocketHandler;

    public TraceStep start(String taskId, String conversationId, String agentId, String step,
                           String stepName, String input) {
        return new TraceStep(taskId, conversationId, agentId, step, stepName, input);
    }

    public List<ExecutionLogVO> byTask(String taskId) {
        return logMapper.selectList(new LambdaQueryWrapper<AgentExecutionLog>()
                        .eq(AgentExecutionLog::getTaskId, taskId)
                        .orderByAsc(AgentExecutionLog::getId))
                .stream().map(this::toVO).toList();
    }

    public List<ExecutionLogVO> byConversation(String conversationId) {
        return logMapper.selectList(new LambdaQueryWrapper<AgentExecutionLog>()
                        .eq(AgentExecutionLog::getConversationId, conversationId)
                        .orderByAsc(AgentExecutionLog::getId)
                        .last("LIMIT 200"))
                .stream().map(this::toVO).toList();
    }

    public List<ExecutionLogVO> recent(int limit) {
        return logMapper.selectList(new LambdaQueryWrapper<AgentExecutionLog>()
                        .orderByDesc(AgentExecutionLog::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream().map(this::toVO).toList();
    }

    public PageResult<ExecutionLogVO> search(String taskId, String conversationId, String agentId,
                                             String step, long pageNo, long size) {
        Page<AgentExecutionLog> page = logMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, size))),
                new LambdaQueryWrapper<AgentExecutionLog>()
                        .eq(taskId != null && !taskId.isBlank(), AgentExecutionLog::getTaskId, taskId)
                        .eq(conversationId != null && !conversationId.isBlank(),
                                AgentExecutionLog::getConversationId, conversationId)
                        .eq(agentId != null && !agentId.isBlank(), AgentExecutionLog::getAgentId, agentId)
                        .eq(step != null && !step.isBlank(), AgentExecutionLog::getStep, step)
                        .orderByDesc(AgentExecutionLog::getId));
        return PageResult.of(page.convert(this::toVO));
    }

    private ExecutionLogVO toVO(AgentExecutionLog entity) {
        ExecutionLogVO vo = new ExecutionLogVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setConversationId(entity.getConversationId());
        vo.setAgentId(entity.getAgentId());
        vo.setStep(entity.getStep());
        vo.setStepName(entity.getStepName());
        vo.setInput(entity.getInput());
        vo.setOutput(entity.getOutput());
        vo.setStatus(entity.getStatus());
        vo.setDurationMs(entity.getDurationMs());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public class TraceStep {

        private final String taskId;
        private final String conversationId;
        private final String agentId;
        private final String step;
        private final String stepName;
        private final String input;
        private final long startNanos = System.nanoTime();
        private boolean ended;

        private TraceStep(String taskId, String conversationId, String agentId, String step,
                          String stepName, String input) {
            this.taskId = taskId;
            this.conversationId = conversationId;
            this.agentId = agentId;
            this.step = step;
            this.stepName = stepName;
            this.input = input;
        }

        public AgentStep success(String output) {
            return finish("success", output);
        }

        public AgentStep failed(String output) {
            return finish("failed", output);
        }

        public AgentStep skipped(String output) {
            return finish("skipped", output);
        }

        private AgentStep finish(String status, String output) {
            if (ended) {
                return null;
            }
            ended = true;
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            AgentExecutionLog entity = new AgentExecutionLog();
            entity.setTaskId(taskId);
            entity.setConversationId(conversationId);
            entity.setAgentId(agentId);
            entity.setStep(step);
            entity.setStepName(stepName);
            entity.setInput(truncate(input, 2000));
            entity.setOutput(truncate(output, 2000));
            entity.setStatus(status);
            entity.setDurationMs((int) durationMs);
            entity.setCreatedAt(LocalDateTime.now());
            try {
                logMapper.insert(entity);
            } catch (Exception e) {
                log.warn("执行日志写入失败: {}", e.getMessage());
            }
            AgentStep stepDto = new AgentStep();
            stepDto.setStep(step);
            stepDto.setStepName(stepName);
            stepDto.setStatus(status);
            stepDto.setDurationMs(durationMs);
            stepDto.setSummary(truncate(output, 180));
            stepDto.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            try {
                webSocketHandler.broadcast(conversationId, AgentEvent.step(taskId, conversationId, stepDto));
            } catch (Exception e) {
                log.debug("执行过程推送失败: {}", e.getMessage());
            }
            return stepDto;
        }
    }
}
