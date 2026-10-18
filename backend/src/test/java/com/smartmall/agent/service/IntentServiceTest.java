package com.smartmall.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentServiceTest {

    private final IntentService intentService = new IntentService(null);

    @Test
    void detectsCompareIntent() {
        var result = intentService.ruleDetect("帮我对比一下A款和B款，哪个好");
        assertThat(result.getIntent()).isEqualTo("compare");
        assertThat(result.getSource()).isEqualTo("rule");
    }

    @Test
    void detectsOrderIntent() {
        assertThat(intentService.ruleDetect("我的订单到哪了").getIntent()).isEqualTo("query_order");
    }

    @Test
    void detectsCartIntent() {
        assertThat(intentService.ruleDetect("把第一款加入购物车").getIntent()).isEqualTo("add_to_cart");
    }

    @Test
    void detectsChitchatIntent() {
        assertThat(intentService.ruleDetect("你好").getIntent()).isEqualTo("chitchat");
    }

    @Test
    void defaultsToSearch() {
        assertThat(intentService.ruleDetect("我想买个跑步用的耳机").getIntent()).isEqualTo("search_product");
    }
}
