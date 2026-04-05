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
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@ecoswap.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Quản trị viên");
            admin.setPhone("0123456789");
            admin.setAddress("Hà Nội, Việt Nam");
            admin.setActive(true);
            admin.setRole("ADMIN");
            userRepository.save(admin);
        } else if (!"ADMIN".equals(admin.getRole())) {
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }

        if (userRepository.findByUsername("nguoidung1").isEmpty()) {
            User user1 = new User();
            user1.setUsername("nguoidung1");
            user1.setEmail("user1@email.com");
            user1.setPassword(passwordEncoder.encode("123456"));
            user1.setFullName("Người Dùng Một");
            user1.setPhone("0987654321");
            user1.setAddress("TP. Hồ Chí Minh, Việt Nam");
            user1.setActive(true);
            user1.setRole("USER");
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
                Category category = new Category();
                category.setName(name);
                category.setDescription("Danh mục " + name.toLowerCase());
                categoryRepository.save(category);
            }
        }
    }
}
