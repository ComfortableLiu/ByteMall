package com.bytemall.payservice.reconciliation;

import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
public class InternalController {

    private final InternalService internalService;

    @PostMapping("/payment-callbacks/{channel}")
    public ResponseEntity<Void> start(@PathVariable String channel) {
        // TODO 待实现
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/mock-payments/query")
    public ApiResponse<InternalResponses.MockPayments> mockPaymentsQuery(@Valid @RequestParam InternalRequests.MockPaymentsQuery request) {
        // TODO 待实现
        return ApiResponse.ok(null);
    }
}
