package com.bytemail.userservice.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.dto.UserResponse;
import com.bytemail.userservice.user.entity.UserAddress;
import com.bytemail.userservice.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final UserAddressMapper userAddressMapper;

    // 查询所有地址
    public List<UserResponse.Address> meAddresses(Long userId) {
        List<UserAddress> userAddresses = userAddressMapper.selectList(new QueryWrapper<UserAddress>().eq("user_id", userId));
        return userAddresses.stream().map(AddressService::toResponse).toList();
    }

    // 创建地址
    @Transactional
    public UserResponse.Address createAddress(Long userId, UserResponse.Address address) {
        if (address.isDefault()) {
            clearDefaults(userId);
        }
        UserAddress userAddress = new UserAddress();
        userAddress.setUserId(userId);
        userAddress.setReceiverName(address.receiverName());
        userAddress.setReceiverMobile(address.receiverMobile());
        userAddress.setProvince(address.province());
        userAddress.setCity(address.city());
        userAddress.setDistrict(address.district());
        userAddress.setDetailAddress(address.detailAddress());
        userAddress.setPostalCode(address.postalCode());
        userAddress.setIsDefault(address.isDefault());
        userAddress.setCreatedAt(LocalDateTime.now());
        userAddress.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.insert(userAddress);
        promoteDefaultIfNeeded(userId);
        return toResponse(userAddressMapper.selectById(userAddress.getId()));
    }

    // 更新地址
    @Transactional
    public UserResponse.Address updateAddress(Long userId, Long addressId, UserResponse.Address address) {
        UserAddress userAddress = userAddressMapper.selectById(addressId);
        if (userAddress == null || userId == null || !Objects.equals(userAddress.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "非法访问");
        }
        if (address.isDefault()) {
            clearDefaults(userId);
        }
        userAddress.setId(addressId);
        userAddress.setUserId(userId);
        userAddress.setReceiverName(address.receiverName());
        userAddress.setReceiverMobile(address.receiverMobile());
        userAddress.setProvince(address.province());
        userAddress.setCity(address.city());
        userAddress.setDistrict(address.district());
        userAddress.setDetailAddress(address.detailAddress());
        userAddress.setPostalCode(address.postalCode());
        userAddress.setIsDefault(address.isDefault());
        userAddress.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.updateById(userAddress);
        promoteDefaultIfNeeded(userId);
        return toResponse(userAddressMapper.selectById(addressId));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress userAddress = userAddressMapper.selectById(addressId);
        if (userAddress == null || userId == null || !Objects.equals(userAddress.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "非法访问");
        }
        userAddressMapper.deleteById(addressId);
        promoteDefaultIfNeeded(userId);
    }

    /** 原子切换默认地址：同一事务内取消旧默认值并设置新默认。 */
    @Transactional
    public UserResponse.Address setDefaultAddress(Long userId, Long addressId) {
        UserAddress userAddress = userAddressMapper.selectOne(new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("id", addressId));
        if (userAddress == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        clearDefaults(userId);
        userAddress.setIsDefault(true);
        userAddress.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.updateById(userAddress);
        return toResponse(userAddress);
    }

    private void clearDefaults(Long userId) {
        UserAddress probe = new UserAddress();
        probe.setIsDefault(false);
        userAddressMapper.update(probe, new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("is_default", true));
    }

    /** 保证该用户有地址时至少保留一个默认地址，删除或取消默认后把最早一条提为默认。 */
    private void promoteDefaultIfNeeded(Long userId) {
        Long defaultCount = userAddressMapper.selectCount(new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("is_default", true));
        if (defaultCount != null && defaultCount > 0) {
            return;
        }
        UserAddress first = userAddressMapper.selectOne(new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .orderByAsc("id")
                .last("LIMIT 1"));
        if (first != null) {
            first.setIsDefault(true);
            first.setUpdatedAt(LocalDateTime.now());
            userAddressMapper.updateById(first);
        }
    }

    private static UserResponse.Address toResponse(UserAddress address) {
        return new UserResponse.Address(
                address.getReceiverName(),
                address.getReceiverMobile(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode(),
                address.getIsDefault(),
                address.getId(),
                address.getUserId()
        );
    }
}
