package com.bytemall.payservice.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    public List<ReconciliationResponses.ReconciliationRecord> listMerchantRecords(LocalDate bizDate) {
        log.info("Listing merchant reconciliation records for {}", bizDate);
        return List.of();
    }

    public void startJob(ReconciliationRequests.CreateJob request) {
        log.info("Starting reconciliation job for {} on {}", request.channel(), request.bizDate());
    }
}
