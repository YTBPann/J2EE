package com.example.EcoSwap.config;

import com.example.EcoSwap.entity.ExchangeRequest.ExchangeStatus;
import com.example.EcoSwap.repository.ExchangeRequestRepository;
import com.example.EcoSwap.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final UserRepository userRepository;

    public GlobalControllerAdvice(ExchangeRequestRepository exchangeRequestRepository,
                                  UserRepository userRepository) {
        this.exchangeRequestRepository = exchangeRequestRepository;
        this.userRepository = userRepository;
    }

    @ModelAttribute
    public void addExchangeNotification(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                long pendingCount = exchangeRequestRepository.countByOwnerIdAndStatusIn(
                    user.getId(),
                    List.of(ExchangeStatus.PENDING, ExchangeStatus.NEGOTIATING)
                );
                model.addAttribute("pendingExchangeCount", pendingCount);
            });
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return "redirect:/?error=upload_too_large";
        }

        if ("/products/create".equals(requestUri)) {
            return "redirect:/products/create?error=image_too_large";
        }

        if (requestUri.matches("^/products/\\d+/edit$")) {
            return "redirect:" + requestUri + "?error=image_too_large";
        }

        if ("/admin/categories/create".equals(requestUri)) {
            return "redirect:/admin/categories/create?error=icon_too_large";
        }

        if (requestUri.matches("^/admin/categories/\\d+/edit$")) {
            return "redirect:" + requestUri + "?error=icon_too_large";
        }

        return "redirect:/?error=upload_too_large";
    }
}
