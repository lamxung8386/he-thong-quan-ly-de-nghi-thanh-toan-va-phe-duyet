package vn.fin01.config;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class DemoData {
 @Bean CommandLineRunner seed(JdbcTemplate db, PasswordEncoder encoder) {
  return args -> {
   for(String dep:new String[]{"Hành chính","Kế toán","Kinh doanh","Ban giám đốc"}) db.update("INSERT INTO departments(name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name=?)",dep,dep);
   for(String c:new String[]{"Công tác phí","Văn phòng phẩm","Mua sắm thiết bị","Dịch vụ","Chi phí vận hành","Khác"}) db.update("INSERT INTO categories(name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name=?)",c,c);
   demo(db,encoder,"nguoilap","Nguyễn Văn Lập","lap@example.test","USER",10000000);
   demo(db,encoder,"nguoiduyet","Trần Thị Duyệt","duyet@example.test","APPROVER",100000000);
   demo(db,encoder,"ketoan","Lê Văn Kế Toán","ketoan@example.test","ACCOUNTANT",100000000);
   demo(db,encoder,"admin","Quản trị viên","admin@example.test","ADMIN",100000000);
   demo(db,encoder,"kiemtoan","Kiểm toán viên","kiemtoan@example.test","AUDITOR",100000000);
  };
 }
 private void demo(JdbcTemplate db,PasswordEncoder encoder,String user,String name,String email,String role,long limit) {
  Integer count=db.queryForObject("SELECT COUNT(*) FROM app_users WHERE username=?",Integer.class,user);
  if(count==0) db.update("INSERT INTO app_users(username,password_hash,full_name,email,role,limit_amount) VALUES (?,?,?,?,?,?)",user,encoder.encode("123456"),name,email,role,limit);
 }
}
