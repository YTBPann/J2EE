package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.ExchangeMessage;
import com.example.EcoSwap.entity.ExchangeRequest;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.service.ExchangeService;
import com.example.EcoSwap.service.ProductService;
import com.example.EcoSwap.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        List<ExchangeRequest> allExchanges = exchangeService.getAllUserRequests(currentUser.getId());

        List<ExchangeRequest> filtered = allExchanges;
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) {
            try {
                ExchangeRequest.ExchangeStatus exchangeStatus = ExchangeRequest.ExchangeStatus.valueOf(status);
                filtered = allExchanges.stream().filter(e -> e.getStatus() == exchangeStatus).toList();
            } catch (IllegalArgumentException ignored) {
            }
        }

        int pageSize = 15;
        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int from = page * pageSize;
        int to = Math.min(from + pageSize, total);
        List<ExchangeRequest> paged = from < total ? filtered.subList(from, to) : List.of();

        long countPending = allExchanges.stream().filter(ExchangeRequest::isAwaitingAdminReview).count();
        long countNegotiating = allExchanges.stream()
            .filter(e -> e.getStatus() == ExchangeRequest.ExchangeStatus.NEGOTIATING
                || e.getStatus() == ExchangeRequest.ExchangeStatus.ACCEPTED)
            .count();
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

    @GetMapping("/exchanges")
    public String myExchanges(Model model, @AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) Boolean showReceived) {
        User currentUser = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, 10);

        Page<ExchangeRequest> sentRequests = exchangeService.getSentRequestsPaged(currentUser.getId(), pageable);
        Page<ExchangeRequest> receivedRequests = exchangeService.getReceivedRequestsPaged(currentUser.getId(), pageable);

        long pendingReceivedCount = receivedRequests.getContent().stream()
            .filter(ExchangeRequest::isAwaitingAdminReview)
            .count();

        boolean showReceivedTab = showReceived != null && showReceived;
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
        if (!requestedProduct.isAvailable()) {
            return "redirect:/products/" + productId + "?error=awaiting_admin_approval";
        }
        if (exchangeService.isProductInActiveExchange(productId)) {
            return "redirect:/products/" + productId + "?error=product_in_exchange";
        }

        List<Product> myProducts = productService.getProductsByUser(currentUser.getId()).stream()
            .filter(Product::isAvailable)
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

        try {
            exchangeService.createRequest(currentUser.getId(), ownerId, offeredProductId, requestedProductId, message);
            return "redirect:/exchanges?success=request_created";
        } catch (RuntimeException ex) {
            String errorCode = "awaiting_admin_approval";
            if (ex.getMessage() != null && ex.getMessage().contains("active exchange")) {
                errorCode = "product_in_exchange";
            }
            return "redirect:/products/" + requestedProductId + "?error=" + errorCode;
        }
    }

    @GetMapping("/exchange/{id}")
    public String viewExchange(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);

        Optional<ExchangeRequest> requestOpt = currentUser.isAdmin()
            ? exchangeService.getRequestById(id)
            : exchangeService.getRequestByIdForUser(id, currentUser.getId());

        if (requestOpt.isEmpty()) {
            return "redirect:/exchanges?error=not_found";
        }

        ExchangeRequest request = requestOpt.get();
        List<ExchangeMessage> messages = exchangeService.getMessages(id);

        boolean isOwner = request.getOwner().getId().equals(currentUser.getId());
        boolean isRequester = request.getRequester().getId().equals(currentUser.getId());

        model.addAttribute("exchange", request);
        model.addAttribute("messages", messages);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("isRequester", isRequester);
        model.addAttribute("isAdmin", currentUser.isAdmin());

        return "exchanges/detail";
    }

    @PostMapping("/exchange/{id}/message")
    public String sendMessage(@PathVariable Long id,
                              @RequestParam String content,
                              @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        try {
            exchangeService.addMessage(id, currentUser.getId(), content);
        } catch (RuntimeException ex) {
            return "redirect:/exchange/" + id + "?error=not_allowed";
        }
        return "redirect:/exchange/" + id;
    }

    @PostMapping("/exchange/{id}/accept")
    public String acceptExchange(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        if (!currentUser.isAdmin()) {
            return "redirect:/exchange/" + id + "?error=admin_only";
        }

        exchangeService.acceptRequest(id);
        return "redirect:/exchange/" + id + "?success=accepted";
    }

    @PostMapping("/exchange/{id}/reject")
    public String rejectExchange(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestById(id);
        if (requestOpt.isEmpty()) {
            return "redirect:/exchanges?error=not_found";
        }

        ExchangeRequest request = requestOpt.get();
        boolean canReject = currentUser.isAdmin() || request.getOwner().getId().equals(currentUser.getId());
        if (!canReject) {
            return "redirect:/exchange/" + id + "?error=not_allowed";
        }

        exchangeService.rejectRequest(id);
        return "redirect:/exchange/" + id + "?success=rejected";
    }

    @PostMapping("/exchange/{id}/complete")
    public String completeExchange(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        Optional<ExchangeRequest> requestOpt = currentUser.isAdmin()
            ? exchangeService.getRequestById(id)
            : exchangeService.getRequestByIdForUser(id, currentUser.getId());

        if (requestOpt.isPresent()) {
            try {
                exchangeService.completeRequest(id);
            } catch (RuntimeException ex) {
                return "redirect:/exchange/" + id + "?error=waiting_admin_workflow";
            }
        }

        return "redirect:/exchange/" + id + "?success=completed";
    }

    @PostMapping("/exchange/{id}/cancel")
    public String cancelExchange(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        Optional<ExchangeRequest> requestOpt = exchangeService.getRequestByIdForUser(id, currentUser.getId());
        if (requestOpt.isEmpty()) {
            return "redirect:/exchanges?error=not_found";
        }

        ExchangeRequest request = requestOpt.get();
        if (!request.getRequester().getId().equals(currentUser.getId())) {
            return "redirect:/exchange/" + id + "?error=not_allowed";
        }

        exchangeService.cancelRequest(id);
        return "redirect:/exchanges?success=cancelled";
    }
}
