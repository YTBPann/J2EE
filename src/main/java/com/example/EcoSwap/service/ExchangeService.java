package com.example.EcoSwap.service;

import com.example.EcoSwap.entity.ExchangeMessage;
import com.example.EcoSwap.entity.ExchangeRequest;
import com.example.EcoSwap.entity.ExchangeRequest.ExchangeStatus;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.repository.ExchangeMessageRepository;
import com.example.EcoSwap.repository.ExchangeRequestRepository;
import com.example.EcoSwap.repository.ProductRepository;
import com.example.EcoSwap.repository.UserRepository;
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

        validateExchangeParticipants(requester, owner, offeredProduct, requestedProduct);
        validateProductEligibilityForExchange(offeredProduct);
        validateProductEligibilityForExchange(requestedProduct);

        if (isProductInActiveExchange(offeredProductId) || isProductInActiveExchange(requestedProductId)) {
            throw new RuntimeException("One of the selected products is already in an active exchange");
        }

        ExchangeRequest exchangeRequest = new ExchangeRequest();
        exchangeRequest.setRequester(requester);
        exchangeRequest.setOwner(owner);
        exchangeRequest.setOfferedProduct(offeredProduct);
        exchangeRequest.setRequestedProduct(requestedProduct);
        exchangeRequest.setMessage(message);
        exchangeRequest.setStatus(ExchangeStatus.PENDING);
        exchangeRequest.setDeliveryConfirmationLevel(0);
        exchangeRequest.setCashAdjustment(null);

        return exchangeRequestRepository.save(exchangeRequest);
    }

    @Transactional
    public ExchangeMessage addMessage(Long exchangeRequestId, Long senderId, String content) {
        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(exchangeRequestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isParticipant = exchangeRequest.getRequester().getId().equals(senderId)
            || exchangeRequest.getOwner().getId().equals(senderId)
            || sender.isAdmin();
        if (!isParticipant) {
            throw new RuntimeException("You are not allowed to join this exchange conversation");
        }

        ExchangeMessage exchangeMessage = new ExchangeMessage();
        exchangeMessage.setExchangeRequest(exchangeRequest);
        exchangeMessage.setSender(sender);
        exchangeMessage.setContent(content);

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

        if (newStatus == ExchangeStatus.ACCEPTED && request.getNormalizedDeliveryLevel() < 1) {
            request.setDeliveryConfirmationLevel(1);
        }

        if (newStatus == ExchangeStatus.COMPLETED) {
            request.setDeliveryConfirmationLevel(4);
            markProductsAsExchanged(request);
        }

        return exchangeRequestRepository.save(request);
    }

    @Transactional
    public ExchangeRequest advanceExchangeWorkflow(Long requestId) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));

        if (request.isTerminal()) {
            return request;
        }
        if (request.getStatus() != ExchangeStatus.ACCEPTED) {
            throw new RuntimeException("Exchange must be accepted before advancing the workflow");
        }

        int currentLevel = request.getNormalizedDeliveryLevel();
        if (currentLevel < 1) {
            currentLevel = 1;
        } else if (currentLevel < 4) {
            currentLevel++;
        }

        request.setDeliveryConfirmationLevel(currentLevel);

        if (currentLevel >= 4) {
            request.setStatus(ExchangeStatus.COMPLETED);
            markProductsAsExchanged(request);
        }

        return exchangeRequestRepository.save(request);
    }

    @Transactional
    public ExchangeRequest evaluateExchangeValue(Long requestId, double tolerancePercent) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));

        double offeredPrice = normalizePrice(request.getOfferedProduct().getEffectivePrice());
        double requestedPrice = normalizePrice(request.getRequestedProduct().getEffectivePrice());
        double difference = requestedPrice - offeredPrice;
        double maxPrice = Math.max(Math.max(offeredPrice, requestedPrice), 1D);
        double diffPercent = Math.abs(difference) / maxPrice * 100;

        if (diffPercent <= tolerancePercent) {
            request.setCashAdjustment(0D);
            request.setStatus(ExchangeStatus.ACCEPTED);
            request.setDeliveryConfirmationLevel(1);
        } else {
            request.setCashAdjustment(difference);
            request.setStatus(ExchangeStatus.NEGOTIATING);
            request.setDeliveryConfirmationLevel(0);
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
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Exchange request not found"));
        if (request.getNormalizedDeliveryLevel() < 4) {
            throw new RuntimeException("Cannot complete exchange before admin finishes workflow");
        }
        return updateStatus(requestId, ExchangeStatus.COMPLETED);
    }

    public boolean isProductInActiveExchange(Long productId) {
        return exchangeRequestRepository.existsActiveExchangeForProduct(productId);
    }

    public List<ExchangeRequest> getActiveExchangesForProduct(Long productId) {
        return exchangeRequestRepository.findActiveExchangesForProduct(productId);
    }

    private void validateExchangeParticipants(User requester, User owner, Product offeredProduct, Product requestedProduct) {
        if (requester.getId().equals(owner.getId())) {
            throw new RuntimeException("You cannot exchange with yourself");
        }
        if (!offeredProduct.getUser().getId().equals(requester.getId())) {
            throw new RuntimeException("Offered product does not belong to requester");
        }
        if (!requestedProduct.getUser().getId().equals(owner.getId())) {
            throw new RuntimeException("Requested product does not belong to owner");
        }
    }

    private void validateProductEligibilityForExchange(Product product) {
        if (!product.isAvailable()) {
            throw new RuntimeException("Only admin-approved products can be exchanged");
        }
        if (product.getEffectivePrice() == null || product.getEffectivePrice() <= 0) {
            throw new RuntimeException("Product must have an admin-approved price before exchange");
        }
    }

    private double normalizePrice(Double value) {
        return value == null ? 0D : value;
    }

    private void markProductsAsExchanged(ExchangeRequest request) {
        Product offeredProduct = request.getOfferedProduct();
        Product requestedProduct = request.getRequestedProduct();

        offeredProduct.setStatus("EXCHANGED");
        requestedProduct.setStatus("EXCHANGED");

        productRepository.save(offeredProduct);
        productRepository.save(requestedProduct);
    }
}
