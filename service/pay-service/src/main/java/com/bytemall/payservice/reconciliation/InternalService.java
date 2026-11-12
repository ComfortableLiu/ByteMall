package com.bytemall.payservice.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalService {

    public List<InternalResponses.ReconciliationRecord> internal(LocalDate bizDate) {
        log.info("Listing merchant reconciliation records for {}", bizDate);
        return List.of();
    }
}
