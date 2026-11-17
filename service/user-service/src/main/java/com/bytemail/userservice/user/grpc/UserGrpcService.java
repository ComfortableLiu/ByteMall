package com.bytemail.userservice.user.grpc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.entity.UserAddress;
import com.bytemail.userservice.user.mapper.MerchantMapper;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import com.bytemail.userservice.user.mapper.UserAddressMapper;
import com.bytemall.user.proto.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private final UserAccountMapper userAccountMapper;
    private final MerchantMapper merchantMapper;
    private final UserAddressMapper userAddressMapper;

    @Transactional
    @Override
    public void getUserContext(GetUserContextRequest request, StreamObserver<GetUserContextResponse> responseObserver) {
        UserAccount user = userAccountMapper.selectById(request.getUserId());
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", user.getId()));
        responseObserver.onNext(GetUserContextResponse
                .newBuilder()
                .setUserId(user.getId())
                .setAvatarUrl(user.getAvatarUrl())
                .setNickname(user.getNickname())
                .setStatus(user.getStatus())
                .setMerchantId(merchant == null ? -1 : merchant.getId())
                .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void batchGetUsers(BatchGetUsersRequest request, StreamObserver<BatchGetUsersResponse> responseObserver) {
        List<Long> userIds = request.getUserIdsList();
        BatchGetUsersResponse.Builder response = BatchGetUsersResponse.newBuilder();
        // 批量查询用户和商家绑定，避免列表场景逐条调用造成 N+1
        if (!userIds.isEmpty()) {
            Map<Long, UserAccount> userMap = userAccountMapper.selectByIds(userIds).stream()
                    .collect(Collectors.toMap(UserAccount::getId, user -> user));
            Map<Long, Merchant> merchantMap = merchantMapper
                    .selectList(new QueryWrapper<Merchant>().in("owner_user_id", userIds))
                    .stream()
                    .collect(Collectors.toMap(Merchant::getOwnerUserId, merchant -> merchant, (first, second) -> first));
            for (Long userId : userIds) {
                UserAccount user = userMap.get(userId);
                if (user == null) {
                    continue;
                }
                Merchant merchant = merchantMap.get(userId);
                response.addUsers(GetUserContextResponse
                        .newBuilder()
                        .setUserId(user.getId())
                        .setAvatarUrl(user.getAvatarUrl() == null ? "" : user.getAvatarUrl())
                        .setNickname(user.getNickname())
                        .setStatus(user.getStatus())
                        .setMerchantId(merchant == null ? -1 : merchant.getId())
                        .build()
                );
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Transactional
    @Override
    public void getAddress(GetAddressRequest request, StreamObserver<GetAddressResponse> responseObserver) {
        UserAccount user = userAccountMapper.selectById(request.getUserId());
        UserAddress userAddress = userAddressMapper.selectOne(new QueryWrapper<UserAddress>().eq("user_id", user.getId()));
        responseObserver.onNext(GetAddressResponse
                .newBuilder()
                .setAddressId(userAddress.getId())
                .setUserId(userAddress.getUserId())
                .setReceiverName(userAddress.getReceiverName())
                .setReceiverMobile(userAddress.getReceiverMobile())
                .setProvince(userAddress.getProvince())
                .setCity(userAddress.getCity())
                .setDistrict(userAddress.getDistrict())
                .setDetailAddress(userAddress.getDetailAddress())
                .setPostalCode(userAddress.getPostalCode())
                .setIsDefault(userAddress.getIsDefault())
                .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void validateMerchant(ValidateMerchantRequest request, StreamObserver<ValidateMerchantResponse> responseObserver) {
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", request.getUserId()));
        responseObserver.onNext(ValidateMerchantResponse
                .newBuilder()
                .setValid(merchant != null && merchant.getId() == request.getMerchantId())
                .setMerchantId(merchant != null ? merchant.getId() : -1)
                .setMerchantStatus(merchant != null ? merchant.getStatus() : null)
                .build()
        );
    }
}
