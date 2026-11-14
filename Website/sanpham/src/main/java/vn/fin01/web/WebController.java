package vn.fin01.web;

import vn.fin01.core.FinanceService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
 private final FinanceService f;
 public WebController(FinanceService f){this.f=f;}

 // Chỉ Việt hóa nội dung hiển thị, giữ mã nghiệp vụ gốc để tương thích dữ liệu hiện có.
 private static final Map<String,String> TEN_TRANG_THAI = Map.ofEntries(
  Map.entry("DRAFT","Bản nháp"), Map.entry("SUBMITTED","Chờ phê duyệt"),
  Map.entry("APPROVED","Đã phê duyệt"), Map.entry("REJECTED","Đã từ chối"),
  Map.entry("PAYMENT_FAILED","Chi trả thất bại"), Map.entry("PARTIALLY_PAID","Đã chi một phần"),
  Map.entry("PAID","Đã chi đủ – Chờ đối soát"), Map.entry("NEEDS_REVIEW","Có chênh lệch – Cần xử lý"),
  Map.entry("COMPLETED","Đã hoàn tất"));
 private static final Map<String,String> TEN_VAI_TRO = Map.of(
  "USER","Nhân viên lập đề nghị", "APPROVER","Người phê duyệt",
  "ACCOUNTANT","Nhân viên kế toán", "ADMIN","Quản trị viên", "AUDITOR","Kiểm toán viên");
 private static final Map<String,String> TEN_HANH_DONG = Map.ofEntries(
  Map.entry("CREATE","Tạo mới"), Map.entry("EDIT","Chỉnh sửa"),
  Map.entry("SUBMIT","Gửi phê duyệt"), Map.entry("APPROVED","Phê duyệt"),
  Map.entry("REJECTED","Từ chối"), Map.entry("PAY_SUCCESS","Chi trả đủ"),
  Map.entry("PAY_PARTIAL","Chi trả một phần"), Map.entry("PAY_FAILED","Chi trả thất bại"),
  Map.entry("VOID_PAYMENT","Hủy phiếu chi sai"), Map.entry("SUPPLEMENT_PAYMENT","Bổ sung thông tin chi trả"),
  Map.entry("RECONCILE_OK","Đối soát khớp"), Map.entry("RECONCILE_DIFF","Đối soát chênh lệch"),
  Map.entry("UPLOAD","Tải chứng từ lên"), Map.entry("PROFILE","Cập nhật hồ sơ cá nhân"),
  Map.entry("AVATAR","Đổi ảnh đại diện"), Map.entry("PASSWORD","Đổi mật khẩu"),
  Map.entry("ADMIN_UPDATE","Cập nhật phân quyền"));
 private static final Map<String,String> TEN_DOI_TUONG = Map.of(
  "EXPENSE","Đề nghị thanh toán", "USER","Người dùng",
  "CATEGORY","Loại chi phí", "DEPARTMENT","Phòng ban");
 private static final Map<String,String> TEN_GIAO_DICH = Map.of(
  "SUCCESS","Thành công (mô phỏng)", "FAILED","Thất bại (mô phỏng)",
  "MATCHED","Đã đối soát khớp", "MISMATCHED","Đối soát có chênh lệch");
 private Map<String,Object> actor(Principal p){return f.actor(p.getName());}
 private void onlyRole(Principal p,String... allowed){
  if(!java.util.Arrays.asList(allowed).contains(f.str(actor(p),"role")))
   throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập trang này.");
 }
 @ModelAttribute void common(Model m,Principal p){
  m.addAttribute("statuses",java.util.List.of("DRAFT","SUBMITTED","APPROVED","REJECTED","PAYMENT_FAILED","PARTIALLY_PAID","PAID","NEEDS_REVIEW","COMPLETED"));
  m.addAttribute("roles",java.util.List.of("USER","APPROVER","ACCOUNTANT","ADMIN","AUDITOR"));
  m.addAttribute("statusNames",TEN_TRANG_THAI);
  m.addAttribute("roleNames",TEN_VAI_TRO);
  m.addAttribute("actionNames",TEN_HANH_DONG);
  m.addAttribute("entityNames",TEN_DOI_TUONG);
  m.addAttribute("transactionNames",TEN_GIAO_DICH);
  if(p!=null){Map<String,Object>a=actor(p);m.addAttribute("me",a);m.addAttribute("unread",f.unread(a));}
 }
 @GetMapping("/login") String login(){return "login";}
 @GetMapping("/register") String registerPage(){return "register";}
 @PostMapping("/register") String register(@RequestParam String username,@RequestParam String fullName,@RequestParam String email,@RequestParam String password,RedirectAttributes r){f.register(username,fullName,email,password);r.addFlashAttribute("success","Đăng ký thành công. Bạn có thể đăng nhập.");return "redirect:/login";}
 @GetMapping("/") String home(Model m,Principal p){var a=actor(p);m.addAttribute("stats",f.dashboard(a));m.addAttribute("recent",f.expenses(a,null,null).stream().limit(8).toList());
   String role=f.str(a,"role");
   if("APPROVER".equals(role))m.addAttribute("myWork",f.expenses(a,null,"SUBMITTED").stream().limit(6).toList());
   else if("ACCOUNTANT".equals(role))m.addAttribute("myWork",f.expenses(a,null,null).stream().filter(x->Set.of("APPROVED","PARTIALLY_PAID","PAYMENT_FAILED","PAID","NEEDS_REVIEW").contains(x.get("status"))).limit(6).toList());
   else m.addAttribute("myWork",f.expenses(a,null,null).stream().limit(6).toList());
   return "dashboard";}
 @GetMapping("/expenses") String expenses(Model m,Principal p,@RequestParam(required=false) String q,@RequestParam(required=false) String status){m.addAttribute("items",f.expenses(actor(p),q,status));m.addAttribute("q",q);m.addAttribute("status",status);return "expenses";}
 @GetMapping("/expenses/new") String createForm(Model m,Principal p){onlyRole(p,"USER");m.addAttribute("categories",f.categories());return "new-expense";}
 @PostMapping("/expenses") String create(Principal p,@RequestParam long categoryId,@RequestParam String purpose,@RequestParam String amount,@RequestParam String payee,@RequestParam String supplier,@RequestParam String invoiceNo,@RequestParam String invoiceDate,@RequestParam(value="file",required=false) MultipartFile file,RedirectAttributes r) throws IOException{long eid=f.create(actor(p),categoryId,purpose,amount,payee,supplier,invoiceNo,invoiceDate);if(file!=null&&!file.isEmpty())f.upload(actor(p),eid,file);r.addFlashAttribute("success",file!=null&&!file.isEmpty()?"Đã lưu hồ sơ và chứng từ. Bạn có thể gửi duyệt.":"Đã tạo bản nháp. Hãy tải chứng từ lên rồi gửi duyệt.");return "redirect:/expenses/"+eid;}
 @GetMapping("/expenses/{eid}") String detail(Model m,Principal p,@PathVariable long eid){
   var a=actor(p);var e=f.expense(eid);f.canView(a,e);
   var payments=f.payments(eid);
   var latestSuccess=payments.stream().filter(x->"SUCCESS".equals(x.get("status")) && !Boolean.TRUE.equals(x.get("voided"))).findFirst().orElse(null);
   var ps=f.paymentSummary(eid);
   m.addAttribute("e",e);m.addAttribute("documents",f.documents(eid));m.addAttribute("approvals",f.approvals(eid));
   m.addAttribute("payments",payments);m.addAttribute("latestSuccess",latestSuccess);m.addAttribute("hasPaymentData",ps.get("dataComplete"));
   m.addAttribute("amountMatches",ps.get("amountMatches"));m.addAttribute("payeeMatches",ps.get("namesMatch"));m.addAttribute("reconMatches",ps.get("reconMatches"));
   m.addAttribute("paidTotal",ps.get("paidTotal"));m.addAttribute("remaining",ps.get("remaining"));
   m.addAttribute("recons",f.reconciliations(eid));m.addAttribute("history",f.history(eid));m.addAttribute("categories",f.categories());return "detail";
 }
 @PostMapping("/expenses/{eid}/edit") String edit(Principal p,@PathVariable long eid,@RequestParam long categoryId,@RequestParam String purpose,@RequestParam String amount,@RequestParam String payee,RedirectAttributes r){f.edit(actor(p),eid,categoryId,purpose,amount,payee);r.addFlashAttribute("success","Đã cập nhật hồ sơ");return "redirect:/expenses/"+eid;}
 @PostMapping("/expenses/{eid}/submit") String submit(Principal p,@PathVariable long eid,RedirectAttributes r){f.submit(actor(p),eid);r.addFlashAttribute("success","Hồ sơ đã gửi, đang chờ duyệt");return "redirect:/expenses/"+eid;}
 @PostMapping("/expenses/{eid}/upload") String upload(Principal p,@PathVariable long eid,@RequestParam("file") MultipartFile file,RedirectAttributes r) throws IOException{f.upload(actor(p),eid,file);r.addFlashAttribute("success","Đã lưu chứng từ");return "redirect:/expenses/"+eid;}
 @GetMapping("/documents/{id}") ResponseEntity<byte[]> download(Principal p,@PathVariable long id) throws IOException {
  var d=f.document(actor(p),id);var path=f.documentPath(d);byte[] bytes=Files.readAllBytes(path);
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.str(d,"original_name"),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
 }
 @GetMapping("/approvals") String queueApproval(Model m,Principal p){onlyRole(p,"APPROVER","ADMIN");m.addAttribute("items",f.expenses(actor(p),null,"SUBMITTED"));return "queue-approvals";}
 @PostMapping("/expenses/{eid}/decision") String decision(Principal p,@PathVariable long eid,@RequestParam String decision,@RequestParam(required=false) String reason,RedirectAttributes r){f.decide(actor(p),eid,decision,reason);r.addFlashAttribute("success","Đã ghi nhận quyết định");return "redirect:/expenses/"+eid;}
 @GetMapping("/payments") String queuePayments(Model m,Principal p){onlyRole(p,"ACCOUNTANT","ADMIN");m.addAttribute("approved",f.expenses(actor(p),null,"APPROVED"));m.addAttribute("failed",f.expenses(actor(p),null,"PAYMENT_FAILED"));m.addAttribute("partial",f.expenses(actor(p),null,"PARTIALLY_PAID"));return "queue-payments";}
 @PostMapping("/expenses/{eid}/pay") String pay(Principal p,@PathVariable long eid,@RequestParam String result,
   @RequestParam(required=false) String note,@RequestParam(required=false) String paidAmount,
   @RequestParam(required=false) String paidPayee,@RequestParam(required=false) String paymentMethod,
   @RequestParam(required=false) String evidenceReference,@RequestParam(required=false) String bankName,
   @RequestParam(required=false) String bankAccount,RedirectAttributes r){
   if (!Set.of("SUCCESS","FAILED").contains(result)) throw new IllegalArgumentException("Kết quả thanh toán không hợp lệ");
   boolean success="SUCCESS".equals(result);
   try{f.pay(actor(p),eid,success,note,paidAmount,paidPayee,paymentMethod,evidenceReference,bankName,bankAccount);}
   catch(IllegalArgumentException ex){
      r.addFlashAttribute("paymentError",ex.getMessage());
      r.addFlashAttribute("formPayAmount",paidAmount);r.addFlashAttribute("formPayPayee",paidPayee);
      r.addFlashAttribute("formMethod",paymentMethod);r.addFlashAttribute("formBankName",bankName);
      r.addFlashAttribute("formBankAccount",bankAccount);
      return "redirect:/expenses/"+eid+"#chi-tra";
   }
   var summary=f.paymentSummary(eid);
   String message;
   if (!success) {
      message = "Đã ghi nhận lần mô phỏng thất bại. Không cộng thêm số tiền nào.";
   } else if (((BigDecimal) summary.get("remaining")).signum() == 0) {
      message = "Đã ghi nhận đủ số tiền chi trả mô phỏng. Có thể đối soát.";
   } else {
      message = "Đã ghi nhận chi trả một phần. Còn " +
          ((BigDecimal) summary.get("remaining")).toPlainString() + " đồng; hãy bổ sung tiếp.";
   }
   r.addFlashAttribute("success", message);
   return "redirect:/expenses/"+eid+"#chi-tra";
 }
 @PostMapping("/expenses/{eid}/payment/legacy") String legacyPayment(Principal p,@PathVariable long eid,
  @RequestParam String paidAmount,@RequestParam String paidPayee,@RequestParam String paymentMethod,
  @RequestParam(required=false) String evidenceReference,@RequestParam(required=false) String bankName,
  @RequestParam(required=false) String bankAccount,RedirectAttributes r){
    try{f.supplementLegacyPayment(actor(p),eid,paidAmount,paidPayee,paymentMethod,evidenceReference,bankName,bankAccount);}
    catch(IllegalArgumentException ex){r.addFlashAttribute("paymentError",ex.getMessage());return "redirect:/expenses/"+eid+"#chi-tra";}
    r.addFlashAttribute("success","Đã ghi nhận bổ sung dữ liệu mô phỏng cũ. Số tiền được kiểm tra lại theo hồ sơ.");
    return "redirect:/expenses/"+eid+"#chi-tra";
 }
 @PostMapping("/expenses/{eid}/payment/{paymentId}/void") String voidPayment(Principal p,@PathVariable long eid,
  @PathVariable long paymentId,@RequestParam String reason,RedirectAttributes r){
   try{f.voidPayment(actor(p),eid,paymentId,reason);}
   catch(IllegalArgumentException ex){r.addFlashAttribute("paymentError",ex.getMessage());return "redirect:/expenses/"+eid+"#chi-tra";}
   r.addFlashAttribute("success","Đã hủy bản ghi mô phỏng sai (vẫn lưu lịch sử). Có thể tạo lần chi trả mới.");
   return "redirect:/expenses/"+eid+"#chi-tra";
 }
 @GetMapping("/reconciliation") String queueRecon(Model m,Principal p){onlyRole(p,"ACCOUNTANT","ADMIN");m.addAttribute("paid",f.expenses(actor(p),null,"PAID"));m.addAttribute("review",f.expenses(actor(p),null,"NEEDS_REVIEW"));return "queue-reconciliation";}
 @PostMapping("/expenses/{eid}/reconcile") String reconcile(Principal p,@PathVariable long eid,@RequestParam String result,@RequestParam(required=false) String note,RedirectAttributes r){if (!java.util.Set.of("MATCHED","MISMATCHED").contains(result)) throw new IllegalArgumentException("Kết quả đối soát không hợp lệ");f.reconcile(actor(p),eid,"MATCHED".equals(result),note);r.addFlashAttribute("success","Đã ghi nhận kết quả đối soát");return "redirect:/expenses/"+eid;}
 @GetMapping("/reports") String reports(Model m,Principal p){var a=actor(p);m.addAttribute("summary",f.report(a));m.addAttribute("monthly",f.monthly(a));return "reports";}
 @GetMapping(value="/reports/export",produces="text/csv;charset=UTF-8") ResponseEntity<byte[]> csv(Principal p){var rows=f.report(actor(p));StringBuilder sb=new StringBuilder("\uFEFFTrạng thái,Số lượng,Tổng tiền (đồng)\r\n");for(var r:rows){String code=Objects.toString(r.get("status"),"");sb.append(TEN_TRANG_THAI.getOrDefault(code,code)).append(',').append(r.get("total_count")).append(',').append(r.get("total_amount")).append("\r\n");}return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=fin01_bao_cao.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @GetMapping("/notifications") String notices(Principal p,Model m){m.addAttribute("items",f.notifications(actor(p)));return "notifications";}
 @PostMapping("/notifications/{id}/read") String markRead(Principal p,@PathVariable long id){f.readNotification(actor(p),id);return "redirect:/notifications";}
 @GetMapping("/profile") String profile(){return "profile";}
 @PostMapping("/profile/avatar") String profileAvatar(Principal p,@RequestParam("file") MultipartFile file,RedirectAttributes r) throws IOException {
  try{f.changeAvatar(actor(p),file);r.addFlashAttribute("success","Đã cập nhật ảnh đại diện.");}
  catch(IllegalArgumentException ex){r.addFlashAttribute("avatarError",ex.getMessage());}
  return "redirect:/profile";
 }
 @GetMapping("/avatars/{userId}") ResponseEntity<byte[]> viewAvatar(@PathVariable long userId) throws IOException {
  try{
   Path image=f.avatarPath(f.avatarOwner(userId));
   if(!Files.isRegularFile(image))return ResponseEntity.notFound().build();
   MediaType type=image.getFileName().toString().endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
   return ResponseEntity.ok().contentType(type).header(HttpHeaders.CACHE_CONTROL,"no-store").body(Files.readAllBytes(image));
  }catch(IllegalArgumentException ex){return ResponseEntity.notFound().build();}
 }
 @PostMapping("/profile") String profileUpdate(Principal p,@RequestParam String fullName,@RequestParam String email,RedirectAttributes r){f.profile(actor(p),fullName,email);r.addFlashAttribute("success","Đã cập nhật thông tin cá nhân");return "redirect:/profile";}
 @PostMapping("/profile/password") String changePassword(Principal p,@RequestParam String oldPassword,@RequestParam String newPassword,RedirectAttributes r){f.password(actor(p),oldPassword,newPassword);r.addFlashAttribute("success","Đã đổi mật khẩu");return "redirect:/profile";}
 @GetMapping("/admin") String admin(Model m,Principal p){var a=actor(p);m.addAttribute("users",f.users(a));m.addAttribute("cats",f.categories());m.addAttribute("deps",f.departments());return "admin";}
 @PostMapping("/admin/users/{id}") String changeUser(Principal p,@PathVariable long id,@RequestParam String role,@RequestParam(defaultValue="false") boolean active,@RequestParam String limitAmount,RedirectAttributes r){f.updateUser(actor(p),id,role,active,limitAmount);r.addFlashAttribute("success","Đã cập nhật tài khoản");return "redirect:/admin";}
 @PostMapping("/admin/categories") String category(Principal p,@RequestParam String name,RedirectAttributes r){f.newCategory(actor(p),name);r.addFlashAttribute("success","Đã tạo loại chi");return "redirect:/admin";}
 @PostMapping("/admin/departments") String department(Principal p,@RequestParam String name,RedirectAttributes r){f.newDepartment(actor(p),name);r.addFlashAttribute("success","Đã tạo phòng ban");return "redirect:/admin";}
 @GetMapping("/audit") String audit(Principal p,Model m,@RequestParam(defaultValue="0") int page){
  var a=actor(p);
  int pageSize=25;
  long total=f.auditCount(a);
  int totalPages=(int)Math.max(1L,(total+pageSize-1)/pageSize);
  int current=Math.min(Math.max(0,page),totalPages-1);
  var items=f.auditPage(a,current,pageSize);
  m.addAttribute("items",items);
  m.addAttribute("page",current);
  m.addAttribute("totalPages",totalPages);
  m.addAttribute("totalItems",total);
  m.addAttribute("startItem",total==0?0:(long)current*pageSize+1);
  m.addAttribute("endItem",Math.min(total,(long)(current+1)*pageSize));
  return "audit";
 }
 @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,DataAccessException.class,IOException.class}) String businessError(Exception ex,Model m,jakarta.servlet.http.HttpServletRequest r){m.addAttribute("errorMessage",ex instanceof DataAccessException?"Dữ liệu không hợp lệ hoặc đã tồn tại. Vui lòng kiểm tra lại.":ex.getMessage());return "error-custom";}
}
