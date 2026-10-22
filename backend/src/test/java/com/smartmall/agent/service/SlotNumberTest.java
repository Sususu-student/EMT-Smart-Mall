package com.smartmall.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotNumberTest {

    @Test
    void parsesCommonChineseAmounts() {
        assertThat(SlotService.parseChineseNumber("三百")).isEqualTo(300);
        assertThat(SlotService.parseChineseNumber("三百五")).isEqualTo(350);
        assertThat(SlotService.parseChineseNumber("三百五十")).isEqualTo(350);
        assertThat(SlotService.parseChineseNumber("一千五")).isEqualTo(1500);
        assertThat(SlotService.parseChineseNumber("一千零五")).isEqualTo(1005);
        assertThat(SlotService.parseChineseNumber("两千")).isEqualTo(2000);
        assertThat(SlotService.parseChineseNumber("一万二")).isEqualTo(12000);
        assertThat(SlotService.parseChineseNumber("十五")).isEqualTo(15);
        assertThat(SlotService.parseChineseNumber("一百二十三")).isEqualTo(123);
    }

    @Test
    void ignoresInvalidText() {
        assertThat(SlotService.parseChineseNumber("")).isNull();
        assertThat(SlotService.parseChineseNumber("防水")).isNull();
    }
}
