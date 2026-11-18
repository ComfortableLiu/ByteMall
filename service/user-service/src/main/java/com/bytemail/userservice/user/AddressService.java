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
        return userAddresses.stream().map(address -> new UserResponse.Address(
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
        )).toList();
    }

    // 创建地址
    @Transactional
    public UserResponse.Address createAddress(Long userId, UserResponse.Address address) {
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
        return new UserResponse.Address(
                userAddress.getReceiverName(),
                userAddress.getReceiverMobile(),
                userAddress.getProvince(),
                userAddress.getCity(),
                userAddress.getDistrict(),
                userAddress.getDetailAddress(),
                userAddress.getPostalCode(),
                userAddress.getIsDefault(),
                userAddress.getId(),
                userAddress.getUserId()
        );
    }

    // 更新地址
    @Transactional
    public UserResponse.Address updateAddress(Long userId, Long addressId, UserResponse.Address address) {
        UserAddress userAddress = userAddressMapper.selectById(addressId);
        if (userAddress == null || userId == null || !Objects.equals(userAddress.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "非法访问");
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
        return new UserResponse.Address(
                userAddress.getReceiverName(),
                userAddress.getReceiverMobile(),
                userAddress.getProvince(),
                userAddress.getCity(),
                userAddress.getDistrict(),
                userAddress.getDetailAddress(),
                userAddress.getPostalCode(),
                userAddress.getIsDefault(),
                userAddress.getId(),
                userAddress.getUserId()
        );
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress userAddress = userAddressMapper.selectById(addressId);
        if (userAddress == null || userId == null || !Objects.equals(userAddress.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "非法访问");
        }
        userAddressMapper.deleteById(addressId);
    }

    @Transactional
    public UserResponse.Address setDefaultAddress(Long userId, Long addressId) {
        UserAddress userAddress = userAddressMapper.selectOne(new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("id", addressId));
        if (userAddress == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        userAddressMapper.updateById(userAddress);
        return new UserResponse.Address(
                userAddress.getReceiverName(),
                userAddress.getReceiverMobile(),
                userAddress.getProvince(),
                userAddress.getCity(),
                userAddress.getDistrict(),
                userAddress.getDetailAddress(),
                userAddress.getPostalCode(),
                userAddress.getIsDefault(),
                userAddress.getId(),
                userAddress.getUserId()
        );
    }
}
