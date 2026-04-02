package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.*;
import com.example.EcoSwap.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@Controller
public class ExchangeController {

    private final ExchangeService exchangeService;
    private final ProductService productService;
    private final UserService userService;

    public ExchangeController(ExchangeService exchangeService, ProductService productService,
                             UserService userService) {
        this.exchangeService = exchangeService;
        this.productService = productService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        return userService.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    @GetMapping("/exchanges/history")
    public String exchangeHistory(Model model, @AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page) {
        User currentUser = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, 15);

        // Lấy tất cả trao đổi liên quan đến user (cả gửi và nhận)
        var allExchanges = exchangeService.getAllUserRequests(currentUser.getId());

        // Lọc theo trạng thái nếu có
        List<ExchangeRequest> filtered = allExchanges;
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) {
            try {
                ExchangeRequest.ExchangeStatus es = ExchangeRequest.ExchangeStatus.valueOf(status);
                final ExchangeRequest.ExchangeStatus fs = es;
                filtered = allExchanges.stream()
                        .filter(e -> e.getStatus() == fs)
                        .toList();
            } catch (IllegalArgumentException ignored) {}
        }

        // Phân trang thủ công
        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / 15);
        int from = page * 15;
        int to = Math.min(from + 15, total);
        List<ExchangeRequest> paged = from < total ? filtered.subList(from, to) : List.of();

        // Đếm theo trạng thái
        long countPending = allExchanges.stream().filter(e -> e.getStatus() == ExchangeRequest.ExchangeStatus.PENDING).count();
        long countNegotiating = allExchanges.stream().filter(e -> e.getStatus() == ExchangeRequest.ExchangeStatus.NEGOTIATING).count();
        long countCompleted = allExchanges.stream().filter(e -> e.getStatus() == ExchangeRequest.ExchangeStatus.COMPLETED).count();

        model.addAttribute("exchanges", paged);
        model.addAttribute("totalCount", total);
        model.addAttribute("countPending", countPending);
        model.addAttribute("countNegotiating", countNegotiating);
        model.addAttribute("countCompleted", countCompleted);
        model.addAttribute("selectedStatus", status != null ? status : "ALL");
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentUserId", currentUser.getId());

        return "exchanges/history";
    }

    // ===================== DASHBOARD NHANH =====================

    @GetMapping("/exchanges")
    public String myExchanges(Model model, @AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) Boolean showReceived) {
        User currentUser = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, 10);
        
        Page<ExchangeRequest> sentRequests = exchangeService.getSentRequestsPaged(currentUser.getId(), pageable);
        Page<ExchangeRequest> receivedRequests = exchangeService.getReceivedRequestsPaged(currentUser.getId(), pageable);
        
        // Đếm yêu cầu nhận được đang chờ xử lý (PENDING hoặc NEGOTIATING)
        long pendingReceivedCount = receivedRequests.getContent().stream()
            .filter(r -> r.getStatus() == ExchangeRequest.ExchangeStatus.PENDING 
                      || r.getStatus() == ExchangeRequest.ExchangeStatus.NEGOTIATING)
            .count();
        
        // Xác định tab nào hiển thị mặc định
        boolean showReceivedTab = showReceived != null && showReceived;
        // Nếu có yêu cầu PENDING nhận được và không có param cụ thể -> hiển thị tab nhận được
        if (showReceived == null && pendingReceivedCount > 0) {
            showReceivedTab = true;
        }
        
        model.addAttribute("sentRequests", sentRequests.getContent());
        model.addAttribute("receivedRequests", receivedRequests.getContent());
        model.addAttribute("pendingReceivedCount", pendingReceivedCount);
        model.addAttribute("showReceivedTab", showReceivedTab);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", Math.max(sentRequests.getTotalPages(), receivedRequests.getTotalPages()));
        
        return "exchanges/list";
    }
    
    @GetMapping("/exchange/request/{productId}")
    public String showExchangeRequestForm(@PathVariable Long productId, Model model,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Product requestedProduct = productService.getProductById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        if (requestedProduct.getUser().getId().equals(currentUser.getId())) {
            return "redirect:/products/" + productId + "?error=cannot_exchange_own_product";
        }

        // Check if product is already in an active exchange
        if (exchangeService.isProductInActiveExchange(productId)) {
            return "redirect:/products/" + productId + "?error=product_in_exchange";
        }
        
        // Get user's products that are available and not in active exchange
        List<Product> myProducts = productService.getProductsByUser(currentUser.getId())
            .stream()
            .filter(p -> "AVAILABLE".equals(p.getStatus()))
            .filter(p -> !exchangeService.isProductInActiveExchange(p.getId()))
            .toList();
        
        model.addAttribute("requestedProduct", requestedProduct);
        model.addAttribute("myProducts", myProducts);
        model.addAttribute("currentUser", currentUser);
        
        return "exchanges/request";
    }
    
    @PostMapping("/exchange/request")
    public String createExchangeRequest(@RequestParam Long ownerId,
                                        @RequestParam Long offeredProductId,
                                        @RequestParam Long requestedProductId,
                                        @RequestParam(required = false) String message,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        if (exchangeService.hasExistingRequest(requestedProductId, currentUser.getId())) {
            return "redirect:/products/" + requestedProductId + "?error=already_requested";
        }
        
        exchangeService.createRequest(currentUser.getId(), ownerId, offeredProductId, 
                                       requestedProductId, message);
        
        return "redirect:/exchanges?success=request_created";
    }
    
    @GetMapping("/exchange/{id}")
    public String viewExchange(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestByIdForUser(id, currentUser.getId());
        
        if (requestOpt.isEmpty()) {
            return "redirect:/exchanges?error=not_found";
        }
        
        ExchangeRequest request = requestOpt.get();
        List<ExchangeMessage> messages = exchangeService.getMessages(id);
        
        model.addAttribute("exchange", request);
        model.addAttribute("messages", messages);
        model.addAttribute("currentUser", currentUser);
        
        boolean isOwner = request.getOwner().getId().equals(currentUser.getId());
        model.addAttribute("isOwner", isOwner);
        
        return "exchanges/detail";
    }
    
    @PostMapping("/exchange/{id}/message")
    public String sendMessage(@PathVariable Long id,
                              @RequestParam String content,
                              @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        exchangeService.addMessage(id, currentUser.getId(), content);
        return "redirect:/exchange/" + id;
    }
    
    @PostMapping("/exchange/{id}/accept")
    public String acceptExchange(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestById(id);
        if (requestOpt.isPresent() && requestOpt.get().getOwner().getId().equals(currentUser.getId())) {
            exchangeService.acceptRequest(id);
        }
        
        return "redirect:/exchange/" + id + "?success=accepted";
    }
    
    @PostMapping("/exchange/{id}/reject")
    public String rejectExchange(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestById(id);
        if (requestOpt.isPresent() && requestOpt.get().getOwner().getId().equals(currentUser.getId())) {
            exchangeService.rejectRequest(id);
        }
        
        return "redirect:/exchange/" + id + "?success=rejected";
    }
    
    @PostMapping("/exchange/{id}/complete")
    public String completeExchange(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestByIdForUser(id, currentUser.getId());
        if (requestOpt.isPresent()) {
            exchangeService.completeRequest(id);
        }
        
        return "redirect:/exchange/" + id + "?success=completed";
    }
    
    @PostMapping("/exchange/{id}/cancel")
    public String cancelExchange(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestByIdForUser(id, currentUser.getId());
        if (requestOpt.isPresent()) {
            exchangeService.cancelRequest(id);
        }
        
        return "redirect:/exchanges?success=cancelled";
    }
}
