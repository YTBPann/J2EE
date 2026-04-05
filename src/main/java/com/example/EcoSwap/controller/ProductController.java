package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.Category;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.service.CategoryService;
import com.example.EcoSwap.service.ExchangeService;
import com.example.EcoSwap.service.FileUploadService;
import com.example.EcoSwap.service.ProductService;
import com.example.EcoSwap.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Controller
public class ProductController {

    private final ProductService productService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final FileUploadService fileUploadService;
    private final ExchangeService exchangeService;

    public ProductController(ProductService productService, UserService userService,
                             CategoryService categoryService, FileUploadService fileUploadService,
                             ExchangeService exchangeService) {
        this.productService = productService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.fileUploadService = fileUploadService;
        this.exchangeService = exchangeService;
    }

    @GetMapping("/my-products")
    public String myProducts(Model model, Authentication authentication,
                             @RequestParam(defaultValue = "ALL") String status) {
        User currentUser = requireCurrentUser(authentication);

        var products = productService.getProductsByUserAndStatus(currentUser.getId(), status);
        var allProducts = productService.getProductsByUser(currentUser.getId());

        long countPendingApproval = allProducts.stream().filter(Product::isPendingApproval).count();
        long countAvailable = allProducts.stream().filter(Product::isAvailable).count();
        long countExchanged = allProducts.stream().filter(p -> "EXCHANGED".equals(p.getStatus())).count();
        long countSold = allProducts.stream().filter(p -> "SOLD".equals(p.getStatus())).count();

        model.addAttribute("products", products);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("countPendingApproval", countPendingApproval);
        model.addAttribute("countAvailable", countAvailable);
        model.addAttribute("countExchanged", countExchanged);
        model.addAttribute("countSold", countSold);
        return "products/my-list";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model, Authentication authentication) {
        User currentUser = requireCurrentUser(authentication);
        Product product = productService.getProductByIdForUser(id, currentUser.getId()).orElse(null);

        if (product == null) {
            return "redirect:/my-products?error=not_found";
        }
        if (exchangeService.isProductInActiveExchange(id)) {
            return "redirect:/my-products?error=cannot_edit_exchange";
        }

        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "products/edit";
    }

    @PostMapping("/products/{id}/edit")
    public String updateProduct(@PathVariable Long id,
                                @ModelAttribute Product product,
                                @RequestParam("categoryId") Long categoryId,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(value = "removeImage", required = false) String removeImage,
                                Authentication authentication) {
        User currentUser = requireCurrentUser(authentication);
        Product existingProduct = productService.getProductByIdForUser(id, currentUser.getId()).orElse(null);

        if (existingProduct == null) {
            return "redirect:/my-products?error=not_found";
        }
        if (exchangeService.isProductInActiveExchange(id)) {
            return "redirect:/my-products?error=cannot_edit_exchange";
        }

        Category category = categoryService.getCategoryById(categoryId)
            .orElseThrow(() -> new RuntimeException("Category not found"));

        boolean wasAvailable = existingProduct.isAvailable();

        existingProduct.setTitle(product.getTitle());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setCondition(product.getCondition());
        existingProduct.setLocation(product.getLocation());
        existingProduct.setCategory(category);

        if ("true".equals(removeImage)) {
            existingProduct.setImageUrl(null);
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = fileUploadService.uploadFile(imageFile);
            if (imageUrl != null) {
                existingProduct.setImageUrl(imageUrl);
            }
        }

        if (wasAvailable) {
            existingProduct.setStatus("PENDING_APPROVAL");
            existingProduct.setApprovedPrice(null);
            existingProduct.setApprovedAt(null);
        }

        productService.updateProduct(existingProduct);
        return "redirect:/my-products?success=" + (wasAvailable ? "updated_pending_approval" : "updated");
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, Authentication authentication) {
        User currentUser = requireCurrentUser(authentication);
        Product product = productService.getProductByIdForUser(id, currentUser.getId()).orElse(null);

        if (product == null) {
            return "redirect:/my-products?error=not_found";
        }
        if (exchangeService.isProductInActiveExchange(id)) {
            return "redirect:/my-products?error=cannot_delete_exchange";
        }

        productService.deleteProduct(id);
        return "redirect:/my-products?success=deleted";
    }

    @GetMapping("/products")
    public String products(Model model, @RequestParam(defaultValue = "0") int page) {
        var productPage = productService.getAvailableProducts(PageRequest.of(page, 12));
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        return "products/list";
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model, Authentication authentication) {
        return loadProductDetail(id, model, authentication);
    }

    @GetMapping("/products/{id}")
    public String productDetailAlt(@PathVariable Long id, Model model, Authentication authentication) {
        return loadProductDetail(id, model, authentication);
    }

    @GetMapping("/products/create")
    public String newProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "products/create";
    }

    @PostMapping("/products/create")
    public String createProduct(@ModelAttribute Product product,
                                @RequestParam("categoryId") Long categoryId,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                Authentication authentication) {
        User currentUser = requireCurrentUser(authentication);
        Category category = categoryService.getCategoryById(categoryId)
            .orElseThrow(() -> new RuntimeException("Category not found"));

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = fileUploadService.uploadFile(imageFile);
            if (imageUrl != null) {
                product.setImageUrl(imageUrl);
            }
        }

        product.setUser(currentUser);
        product.setCategory(category);
        product.setStatus("PENDING_APPROVAL");
        product.setApprovedPrice(null);
        product.setApprovedAt(null);
        productService.createProduct(product);
        return "redirect:/my-products?success=pending_approval";
    }

    private String loadProductDetail(Long id, Model model, Authentication authentication) {
        Optional<Product> productOpt = productService.getProductById(id);
        if (productOpt.isEmpty()) {
            return "redirect:/products?error=not_found";
        }

        Product product = productOpt.get();
        User currentUser = resolveCurrentUser(authentication);
        boolean isAdmin = currentUser != null && currentUser.isAdmin();
        boolean isOwner = currentUser != null && product.getUser().getId().equals(currentUser.getId());

        if (!product.isPubliclyVisible() && !isAdmin && !isOwner) {
            return "redirect:/products?error=not_public";
        }

        model.addAttribute("product", product);
        model.addAttribute("isInActiveExchange", exchangeService.isProductInActiveExchange(id));
        model.addAttribute("isOwnerOrAdmin", isOwner || isAdmin);
        return "products/detail";
    }

    private User requireCurrentUser(Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }
        return currentUser;
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || "anonymousUser".equals(username)) {
            return null;
        }
        return userService.getUserByUsername(username).orElse(null);
    }
}
