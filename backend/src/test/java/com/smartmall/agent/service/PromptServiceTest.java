package com.smartmall.agent.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptServiceTest {

    private final PromptService promptService = new PromptService(null);

    @Test
    void exposesDefaultTemplates() {
        assertThat(promptService.list()).hasSize(5);
        assertThat(promptService.text("intent.system")).contains("search_product");
    }

    @Test
    void rendersCategoryPlaceholder() {
        String prompt = promptService.render("slot.system", Map.of("categories", "蓝牙耳机、咖啡机"));
        assertThat(prompt).contains("蓝牙耳机、咖啡机");
        assertThat(prompt).doesNotContain("{{categories}}");
    }
}
