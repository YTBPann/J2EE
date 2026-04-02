package com.example.EcoSwap.config;

import com.example.EcoSwap.entity.Category;
import com.example.EcoSwap.entity.User;
import com.example.EcoSwap.repository.CategoryRepository;
import com.example.EcoSwap.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    
    public DataInitializer(UserRepository userRepository, CategoryRepository categoryRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    @Override
    public void run(String... args) {
        // Tạo hoặc cập nhật admin
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .username("admin")
                    .email("admin@ecoswap.com")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Quản trị viên")
                    .phone("0123456789")
                    .address("Hà Nội, Việt Nam")
                    .active(true)
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
        } else {
            // Đảm bảo admin luôn có role ADMIN (cập nhật nếu thiếu)
            if (!"ADMIN".equals(admin.getRole())) {
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        }
        
        // Tạo user1 nếu chưa tồn tại
        if (userRepository.findByUsername("nguoidung1").isEmpty()) {
            User user1 = User.builder()
                .username("nguoidung1")
                .email("user1@email.com")
                .password(passwordEncoder.encode("123456"))
                .fullName("Người Dùng Một")
                .phone("0987654321")
                .address("TP. Hồ Chí Minh, Việt Nam")
                .active(true)
                .role("USER")
                .build();
            userRepository.save(user1);
        }
        
        if (categoryRepository.count() == 0) {
            String[] categories = {
                "Điện tử - Công nghệ",
                "Đồ gia dụng",
                "Thời trang",
                "Sách - Văn hóa",
                "Thể thao - Dã ngoại",
                "Đồ chơi - Game",
                "Nội thất",
                "Ô tô - Xe máy"
            };
            
            for (String name : categories) {
                Category category = Category.builder()
                    .name(name)
                    .description("Danh mục " + name.toLowerCase())
                    .build();
                categoryRepository.save(category);
            }
        }
    }
}
