package com.smartmall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 30, message = "昵称最多 30 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @Size(max = 200, message = "头像地址过长")
    private String avatar;
}
