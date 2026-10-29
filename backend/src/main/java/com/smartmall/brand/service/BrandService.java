package com.smartmall.brand.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.brand.dto.BrandVO;
import com.smartmall.brand.entity.Brand;
import com.smartmall.brand.mapper.BrandMapper;
import com.smartmall.common.BizException;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandMapper brandMapper;
    private final ProductMapper productMapper;

    /** 品牌条：按排序返回启用中的品牌，附带在售商品数 */
    public List<BrandVO> list() {
        List<Brand> brands = brandMapper.selectList(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getStatus, 1)
                .orderByAsc(Brand::getSortOrder)
                .orderByAsc(Brand::getId));
        return brands.stream().map(this::toVO).toList();
    }

    /** 品牌详情：不存在或已下架时返回 404 */
    public BrandVO detail(String id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null || brand.getStatus() == null || brand.getStatus() != 1) {
            throw BizException.notFound("品牌不存在或已下架");
        }
        return toVO(brand);
    }

    /** 按品牌名查档案，供商品详情页做联动 */
    public BrandVO byName(String name) {
        Brand brand = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getName, name)
                .eq(Brand::getStatus, 1)
                .last("LIMIT 1"));
        return brand == null ? null : toVO(brand);
    }

    private BrandVO toVO(Brand brand) {
        BrandVO vo = new BrandVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setSlogan(brand.getSlogan());
        vo.setOrigin(brand.getOrigin());
        vo.setFounded(brand.getFounded());
        vo.setPositioning(brand.getPositioning());
        vo.setIntro(brand.getIntro());
        vo.setStory(brand.getStory());
        vo.setHighlight(brand.getHighlight());
        vo.setLogoText(brand.getLogoText());
        vo.setAccent(brand.getAccent());
        vo.setProductCount(productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getBrand, brand.getName())
                .eq(Product::getStatus, 1)));
        return vo;
    }
}