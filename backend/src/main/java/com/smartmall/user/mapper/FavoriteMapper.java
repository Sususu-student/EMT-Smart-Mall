package com.smartmall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmall.user.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
