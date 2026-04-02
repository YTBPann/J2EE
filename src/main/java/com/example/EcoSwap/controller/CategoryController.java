package com.example.EcoSwap.controller;

import com.example.EcoSwap.entity.Category;
import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.service.CategoryService;
import com.example.EcoSwap.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
public class CategoryController {
    
    private final CategoryService categoryService;
    private final ProductService productService;
    
    public CategoryController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }
    
    @GetMapping("/categories")
    public String categories(Model model) {
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        return "categories/list";
    }
    
    @GetMapping("/category/{id}")
    public String categoryProducts(@PathVariable Long id, Model model, @RequestParam(defaultValue = "0") int page) {
        Optional<Category> categoryOpt = categoryService.getCategoryById(id);
        if (categoryOpt.isPresent()) {
            Pageable pageable = PageRequest.of(page, 12);
            Page<Product> products = productService.getProductsByCategory(id, pageable);
            model.addAttribute("category", categoryOpt.get());
            model.addAttribute("products", products.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", products.getTotalPages());
        }
        return "categories/detail";
    }
}
