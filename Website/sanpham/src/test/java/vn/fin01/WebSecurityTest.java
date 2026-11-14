package vn.fin01;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:fin01web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=always","app.upload-dir=./target/test-uploads"})
@AutoConfigureMockMvc
public class WebSecurityTest {
 @Autowired MockMvc mvc;
 @Test void TC24_loginPagePublic() throws Exception {mvc.perform(get("/login")).andExpect(status().isOk());}
 @Test void TC25_anonymousMustLogin() throws Exception {mvc.perform(get("/expenses")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));}
 @Test void TC26_userCannotOpenAdmin() throws Exception {mvc.perform(get("/admin").with(user("nguoilap").roles("USER"))).andExpect(status().isForbidden());}
 @Test void TC27_postWithoutCsrfForbidden() throws Exception {mvc.perform(post("/profile").with(user("nguoilap").roles("USER")).param("fullName","ABC").param("email","a@b.com")).andExpect(status().isForbidden());}
 @Test void TC28_registrationVisible() throws Exception {mvc.perform(get("/register")).andExpect(status().isOk());}
 @Test void TC43_adminRoleOptionsReadable() throws Exception {
  mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
   .andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên lập đề nghị")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên kế toán")));
 }
 @Test void TC44_auditHasPagination() throws Exception {
  mvc.perform(get("/audit?page=0").with(user("admin").roles("ADMIN")))
   .andExpect(status().isOk())
   .andExpect(model().attributeExists("page","totalItems","totalPages"))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Lịch sử thao tác")));
 }

}
