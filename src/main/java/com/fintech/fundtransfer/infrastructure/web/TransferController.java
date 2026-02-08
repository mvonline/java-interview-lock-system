package com.fintech.fundtransfer.infrastructure.web;

import com.fintech.fundtransfer.application.dto.TransferRequest;
import com.fintech.fundtransfer.application.dto.TransferResponse;
import com.fintech.fundtransfer.application.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest request) {
        TransferResponse response = transferService.transferFunds(request);
        return ResponseEntity.ok(response);
    }
}
