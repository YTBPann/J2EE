package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.Category;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.service.CategoryService;
import com.example.EcoSwap.service.ExchangeService;
import com.example.EcoSwap.service.FileUploadService;
import com.example.EcoSwap.service.ProductService;
import com.example.EcoSwap.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // === TRANG DANH SÁCH SẢN PHẨM CÁ NHÂN ===
    @GetMapping("/my-products")
    public String myProducts(Model model, Authentication authentication,
                            @RequestParam(defaultValue = "ALL") String status) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        var products = productService.getProductsByUserAndStatus(currentUser.getId(), status);

        // Đếm theo từng trạng thái
        var allProducts = productService.getProductsByUser(currentUser.getId());
        long countAvailable = allProducts.stream().filter(p -> "AVAILABLE".equals(p.getStatus())).count();
        long countExchanged = allProducts.stream().filter(p -> "EXCHANGED".equals(p.getStatus())).count();
        long countSold = allProducts.stream().filter(p -> "SOLD".equals(p.getStatus())).count();

        model.addAttribute("products", products);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("countAvailable", countAvailable);
        model.addAttribute("countExchanged", countExchanged);
        model.addAttribute("countSold", countSold);
        return "products/my-list";
    }

    // === CHỈNH SỬA SẢN PHẨM - FORM ===
    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = productService.getProductByIdForUser(id, currentUser.getId())
                .orElse(null);

        if (product == null) {
            return "redirect:/my-products?error=not_found";
        }

        // Kiểm tra sản phẩm có đang trong trao đổi không
        boolean isInExchange = exchangeService.isProductInActiveExchange(id);
        if (isInExchange) {
            return "redirect:/my-products?error=cannot_edit_exchange";
        }

        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "products/edit";
    }

    // === CHỈNH SỬA SẢN PHẨM - XỬ LÝ ===
    @PostMapping("/products/{id}/edit")
    public String updateProduct(@PathVariable Long id,
                                @ModelAttribute Product product,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(value = "removeImage", required = false) String removeImage,
                                Authentication authentication,
                                Model model) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product existingProduct = productService.getProductByIdForUser(id, currentUser.getId())
                .orElse(null);

        if (existingProduct == null) {
            return "redirect:/my-products?error=not_found";
        }

        // Kiểm tra sản phẩm có đang trong trao đổi không
        if (exchangeService.isProductInActiveExchange(id)) {
            return "redirect:/my-products?error=cannot_edit_exchange";
        }

        // Cập nhật các trường
        existingProduct.setTitle(product.getTitle());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setCondition(product.getCondition());
        existingProduct.setLocation(product.getLocation());
        existingProduct.setCategory(product.getCategory());

        // Xử lý hình ảnh
        if (removeImage != null && removeImage.equals("true")) {
            existingProduct.setImageUrl(null);
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = fileUploadService.uploadFile(imageFile);
            if (imageUrl != null) {
                existingProduct.setImageUrl(imageUrl);
            }
        }

        productService.updateProduct(existingProduct);
        return "redirect:/my-products?success=updated";
    }

    // === XÓA SẢN PHẨM ===
    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = productService.getProductByIdForUser(id, currentUser.getId())
                .orElse(null);

        if (product == null) {
            return "redirect:/my-products?error=not_found";
        }

        // Kiểm tra sản phẩm có đang trong trao đổi không
        if (exchangeService.isProductInActiveExchange(id)) {
            return "redirect:/my-products?error=cannot_delete_exchange";
        }

        productService.deleteProduct(id);
        return "redirect:/my-products?success=deleted";
    }
    
    @GetMapping("/products")
    public String products(Model model, @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("products", productService.getAvailableProducts(org.springframework.data.domain.PageRequest.of(page, 12)).getContent());
        return "products/list";
    }
    
    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        productService.getProductById(id).ifPresent(product -> {
            model.addAttribute("product", product);
            boolean isInExchange = exchangeService.isProductInActiveExchange(id);
            model.addAttribute("isInActiveExchange", isInExchange);
        });
        return "products/detail";
    }

    @GetMapping("/products/{id}")
    public String productDetailAlt(@PathVariable Long id, Model model) {
        productService.getProductById(id).ifPresent(product -> {
            model.addAttribute("product", product);
            boolean isInExchange = exchangeService.isProductInActiveExchange(id);
            model.addAttribute("isInActiveExchange", isInExchange);
        });
        return "products/detail";
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
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
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
        product.setStatus("AVAILABLE");
        productService.createProduct(product);
        return "redirect:/my-products";
    }
}
