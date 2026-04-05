package com.example.EcoSwap.service;

import com.example.EcoSwap.entity.Product;
import com.example.EcoSwap.repository.ExchangeRequestRepository;
import com.example.EcoSwap.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;

    public ProductService(ProductRepository productRepository, ExchangeRequestRepository exchangeRequestRepository) {
        this.productRepository = productRepository;
        this.exchangeRequestRepository = exchangeRequestRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Page<Product> getAvailableProducts(Pageable pageable) {
        return productRepository.findByStatus("AVAILABLE", pageable);
    }

    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByStatusAndCategoryId("AVAILABLE", categoryId, pageable);
    }

    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        return productRepository.findByStatusAndTitleContainingIgnoreCase("AVAILABLE", keyword, pageable);
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> getProductsByUser(Long userId) {
        return productRepository.findByUserId(userId);
    }

    public List<Product> getProductsByUserAndStatus(Long userId, String status) {
        if (status == null || status.isEmpty() || "ALL".equals(status)) {
            return productRepository.findByUserId(userId);
        }
        return productRepository.findByUserIdAndStatus(userId, status);
    }

    public Optional<Product> getProductByIdForUser(Long id, Long userId) {
        return productRepository.findByIdAndUserId(id, userId);
    }

    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public Product approveProduct(Product product, Double approvedPrice) {
        double finalApprovedPrice = approvedPrice != null
            ? approvedPrice
            : (product.getPrice() == null ? 0D : product.getPrice());

        if (finalApprovedPrice <= 0) {
            throw new RuntimeException("Approved price must be greater than zero");
        }

        product.setApprovedPrice(finalApprovedPrice);
        product.setApprovedAt(java.time.LocalDateTime.now());
        product.setStatus("AVAILABLE");
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
