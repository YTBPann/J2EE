package com.example.EcoSwap.repository;

import com.example.EcoSwap.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByStatus(String status, Pageable pageable);
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
    List<Product> findByUserId(Long userId);
    Page<Product> findByUserId(Long userId, Pageable pageable);
    Page<Product> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
    List<Product> findByUserIdAndStatus(Long userId, String status);
    Optional<Product> findByIdAndUserId(Long id, Long userId);
}
