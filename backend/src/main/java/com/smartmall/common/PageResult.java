package com.smartmall.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        long pages = size == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(records, total, page, size, pages);
    }
}
