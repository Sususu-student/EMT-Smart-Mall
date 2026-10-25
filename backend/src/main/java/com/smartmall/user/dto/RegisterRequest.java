package com.smartmall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @Size(max = 30, message = "昵称最多 30 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** 注册角色：USER（买家）/ SELLER（卖家），只能二选一 */
    @Pattern(regexp = "^(USER|SELLER)$", message = "角色只能是买家或卖家")
    private String role = "USER";
}
