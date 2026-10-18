package com.smartmall.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    private String intent;
    private double confidence;
    private String source;
    private String reason;

    public static IntentResult of(String intent, double confidence, String source, String reason) {
        return new IntentResult(intent, confidence, source, reason);
    }
}
