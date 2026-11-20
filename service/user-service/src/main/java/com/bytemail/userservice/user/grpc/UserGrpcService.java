package com.bytemail.userservice.user.grpc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.entity.UserAddress;
import com.bytemail.userservice.merchants.MerchantMapper;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import com.bytemail.userservice.user.mapper.UserAddressMapper;
import com.bytemall.user.proto.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserAccountMapper userAccountMapper;
    private final MerchantMapper merchantMapper;
    private final UserAddressMapper userAddressMapper;

    @Transactional(readOnly = true)
    @Override
    public void getUserContext(GetUserContextRequest request, StreamObserver<GetUserContextResponse> responseObserver) {
        UserAccount user = userAccountMapper.selectById(request.getUserId());
        if (user == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException());
            return;
        }

        // 仅ACTIVE商家属于有效绑定，其他状态不向上游暴露商家ID。
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>()
                .eq("owner_user_id", user.getId())
                .eq("status", ACTIVE_STATUS));
        responseObserver.onNext(GetUserContextResponse
                .newBuilder()
                .setUserId(user.getId())
                .setAvatarUrl(emptyIfNull(user.getAvatarUrl()))
                .setNickname(emptyIfNull(user.getNickname()))
                .setStatus(emptyIfNull(user.getStatus()))
                .setMerchantId(merchant == null || merchant.getId() == null ? -1 : merchant.getId())
                .build()
        );
        responseObserver.onCompleted();
    }

    @Transactional(readOnly = true)
    @Override
    public void batchGetUsers(BatchGetUsersRequest request, StreamObserver<BatchGetUsersResponse> responseObserver) {
        // 保持请求顺序并去重，避免重复ID造成重复响应及Map收集异常。
        Set<Long> userIds = new LinkedHashSet<>(request.getUserIdsList());
        BatchGetUsersResponse.Builder response = BatchGetUsersResponse.newBuilder();
        // 批量查询用户和ACTIVE商家绑定，避免列表场景逐条调用造成N+1。
        if (!userIds.isEmpty()) {
            Map<Long, UserAccount> userMap = userAccountMapper.selectByIds(userIds).stream()
                    .filter(user -> user != null && user.getId() != null)
                    .collect(Collectors.toMap(UserAccount::getId, user -> user, (first, second) -> first));
            Map<Long, Merchant> merchantMap = merchantMapper
                    .selectList(new QueryWrapper<Merchant>()
                            .in("owner_user_id", userIds)
                            .eq("status", ACTIVE_STATUS))
                    .stream()
                    .filter(merchant -> merchant != null && merchant.getOwnerUserId() != null)
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
                        .setAvatarUrl(emptyIfNull(user.getAvatarUrl()))
                        .setNickname(emptyIfNull(user.getNickname()))
                        .setStatus(emptyIfNull(user.getStatus()))
                        .setMerchantId(merchant == null || merchant.getId() == null ? -1 : merchant.getId())
                        .build()
                );
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Transactional(readOnly = true)
    @Override
    public void getAddress(GetAddressRequest request, StreamObserver<GetAddressResponse> responseObserver) {
        UserAccount user = userAccountMapper.selectById(request.getUserId());
        if (user == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException());
            return;
        }

        // 地址必须同时匹配地址ID和用户ID，禁止越权读取或退化为用户首个地址。
        UserAddress userAddress = userAddressMapper.selectOne(new QueryWrapper<UserAddress>()
                .eq("id", request.getAddressId())
                .eq("user_id", user.getId()));
        if (userAddress == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("地址不存在")
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(GetAddressResponse
                .newBuilder()
                .setAddressId(userAddress.getId())
                .setUserId(userAddress.getUserId())
                .setReceiverName(emptyIfNull(userAddress.getReceiverName()))
                .setReceiverMobile(emptyIfNull(userAddress.getReceiverMobile()))
                .setProvince(emptyIfNull(userAddress.getProvince()))
                .setCity(emptyIfNull(userAddress.getCity()))
                .setDistrict(emptyIfNull(userAddress.getDistrict()))
                .setDetailAddress(emptyIfNull(userAddress.getDetailAddress()))
                .setPostalCode(emptyIfNull(userAddress.getPostalCode()))
                .setIsDefault(Boolean.TRUE.equals(userAddress.getIsDefault()))
                .build()
        );
        responseObserver.onCompleted();
    }

    @Transactional(readOnly = true)
    @Override
    public void validateMerchant(ValidateMerchantRequest request, StreamObserver<ValidateMerchantResponse> responseObserver) {
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>()
                .eq("owner_user_id", request.getUserId()));
        boolean idMatches = merchant != null
                && merchant.getId() != null
                && merchant.getId() == request.getMerchantId();
        boolean active = merchant != null && ACTIVE_STATUS.equals(merchant.getStatus());

        String reason = "";
        if (merchant == null) {
            reason = "商家不存在";
        } else if (!idMatches) {
            reason = "商家ID与用户绑定不匹配";
        } else if (!active) {
            reason = "商家状态不是ACTIVE";
        }

        responseObserver.onNext(ValidateMerchantResponse
                .newBuilder()
                .setValid(idMatches && active)
                .setMerchantId(merchant == null || merchant.getId() == null ? -1 : merchant.getId())
                .setMerchantStatus(merchant == null ? "" : emptyIfNull(merchant.getStatus()))
                .setReason(reason)
                .build()
        );
        responseObserver.onCompleted();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
