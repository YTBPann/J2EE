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

@Controller
public class HomeController {
    
    private final ProductService productService;
    private final CategoryService categoryService;
    
    public HomeController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }
    
    @GetMapping("/")
    public String home(Model model, @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 12);
        Page<Product> products = productService.getAvailableProducts(pageable);
        List<Category> categories = categoryService.getAllCategories();
        
        model.addAttribute("products", products.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("categories", categories);
        
        return "index";
    }
    
    @GetMapping("/home")
    public String homeRedirect() {
        return "redirect:/";
    }
    
    @GetMapping("/search")
    public String search(@RequestParam String keyword, Model model, @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 12);
        Page<Product> products = productService.searchProducts(keyword, pageable);
        List<Category> categories = categoryService.getAllCategories();
        
        model.addAttribute("products", products.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        
        return "index";
    }
}
