package com.bytemail.payservice.reconciliation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/internal/payments")
@RequiredArgsConstructor
public class InternalReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/reconciliation-jobs")
    public ResponseEntity<Void> start(@Valid @RequestBody ReconciliationRequests.CreateJob request) {
        reconciliationService.startJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
