# EcoSwap - Dự án Mua Bán và Trao Đổi Đồ Cũ

## Giới thiệu
EcoSwap là nền tảng mua bán và trao đổi đồ cũ trực tuyến, giúp kết nối người mua và người bán một cách dễ dàng và an toàn.

## Công nghệ sử dụng
- **Backend**: Spring Boot 4.0.3, Java 25
- **Frontend**: HTML, CSS, Thymeleaf
- **Database**: MySQL
- **Security**: Spring Security

## Tính năng
- Xem danh sách sản phẩm
- Tìm kiếm sản phẩm theo từ khóa
- Lọc sản phẩm theo danh mục
- Chi tiết sản phẩm
- Đăng ký/Đăng nhập tài khoản
- Đăng sản phẩm mới
- Phân trang sản phẩm

## Cài đặt

### Yêu cầu
- JDK 25+
- MySQL Server
- Maven

### Cấu hình Database
Chỉnh sửa file `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecoswap
spring.datasource.username=root
spring.datasource.password=root
```

### Chạy ứng dụng
```bash
cd EcoSwap
./mvnw spring-boot:run
```

Truy cập: http://localhost:8080

## Tài khoản mặc định
- Username: admin / Password: admin123
- Username: nguoidung1 / Password: 123456

## Cấu trúc dự án
```
src/main/java/com/example/EcoSwap/
├── config/         # Cấu hình Spring
├── controller/     # Các Controller
├── entity/        # Các Entity (User, Product, Category)
├── repository/    # Repository layer
└── service/       # Business logic
```
