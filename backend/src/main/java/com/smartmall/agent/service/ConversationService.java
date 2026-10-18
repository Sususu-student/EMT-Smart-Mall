package com.smartmall.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.smartmall.agent.dto.ConversationVO;
import com.smartmall.agent.dto.RecommendedProduct;
import com.smartmall.agent.dto.TurnVO;
import com.smartmall.agent.entity.Conversation;
import com.smartmall.agent.entity.DialogueTurn;
import com.smartmall.agent.mapper.ConversationMapper;
import com.smartmall.agent.mapper.DialogueTurnMapper;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final DialogueTurnMapper turnMapper;

    public Conversation getOrCreate(String userId, String conversationId, String firstMessage) {
        if (conversationId != null && !conversationId.isBlank()) {
            Conversation conversation = requireOwned(conversationId, userId);
            return conversation;
        }
        Conversation conversation = new Conversation();
        conversation.setId(Ids.next());
        conversation.setUserId(userId);
        conversation.setTitle(buildTitle(firstMessage));
        conversation.setSlots("{}");
        conversation.setStatus(1);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    public Conversation requireOwned(String id, String userId) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw BizException.notFound("会话不存在");
        }
        return conversation;
    }

    public List<ConversationVO> list(String userId) {
        List<Conversation> conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, 1)
                .orderByDesc(Conversation::getUpdatedAt)
                .last("LIMIT 50"));
        List<ConversationVO> result = new ArrayList<>();
        for (Conversation conversation : conversations) {
            ConversationVO vo = new ConversationVO();
            vo.setId(conversation.getId());
            vo.setTitle(conversation.getTitle());
            vo.setCreatedAt(conversation.getCreatedAt());
            vo.setUpdatedAt(conversation.getUpdatedAt());
            Long count = turnMapper.selectCount(new LambdaQueryWrapper<DialogueTurn>()
                    .eq(DialogueTurn::getConversationId, conversation.getId())
                    .eq(DialogueTurn::getRole, "user"));
            vo.setTurnCount(count == null ? 0 : count.intValue());
            result.add(vo);
        }
        return result;
    }

    public List<TurnVO> turns(String conversationId, String userId) {
        requireOwned(conversationId, userId);
        List<DialogueTurn> turns = turnMapper.selectList(new LambdaQueryWrapper<DialogueTurn>()
                .eq(DialogueTurn::getConversationId, conversationId)
                .orderByAsc(DialogueTurn::getId)
                .last("LIMIT 120"));
        List<TurnVO> result = new ArrayList<>();
        for (DialogueTurn turn : turns) {
            TurnVO vo = new TurnVO();
            vo.setId(turn.getId());
            vo.setRole(turn.getRole());
            vo.setContent(turn.getContent());
            vo.setIntent(turn.getIntent());
            vo.setTaskId(turn.getTaskId());
            vo.setCreatedAt(turn.getCreatedAt());
            if (turn.getProducts() != null && !turn.getProducts().isBlank()) {
                try {
                    vo.setProducts(JsonUtils.mapper().readValue(turn.getProducts(),
                            new TypeReference<List<RecommendedProduct>>() {
                            }));
                } catch (Exception ignored) {
                    vo.setProducts(List.of());
                }
            }
            result.add(vo);
        }
        return result;
    }

    public DialogueTurn appendTurn(String conversationId, String role, String content, String intent,
                                   String slots, String products, String taskId) {
        DialogueTurn turn = new DialogueTurn();
        turn.setConversationId(conversationId);
        turn.setRole(role);
        turn.setContent(content);
        turn.setIntent(intent);
        turn.setSlots(slots);
        turn.setProducts(products);
        turn.setTaskId(taskId);
        turn.setCreatedAt(LocalDateTime.now());
        turnMapper.insert(turn);
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
        return turn;
    }

    public void saveSlots(String conversationId, String slotsJson) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setSlots(slotsJson);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
    }

    @Transactional
    public void delete(String conversationId, String userId) {
        requireOwned(conversationId, userId);
        turnMapper.delete(new LambdaQueryWrapper<DialogueTurn>()
                .eq(DialogueTurn::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    public List<String> recentContext(String conversationId, int limit) {
        List<DialogueTurn> turns = turnMapper.selectList(new LambdaQueryWrapper<DialogueTurn>()
                .eq(DialogueTurn::getConversationId, conversationId)
                .orderByDesc(DialogueTurn::getId)
                .last("LIMIT " + Math.max(1, limit)));
        List<String> lines = new ArrayList<>();
        for (int i = turns.size() - 1; i >= 0; i--) {
            DialogueTurn turn = turns.get(i);
            lines.add(("user".equals(turn.getRole()) ? "用户: " : "助手: ") + turn.getContent());
        }
        return lines;
    }

    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新的导购会话";
        }
        String title = message.replaceAll("\\s+", " ").trim();
        return title.length() <= 24 ? title : title.substring(0, 24) + "…";
    }
}
