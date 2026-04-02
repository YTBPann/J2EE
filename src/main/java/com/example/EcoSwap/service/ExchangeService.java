package com.example.EcoSwap.service;

import com.example.EcoSwap.entity.*;
import com.example.EcoSwap.entity.ExchangeRequest.ExchangeStatus;
import com.example.EcoSwap.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ExchangeService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeMessageRepository exchangeMessageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ExchangeService(ExchangeRequestRepository exchangeRequestRepository,
                           ExchangeMessageRepository exchangeMessageRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository) {
        this.exchangeRequestRepository = exchangeRequestRepository;
        this.exchangeMessageRepository = exchangeMessageRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }
    
    public List<ExchangeRequest> getSentRequests(Long userId) {
        return exchangeRequestRepository.findByRequesterId(userId);
    }
    
    public List<ExchangeRequest> getReceivedRequests(Long userId) {
        return exchangeRequestRepository.findByOwnerId(userId);
    }
    
    public Page<ExchangeRequest> getSentRequestsPaged(Long userId, Pageable pageable) {
        return exchangeRequestRepository.findByRequesterId(userId, pageable);
    }
    
    public Page<ExchangeRequest> getReceivedRequestsPaged(Long userId, Pageable pageable) {
        return exchangeRequestRepository.findByOwnerId(userId, pageable);
    }
    
    public List<ExchangeRequest> getAllUserRequests(Long userId) {
        return exchangeRequestRepository.findByRequesterIdOrOwnerId(userId, userId);
    }
    
    public Optional<ExchangeRequest> getRequestById(Long id) {
        return exchangeRequestRepository.findById(id);
    }
    
    public Optional<ExchangeRequest> getRequestByIdForUser(Long id, Long userId) {
        return exchangeRequestRepository.findByIdAndUserId(id, userId, userId);
    }
    
    public boolean hasExistingRequest(Long requestedProductId, Long requesterId) {
        return exchangeRequestRepository.existsByRequestedProductIdAndRequesterId(requestedProductId, requesterId);
    }
    
    @Transactional
    public ExchangeRequest createRequest(Long requesterId, Long ownerId, Long offeredProductId, 
                                         Long requestedProductId, String message) {
        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new RuntimeException("Owner not found"));
        Product offeredProduct = productRepository.findById(offeredProductId)
            .orElseThrow(() -> new RuntimeException("Offered product not found"));
        Product requestedProduct = productRepository.findById(requestedProductId)
            .orElseThrow(() -> new RuntimeException("Requested product not found"));
        
        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
            .requester(requester)
            .owner(owner)
            .offeredProduct(offeredProduct)
            .requestedProduct(requestedProduct)
            .message(message)
            .status(ExchangeStatus.PENDING)
            .build();
        
        return exchangeRequestRepository.save(exchangeRequest);
    }
    
    @Transactional
    public ExchangeMessage addMessage(Long exchangeRequestId, Long senderId, String content) {
        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(exchangeRequestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        ExchangeMessage exchangeMessage = ExchangeMessage.builder()
            .exchangeRequest(exchangeRequest)
            .sender(sender)
            .content(content)
            .build();
        
        if (exchangeRequest.getStatus() == ExchangeStatus.PENDING) {
            exchangeRequest.setStatus(ExchangeStatus.NEGOTIATING);
            exchangeRequestRepository.save(exchangeRequest);
        }
        
        return exchangeMessageRepository.save(exchangeMessage);
    }
    
    public List<ExchangeMessage> getMessages(Long exchangeRequestId) {
        return exchangeMessageRepository.findByExchangeRequestIdOrderByCreatedAtAsc(exchangeRequestId);
    }
    
    @Transactional
    public ExchangeRequest updateStatus(Long requestId, ExchangeStatus newStatus) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));
        
        request.setStatus(newStatus);
        
        if (newStatus == ExchangeStatus.COMPLETED) {
            Product offeredProduct = request.getOfferedProduct();
            Product requestedProduct = request.getRequestedProduct();
            
            offeredProduct.setStatus("EXCHANGED");
            requestedProduct.setStatus("EXCHANGED");
            
            productRepository.save(offeredProduct);
            productRepository.save(requestedProduct);
        }
        
        return exchangeRequestRepository.save(request);
    }
    
    @Transactional
    public ExchangeRequest acceptRequest(Long requestId) {
        return updateStatus(requestId, ExchangeStatus.ACCEPTED);
    }
    
    @Transactional
    public ExchangeRequest rejectRequest(Long requestId) {
        return updateStatus(requestId, ExchangeStatus.REJECTED);
    }
    
    @Transactional
    public ExchangeRequest cancelRequest(Long requestId) {
        return updateStatus(requestId, ExchangeStatus.CANCELLED);
    }
    
    @Transactional
    public ExchangeRequest completeRequest(Long requestId) {
        return updateStatus(requestId, ExchangeStatus.COMPLETED);
    }

    public boolean isProductInActiveExchange(Long productId) {
        return exchangeRequestRepository.existsActiveExchangeForProduct(productId);
    }

    public List<ExchangeRequest> getActiveExchangesForProduct(Long productId) {
        return exchangeRequestRepository.findActiveExchangesForProduct(productId);
    }
}
