package com.smartmall.user.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PreferenceUpdateRequest {

    @Valid
    private List<PreferenceItem> items = new ArrayList<>();
}
