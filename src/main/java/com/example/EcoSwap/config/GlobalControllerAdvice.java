package com.example.EcoSwap.config;

import com.example.EcoSwap.entity.ExchangeRequest.ExchangeStatus;
import com.example.EcoSwap.repository.ExchangeRequestRepository;
import com.example.EcoSwap.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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
}
