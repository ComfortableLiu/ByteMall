package com.bytemall.searchservice.product;

import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Profile("!prod")
@RequestMapping("/v1/search")
@RequiredArgsConstructor
public class ProductIndexSyncController {

    private final ProductIndexSyncService productIndexSyncService;

    @GetMapping("/reindex")
    public ApiResponse<ProductIndexSyncService.SyncResult> reindex() throws IOException {
        return ApiResponse.ok(productIndexSyncService.synchronizeAll());
    }
}
