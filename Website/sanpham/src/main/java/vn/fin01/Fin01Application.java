package vn.fin01;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Fin01Application {
    public static void main(String[] args) {
        // Cấu hình múi giờ Việt Nam trước khi JDBC khởi tạo kết nối PostgreSQL.
        // Tránh tình huống một số JVM gửi tên múi giờ cũ "Asia/Saigon".
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        SpringApplication.run(Fin01Application.class, args);
    }
}
