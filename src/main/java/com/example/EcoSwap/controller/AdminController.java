package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.Category;
import com.example.EcoSwap.entity.ExchangeRequest.ExchangeStatus;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.repository.CategoryRepository;
import com.example.EcoSwap.repository.ExchangeRequestRepository;
import com.example.EcoSwap.repository.ProductRepository;
import com.example.EcoSwap.repository.UserRepository;
import com.example.EcoSwap.service.CategoryService;
import com.example.EcoSwap.service.ProductService;
import com.example.EcoSwap.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final ProductService productService;

    public AdminController(UserRepository userRepository, ProductRepository productRepository,
                          CategoryRepository categoryRepository, ExchangeRequestRepository exchangeRequestRepository,
                          UserService userService, CategoryService categoryService,
                          ProductService productService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.exchangeRequestRepository = exchangeRequestRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.productService = productService;
    }

    // ===================== DASHBOARD =====================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Tổng quan - Admin EcoSwap");
        model.addAttribute("pageTitle", "Tổng quan");
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("totalCategories", categoryRepository.count());
        model.addAttribute("totalExchanges", exchangeRequestRepository.count());
        model.addAttribute("activeExchanges", exchangeRequestRepository.countActiveExchanges());
        model.addAttribute("pendingExchanges", exchangeRequestRepository.countByStatus(ExchangeStatus.PENDING));
        model.addAttribute("completedExchanges", exchangeRequestRepository.countByStatus(ExchangeStatus.COMPLETED));
        return "admin/dashboard";
    }

    // ===================== USER MANAGEMENT =====================

    @GetMapping("/users")
    public String listUsers(Model model,
                            @RequestParam(defaultValue = "") String search,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage;

        if (search != null && !search.trim().isEmpty()) {
            userPage = userRepository.findAll(pageable);
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(search.toLowerCase())
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(search.toLowerCase()))
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(search.toLowerCase())))
                    .toList();
            model.addAttribute("users", filtered);
            model.addAttribute("totalPages", 1);
        } else {
            userPage = userRepository.findAll(pageable);
            model.addAttribute("users", userPage.getContent());
            model.addAttribute("totalPages", userPage.getTotalPages());
        }

        model.addAttribute("currentPage", page);
        model.addAttribute("search", search);
        model.addAttribute("totalUsers", userRepository.count());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model,
                           @RequestParam(defaultValue = "0") int page) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return "redirect:/admin/users?error=not_found";
        }

        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> productPage = productRepository.findByUserId(user.getId(), pageable);

        model.addAttribute("user", user);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("productCount", productRepository.findByUserId(user.getId()).size());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalProductPages", productPage.getTotalPages());
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, Authentication authentication) {
        String currentUsername = authentication.getName();
        if (currentUsername.equals(userRepository.findById(id).orElseThrow().getUsername())) {
            return "redirect:/admin/users?error=cannot_disable_self";
        }

        User user = userRepository.findById(id).orElseThrow();
        user.setActive(!user.getActive());
        userRepository.save(user);
        return "redirect:/admin/users?success=status_updated";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, Authentication authentication) {
        String currentUsername = authentication.getName();
        if (currentUsername.equals(userRepository.findById(id).orElseThrow().getUsername())) {
            return "redirect:/admin/users?error=cannot_delete_self";
        }

        userService.deleteUser(id);
        return "redirect:/admin/users?success=deleted";
    }

    // ===================== CATEGORY MANAGEMENT =====================

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/categories";
    }

    @GetMapping("/categories/create")
    public String createCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "admin/category-form";
    }

    @PostMapping("/categories/create")
    public String createCategory(@ModelAttribute Category category,
                                 @RequestParam(required = false) MultipartFile iconFile,
                                 @RequestParam(required = false) String removeIcon) {
        if (removeIcon != null && removeIcon.equals("true")) {
            category.setIcon(null);
        }
        if (iconFile != null && !iconFile.isEmpty()) {
            String iconUrl = uploadIcon(iconFile);
            if (iconUrl != null) {
                category.setIcon(iconUrl);
            }
        }

        categoryService.createCategory(category);
        return "redirect:/admin/categories?success=created";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        Category category = categoryService.getCategoryById(id).orElse(null);
        if (category == null) {
            return "redirect:/admin/categories?error=not_found";
        }
        model.addAttribute("category", category);
        return "admin/category-form";
    }

    @PostMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable Long id,
                               @ModelAttribute Category category,
                               @RequestParam(required = false) MultipartFile iconFile,
                               @RequestParam(required = false) String removeIcon) {
        Category existing = categoryService.getCategoryById(id).orElse(null);
        if (existing == null) {
            return "redirect:/admin/categories?error=not_found";
        }

        existing.setName(category.getName());
        existing.setDescription(category.getDescription());

        if (removeIcon != null && removeIcon.equals("true")) {
            existing.setIcon(null);
        }
        if (iconFile != null && !iconFile.isEmpty()) {
            String iconUrl = uploadIcon(iconFile);
            if (iconUrl != null) {
                existing.setIcon(iconUrl);
            }
        }

        categoryService.updateCategory(existing);
        return "redirect:/admin/categories?success=updated";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            return "redirect:/admin/categories?success=deleted";
        } catch (Exception e) {
            log.error("Error deleting category {}: {}", id, e.getMessage());
            return "redirect:/admin/categories?error=has_products";
        }
    }

    // ===================== PRODUCT MANAGEMENT =====================

    @GetMapping("/products")
    public String listProducts(Model model,
                              @RequestParam(defaultValue = "") String search,
                              @RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 15, Sort.by("createdAt").descending());
        Page<Product> productPage;

        if (search != null && !search.trim().isEmpty()) {
            productPage = productRepository.findByTitleContainingIgnoreCase(search, pageable);
            model.addAttribute("search", search);
        } else if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            productPage = productRepository.findByStatus(status, pageable);
            model.addAttribute("status", status);
        } else {
            productPage = productRepository.findAll(pageable);
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalProducts", productRepository.count());
        return "admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return "redirect:/admin/products?success=deleted";
        } catch (DataIntegrityViolationException e) {
            log.warn("Cannot delete product {} due to FK constraint: {}", id, e.getMessage());
            return "redirect:/admin/products?error=has_exchange_reference";
        }
    }

    @PostMapping("/products/{id}/toggle-status")
    public String toggleProductStatus(@PathVariable Long id) {
        Product product = productService.getProductById(id).orElse(null);
        if (product == null) {
            return "redirect:/admin/products?error=not_found";
        }
        String newStatus = "AVAILABLE".equals(product.getStatus()) ? "SOLD" : "AVAILABLE";
        product.setStatus(newStatus);
        productService.updateProduct(product);
        return "redirect:/admin/products?success=status_updated";
    }

    // ===================== EXCHANGE MANAGEMENT =====================

    @GetMapping("/exchanges")
    public String listExchanges(Model model,
                                @RequestParam(required = false) ExchangeStatus status,
                                @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 15, Sort.by("createdAt").descending());
        Page<com.example.EcoSwap.entity.ExchangeRequest> exchangePage;

        if (status != null) {
            exchangePage = exchangeRequestRepository.findAll(pageable);
            List<com.example.EcoSwap.entity.ExchangeRequest> filtered = exchangePage.getContent().stream()
                    .filter(e -> e.getStatus() == status).toList();
            model.addAttribute("exchanges", filtered);
            model.addAttribute("totalPages", 1);
            model.addAttribute("status", status.name());
        } else {
            exchangePage = exchangeRequestRepository.findAll(pageable);
            model.addAttribute("exchanges", exchangePage.getContent());
            model.addAttribute("totalPages", exchangePage.getTotalPages());
        }

        model.addAttribute("currentPage", page);
        model.addAttribute("totalExchanges", exchangeRequestRepository.count());
        return "admin/exchanges";
    }

    // ===================== STATISTICS API =====================

    @GetMapping("/stats/monthly")
    @ResponseBody
    public java.util.Map<String, Long> monthlyStats() {
        java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("users", userRepository.count());
        stats.put("products", productRepository.count());
        stats.put("exchanges", exchangeRequestRepository.count());
        stats.put("activeExchanges", exchangeRequestRepository.countActiveExchanges());
        return stats;
    }

    // ===================== HELPER =====================

    private String uploadIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = java.util.UUID.randomUUID().toString() + extension;
            java.nio.file.Path path = java.nio.file.Paths.get("uploads", newFilename);
            java.nio.file.Files.copy(file.getInputStream(), path);
            return "/uploads/" + newFilename;
        } catch (Exception e) {
            return null;
        }
    }
}
