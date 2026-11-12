package com.bytemall.payservice.merchant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    public List<MerchantResponses.Reconciliations> reconciliations(LocalDate bizDate) {
        return new ArrayList<>();
    }
}
