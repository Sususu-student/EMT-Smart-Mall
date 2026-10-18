package com.smartmall.user.dto;

import com.smartmall.user.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private String id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
