package com.smartmall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.auth.AuthContext;
import com.smartmall.auth.JwtService;
import com.smartmall.cart.entity.CartItem;
import com.smartmall.cart.mapper.CartItemMapper;
import com.smartmall.common.BizException;
import com.smartmall.order.entity.Order;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.service.ProductService;
import com.smartmall.user.dto.AddressRequest;
import com.smartmall.user.dto.LoginRequest;
import com.smartmall.user.dto.LoginResponse;
import com.smartmall.user.dto.PreferenceItem;
import com.smartmall.user.dto.PreferenceUpdateRequest;
import com.smartmall.user.dto.PreferenceVO;
import com.smartmall.user.dto.ProfileVO;
import com.smartmall.user.dto.RegisterRequest;
import com.smartmall.user.dto.UpdateProfileRequest;
import com.smartmall.user.dto.UserVO;
import com.smartmall.user.entity.BrowseHistory;
import com.smartmall.user.entity.Favorite;
import com.smartmall.user.entity.SysUser;
import com.smartmall.user.entity.UserAddress;
import com.smartmall.user.entity.UserPreference;
import com.smartmall.user.mapper.BrowseHistoryMapper;
import com.smartmall.user.mapper.FavoriteMapper;
import com.smartmall.user.mapper.SysUserMapper;
import com.smartmall.user.mapper.UserAddressMapper;
import com.smartmall.user.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final UserAddressMapper addressMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已被注册");
        }
        SysUser user = new SysUser();
        user.setId(com.smartmall.common.Ids.next());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        String token = jwtService.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, UserVO.from(user));
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }
        String token = jwtService.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, UserVO.from(user));
    }

    public ProfileVO profile() {
        String userId = AuthContext.userId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        ProfileVO vo = new ProfileVO();
        vo.setUser(UserVO.from(user));
        vo.setOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId)));
        vo.setFavoriteCount(favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId)));
        vo.setCartCount(cartItemMapper.selectCount(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId)));
        return vo;
    }

    public UserVO updateProfile(UpdateProfileRequest request) {
        SysUser user = userMapper.selectById(AuthContext.userId());
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    public List<UserAddress> addresses() {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, AuthContext.userId())
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreatedAt));
    }

    @Transactional
    public UserAddress createAddress(AddressRequest request) {
        String userId = AuthContext.userId();
        long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || count == 0;
        if (makeDefault) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setId(com.smartmall.common.Ids.next());
        address.setUserId(userId);
        apply(address, request);
        address.setIsDefault(makeDefault ? 1 : 0);
        address.setCreatedAt(LocalDateTime.now());
        addressMapper.insert(address);
        return address;
    }

    @Transactional
    public UserAddress updateAddress(String id, AddressRequest request) {
        UserAddress address = requireOwnAddress(id);
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            clearDefault(address.getUserId());
        }
        apply(address, request);
        if (makeDefault) {
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
        return address;
    }

    @Transactional
    public void deleteAddress(String id) {
        UserAddress address = requireOwnAddress(id);
        addressMapper.deleteById(id);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            List<UserAddress> rest = addresses();
            if (!rest.isEmpty()) {
                UserAddress next = rest.get(0);
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
        }
    }

    @Transactional
    public UserAddress setDefaultAddress(String id) {
        UserAddress address = requireOwnAddress(id);
        clearDefault(address.getUserId());
        address.setIsDefault(1);
        addressMapper.updateById(address);
        return address;
    }

    public List<PreferenceVO> preferences() {
        return preferenceMapper.selectList(new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, AuthContext.userId())
                        .orderByDesc(UserPreference::getWeight))
                .stream().map(p -> new PreferenceVO(p.getPreferenceKey(), p.getPreferenceValue(), p.getWeight()))
                .toList();
    }

    @Transactional
    public List<PreferenceVO> replacePreferences(PreferenceUpdateRequest request) {
        String userId = AuthContext.userId();
        preferenceMapper.delete(new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));
        int limit = Math.min(request.getItems().size(), 20);
        for (int i = 0; i < limit; i++) {
            PreferenceItem item = request.getItems().get(i);
            if (!StringUtils.hasText(item.getKey()) || !StringUtils.hasText(item.getValue())) {
                continue;
            }
            upsertPreference(userId, item.getKey().trim(), item.getValue().trim(),
                    item.getWeight() == null ? BigDecimal.ONE : item.getWeight());
        }
        return preferences();
    }

    /**
     * 供导购 Agent 在对话中学习用户偏好（品类、预算、品牌、场景）。
     */
    @Transactional
    public void learnPreference(String userId, String key, String value, BigDecimal weight) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        upsertPreference(userId, key, value, weight == null ? BigDecimal.ONE : weight);
    }

    public boolean toggleFavorite(String productId) {
        String userId = AuthContext.userId();
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        return true;
    }

    public boolean isFavorite(String productId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, AuthContext.userId()).eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }

    public List<ProductVO> favorites() {
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, AuthContext.userId())
                .orderByDesc(Favorite::getCreatedAt));
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<String> ids = favorites.stream().map(Favorite::getProductId).toList();
        List<ProductVO> products = new ArrayList<>(productService.byIds(ids));
        products.forEach(p -> p.setFavorited(true));
        return products;
    }

    public List<ProductVO> browseHistory(int limit) {
        List<BrowseHistory> histories = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, AuthContext.userId())
                .orderByDesc(BrowseHistory::getCreatedAt)
                .last("LIMIT 60"));
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (BrowseHistory history : histories) {
            ids.add(history.getProductId());
            if (ids.size() >= Math.max(1, Math.min(limit, 30))) {
                break;
            }
        }
        return ids.isEmpty() ? List.of() : productService.byIds(new ArrayList<>(ids));
    }

    private void upsertPreference(String userId, String key, String value, BigDecimal weight) {
        UserPreference existing = preferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId)
                .eq(UserPreference::getPreferenceKey, key));
        if (existing != null) {
            existing.setPreferenceValue(value);
            existing.setWeight(weight);
            existing.setUpdatedAt(LocalDateTime.now());
            preferenceMapper.updateById(existing);
            return;
        }
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setPreferenceKey(key);
        preference.setPreferenceValue(value);
        preference.setWeight(weight);
        preference.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.insert(preference);
    }

    private void clearDefault(String userId) {
        List<UserAddress> list = addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId).eq(UserAddress::getIsDefault, 1));
        for (UserAddress address : list) {
            address.setIsDefault(0);
            addressMapper.updateById(address);
        }
    }

    private UserAddress requireOwnAddress(String id) {
        UserAddress address = addressMapper.selectById(id);
        if (address == null || !address.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("收货地址不存在");
        }
        return address;
    }

    private void apply(UserAddress address, AddressRequest request) {
        address.setReceiver(request.getReceiver());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
    }
}
