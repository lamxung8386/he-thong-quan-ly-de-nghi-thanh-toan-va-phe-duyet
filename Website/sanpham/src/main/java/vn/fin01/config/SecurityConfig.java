package vn.fin01.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean UserDetailsService users(JdbcTemplate db) {
  return username -> db.query("SELECT username,password_hash,role,active FROM app_users WHERE username=?",(rs,n)->User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles(rs.getString("role")).disabled(!rs.getBoolean("active")).build(),username)
   .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Không tìm thấy tài khoản"));
 }
 @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a->a.requestMatchers("/login","/register","/css/**","/js/**","/error").permitAll().anyRequest().authenticated())
    .formLogin(f->f.loginPage("/login").defaultSuccessUrl("/",true).permitAll())
    .logout(l->l.logoutSuccessUrl("/login?logout").permitAll())
    .build();
 }
}
