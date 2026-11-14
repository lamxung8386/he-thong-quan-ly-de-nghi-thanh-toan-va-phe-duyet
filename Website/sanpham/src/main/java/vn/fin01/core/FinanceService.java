package vn.fin01.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FinanceService {
 private final JdbcTemplate db; private final PasswordEncoder encoder; private final Path uploads; private final Path avatarDir;
 public FinanceService(JdbcTemplate db,PasswordEncoder encoder,@Value("${app.upload-dir}") String uploadDir,@Value("${app.avatar-dir:./uploads/avatars}") String avatarLocation){
  this.db=db;this.encoder=encoder;this.uploads=Paths.get(uploadDir).toAbsolutePath().normalize();
  this.avatarDir=Paths.get(avatarLocation).toAbsolutePath().normalize();
 }
 public Map<String,Object> actor(String username){return db.queryForList("SELECT * FROM app_users WHERE username=? AND active=TRUE",username).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Tài khoản không hoạt động"));}
 public long id(Map<String,Object> row){return ((Number)row.get("id")).longValue();}
 public String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
 private boolean is(Map<String,Object> actor,String...roles){return Arrays.asList(roles).contains(str(actor,"role"));}
 private void role(Map<String,Object> actor,String...roles){if(!is(actor,roles))throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền thực hiện thao tác này.");}
 private void condition(boolean test,String error){if(!test)throw new IllegalArgumentException(error);}
 public List<Map<String,Object>> categories(){return db.queryForList("SELECT * FROM categories ORDER BY name");}
 public List<Map<String,Object>> departments(){return db.queryForList("SELECT * FROM departments ORDER BY name");}
 public Map<String,Object> expense(long eid){return db.queryForList("SELECT e.*,u.full_name AS creator_name,c.name AS category_name,u.limit_amount AS creator_limit FROM expenses e JOIN app_users u ON e.created_by=u.id JOIN categories c ON e.category_id=c.id WHERE e.id=?",eid).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy đề nghị"));}
 public void canView(Map<String,Object> a,Map<String,Object> e){if (!(id(a)==((Number)e.get("created_by")).longValue() || is(a,"ADMIN","APPROVER","ACCOUNTANT","AUDITOR"))) throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem đề nghị này.");}
 public List<Map<String,Object>> expenses(Map<String,Object> actor,String search,String status){
  String sql="SELECT e.id,e.code,e.purpose,e.amount,e.status,e.created_at,u.full_name creator_name,c.name category_name FROM expenses e JOIN app_users u ON u.id=e.created_by JOIN categories c ON c.id=e.category_id WHERE 1=1";
  List<Object> args=new ArrayList<>();
  if(is(actor,"USER")){sql+=" AND e.created_by=?";args.add(id(actor));}
  if(search!=null&&!search.isBlank()){sql+=" AND (LOWER(e.code) LIKE ? OR LOWER(e.purpose) LIKE ? OR LOWER(u.full_name) LIKE ?)";for(int i=0;i<3;i++)args.add("%"+search.toLowerCase(Locale.ROOT)+"%");}
  if(status!=null&&!status.isBlank()){sql+=" AND e.status=?";args.add(status);}
  sql+=" ORDER BY e.id DESC";return db.queryForList(sql,args.toArray());
 }
 public List<Map<String,Object>> documents(long eid){return db.queryForList("SELECT * FROM documents WHERE expense_id=? ORDER BY id",eid);}
 public List<Map<String,Object>> approvals(long eid){return db.queryForList("SELECT a.*,u.full_name FROM approvals a JOIN app_users u ON a.approver_id=u.id WHERE a.expense_id=? ORDER BY a.id",eid);}
 public List<Map<String,Object>> payments(long eid){return db.queryForList("SELECT p.*,u.full_name FROM payments p JOIN app_users u ON p.payer_id=u.id WHERE p.expense_id=? ORDER BY p.id DESC",eid);}
 public List<Map<String,Object>> reconciliations(long eid){return db.queryForList("SELECT r.*,u.full_name FROM reconciliations r JOIN app_users u ON r.reconciled_by=u.id WHERE r.expense_id=? ORDER BY r.id DESC",eid);}
 public List<Map<String,Object>> history(long eid){return db.queryForList("SELECT l.*,COALESCE(u.full_name,'Hệ thống') actor_name FROM audit_logs l LEFT JOIN app_users u ON u.id=l.actor_id WHERE l.entity_type='EXPENSE' AND l.entity_id=? ORDER BY l.id DESC",eid);}
 private long insert(String sql,Object...args){KeyHolder holder=new GeneratedKeyHolder();db.update(conn->{PreparedStatement ps=conn.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},holder);Number key=holder.getKey();if(key==null)throw new IllegalStateException("Không lấy được ID bản ghi");return key.longValue();}
 private void log(Map<String,Object> a,String type,long eid,String action,String detail){db.update("INSERT INTO audit_logs(actor_id,entity_type,entity_id,action,detail) VALUES (?,?,?,?,?)",id(a),type,eid,action,detail);}
 private void notifyRoles(String role,String message){db.update("INSERT INTO notifications(recipient_id,message) SELECT id,? FROM app_users WHERE role=? AND active=TRUE",message,role);}
 private void notifyUser(long uid,String message){db.update("INSERT INTO notifications(recipient_id,message) VALUES (?,?)",uid,message);}
 private BigDecimal amount(String s){try{BigDecimal v=new BigDecimal(s);condition(v.signum()>0,"Số tiền phải lớn hơn 0");condition(v.scale()<=2,"Số tiền chỉ được có tối đa 2 chữ số thập phân");return v;}catch(NumberFormatException ex){throw new IllegalArgumentException("Số tiền không hợp lệ");}}
 private String required(String s,String field,int max){condition(s!=null&&!s.isBlank(),"Chưa nhập "+field);s=s.trim();condition(s.length()<=max,field+" quá dài");return s;}
 private void validateLimit(Map<String,Object> a,BigDecimal value){condition(value.compareTo((BigDecimal)a.get("limit_amount"))<=0,"Số tiền vượt hạn mức của người lập");}
 @Transactional public long create(Map<String,Object> a,long category,String purpose,String amount,String payee,String supplier,String invoice,String invoiceDate){
  role(a,"USER");String p=required(purpose,"mục đích chi",1000),pay=required(payee,"người nhận",200);
  String s=required(supplier,"nhà cung cấp",200).toLowerCase(Locale.ROOT);String inv=required(invoice,"số hóa đơn",100).toLowerCase(Locale.ROOT);
  BigDecimal money=amount(amount);condition(db.queryForObject("SELECT COUNT(*) FROM categories WHERE id=?",Integer.class,category)>0,"Loại chi không tồn tại");
  validateLimit(a,money);LocalDate day;try{day=LocalDate.parse(required(invoiceDate,"ngày hóa đơn",20));}catch(Exception ex){throw new IllegalArgumentException("Ngày hóa đơn chưa đúng định dạng");}
  condition(db.queryForObject("SELECT COUNT(*) FROM expenses WHERE supplier=? AND invoice_no=?",Integer.class,s,inv)==0,"Trùng hóa đơn: đã tồn tại nhà cung cấp và số hóa đơn này");
  String code="DN-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  long eid;try{eid=insert("INSERT INTO expenses(code,created_by,category_id,purpose,amount,payee,supplier,invoice_no,invoice_date) VALUES (?,?,?,?,?,?,?,?,?)",code,id(a),category,p,money,pay,s,inv,java.sql.Date.valueOf(day));}
  catch(org.springframework.dao.DuplicateKeyException ex){throw new IllegalArgumentException("Hóa đơn đã tồn tại hoặc mã đề nghị trùng");}
  log(a,"EXPENSE",eid,"CREATE","Tạo bản nháp "+code);return eid;
 }
 @Transactional public void edit(Map<String,Object> a,long eid,long category,String purpose,String amount,String payee){
  Map<String,Object> e=expense(eid);condition(id(a)==((Number)e.get("created_by")).longValue(),"Chỉ người lập được sửa đề nghị");
  condition(str(e,"status").equals("DRAFT")||str(e,"status").equals("REJECTED"),"Đề nghị đã gửi hoặc kết thúc không thể sửa");
  BigDecimal money=amount(amount);validateLimit(a,money);
  condition(db.queryForObject("SELECT COUNT(*) FROM categories WHERE id=?",Integer.class,category)>0,"Loại chi không tồn tại");
  db.update("UPDATE expenses SET category_id=?,purpose=?,amount=?,payee=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",category,required(purpose,"mục đích chi",1000),money,required(payee,"người nhận",200),eid);
  log(a,"EXPENSE",eid,"EDIT","Cập nhật nội dung bản nháp");
 }
 @Transactional public void submit(Map<String,Object> a,long eid){
  Map<String,Object> e=expense(eid);condition(id(a)==((Number)e.get("created_by")).longValue(),"Chỉ người lập được gửi đề nghị");
  condition(is(a,"USER"),"Chỉ người lập được gửi đề nghị");
  condition(str(e,"status").equals("DRAFT")||str(e,"status").equals("REJECTED"),"Trạng thái không cho phép gửi");
  condition(db.queryForObject("SELECT COUNT(*) FROM documents WHERE expense_id=?",Integer.class,eid)>0,"Cần tải ít nhất một chứng từ trước khi gửi");
  validateLimit(a,(BigDecimal)e.get("amount"));
  condition(db.update("UPDATE expenses SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('DRAFT','REJECTED')",eid)==1,"Hồ sơ đã thay đổi, vui lòng tải lại");
  log(a,"EXPENSE",eid,"SUBMIT","Gửi hồ sơ chờ duyệt");notifyRoles("APPROVER","Có đề nghị "+str(e,"code")+" chờ phê duyệt");
 }
 @Transactional public void decide(Map<String,Object> a,long eid,String decision,String reason){
  role(a,"APPROVER");Map<String,Object> e=expense(eid);
  condition(id(a)!=((Number)e.get("created_by")).longValue(),"Người lập không được tự phê duyệt");
  condition(str(e,"status").equals("SUBMITTED"),"Chỉ hồ sơ chờ duyệt mới được xử lý");
  condition(decision.equals("APPROVED")||decision.equals("REJECTED"),"Quyết định không hợp lệ");
  if(decision.equals("REJECTED"))reason=required(reason,"lý do từ chối",1000);else reason=reason==null?"":reason.trim();
  String newStatus=decision.equals("APPROVED")?"APPROVED":"REJECTED";
  condition(db.update("UPDATE expenses SET status=?,approved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='SUBMITTED'",newStatus,eid)==1,"Hồ sơ đã được duyệt bởi người khác");
  db.update("INSERT INTO approvals(expense_id,approver_id,decision,reason) VALUES (?,?,?,?)",eid,id(a),decision,reason);
  log(a,"EXPENSE",eid,decision,reason);notifyUser(((Number)e.get("created_by")).longValue(),"Hồ sơ "+str(e,"code")+" đã "+(decision.equals("APPROVED")?"được duyệt":"bị từ chối"));
  if(decision.equals("APPROVED"))notifyRoles("ACCOUNTANT","Hồ sơ "+str(e,"code")+" chờ chi trả");
 }
 private String normalizePerson(String value){
  return value==null?"":value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
 }
 private List<Map<String,Object>> activeSuccessfulPayments(long eid){
  return db.queryForList("SELECT * FROM payments WHERE expense_id=? AND status='SUCCESS' AND voided=FALSE ORDER BY id",eid);
 }
 private BigDecimal sumRecorded(List<Map<String,Object>> rows){
  BigDecimal total=BigDecimal.ZERO;
  for(Map<String,Object> pay:rows)if(pay.get("paid_amount")!=null)total=total.add((BigDecimal)pay.get("paid_amount"));
  return total;
 }
 /** Dùng cùng kết quả ở service, giao diện và bộ test để tránh tổng tính sai. */
 public Map<String,Object> paymentSummary(long eid){
  var e=expense(eid);var rows=activeSuccessfulPayments(eid);
  BigDecimal approved=(BigDecimal)e.get("amount"),total=sumRecorded(rows);
  boolean completeData=!rows.isEmpty();boolean namesMatch=!rows.isEmpty();
  for(var r:rows){
    if(r.get("paid_amount")==null || str(r,"paid_payee").isBlank())completeData=false;
    if(!normalizePerson(str(r,"paid_payee")).equals(normalizePerson(str(e,"payee"))))namesMatch=false;
  }
  BigDecimal remaining=approved.subtract(total);
  Map<String,Object> summary=new HashMap<>();
  summary.put("paidTotal",total);summary.put("remaining",remaining);
  summary.put("dataComplete",completeData);summary.put("namesMatch",namesMatch);
  summary.put("amountMatches",completeData&&remaining.compareTo(BigDecimal.ZERO)==0);
  summary.put("reconMatches",completeData&&namesMatch&&remaining.compareTo(BigDecimal.ZERO)==0);
  summary.put("activeCount",rows.size());
  return summary;
 }
 private void lockExpense(long eid){
  // Khóa hàng hồ sơ trước khi tính tổng, tránh hai kế toán cộng tiền cùng lúc.
  db.queryForMap("SELECT id FROM expenses WHERE id=? FOR UPDATE",eid);
 }
 @Transactional public void pay(Map<String,Object> a,long eid,boolean success,String note,String paidAmount,
         String paidPayee,String paymentMethod,String evidenceReference,String bankName,String bankAccount){
  role(a,"ACCOUNTANT");lockExpense(eid);Map<String,Object> e=expense(eid);
  condition(id(a)!=((Number)e.get("created_by")).longValue(),"Người chi không được là người lập");
  List<Map<String,Object>> approved=db.queryForList("SELECT approver_id FROM approvals WHERE expense_id=? AND decision='APPROVED' ORDER BY id DESC",eid);
  condition(!approved.isEmpty(),"Hồ sơ chưa được phê duyệt");
  condition(id(a)!=((Number)approved.get(0).get("approver_id")).longValue(),"Người chi không được là người duyệt");
  condition(Set.of("APPROVED","PARTIALLY_PAID","PAYMENT_FAILED").contains(str(e,"status")),"Hồ sơ đã thanh toán đủ hoặc chưa được phép chi trả");
  var earlier=activeSuccessfulPayments(eid);
  // Giao dịch cũ thiếu số tiền hoặc ghi nhận sai người nhận phải được xử lý trước.
  for(var old:earlier)condition(old.get("paid_amount")!=null&&normalizePerson(str(old,"paid_payee")).equals(normalizePerson(str(e,"payee"))),
   "Đã có giao dịch cũ thiếu/sai dữ liệu. Hãy hủy bản ghi mô phỏng sai rồi lập lại trước khi bổ sung tiền.");
  BigDecimal already=sumRecorded(earlier),remaining=((BigDecimal)e.get("amount")).subtract(already);
  condition(remaining.signum()>0,"Hồ sơ đã được ghi nhận đủ số tiền");
  BigDecimal recorded=null;String recipient=null,method=null,bank=null,account=null;
  if(success){
    recorded=amount(required(paidAmount,"số tiền chi trả",30));
    condition(recorded.precision()<=18,"Số tiền vượt kích thước lưu trữ");
    condition(recorded.compareTo(remaining)<=0,"Số tiền nhập vượt số tiền còn phải chi " + remaining.toPlainString() + " VND. Vui lòng nhập lại.");
    recipient=required(paidPayee,"người nhận",200);
    condition(normalizePerson(recipient).equals(normalizePerson(str(e,"payee"))),
      "Tên người nhận không trùng với hồ sơ đã duyệt ("+str(e,"payee")+"). Vui lòng nhập lại.");
    method=required(paymentMethod,"phương thức thanh toán",30);
    condition(Set.of("CASH","TRANSFER").contains(method),"Phương thức thanh toán không hợp lệ");
    if("TRANSFER".equals(method)){
      bank=required(bankName,"tên ngân hàng ảo",100);
      account=required(bankAccount,"số tài khoản ngân hàng ảo",24).replace(" ","");
      condition(account.matches("[0-9]{6,20}"),"Số tài khoản ngân hàng ảo phải gồm 6–20 chữ số");
    }
    // Tiền mặt không thu thập, không lưu tài khoản ngân hàng.
  }
  String ev=evidenceReference==null?"":evidenceReference.trim();
  condition(ev.length()<=100,"Mã chứng từ quá dài");
  String ref="SIM-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
  db.update("INSERT INTO payments(expense_id,payer_id,reference_code,status,note,paid_amount,paid_payee,payment_method,evidence_reference,bank_name,bank_account) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
    eid,id(a),ref,success?"SUCCESS":"FAILED",note==null?"":note.trim(),recorded,recipient,method,ev,bank,account);
  BigDecimal newTotal=success?already.add(recorded):already;
  String newStatus=success?(newTotal.compareTo((BigDecimal)e.get("amount"))==0?"PAID":"PARTIALLY_PAID")
    :(already.signum()>0?"PARTIALLY_PAID":"PAYMENT_FAILED");
  condition(db.update("UPDATE expenses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=?",newStatus,eid,str(e,"status"))==1,"Trạng thái hồ sơ đã thay đổi");
  String details=ref+"; số tiền="+Objects.toString(recorded,"0")+"; tổng đã ghi nhận="+newTotal+"; người nhận="+Objects.toString(recipient,"")+"; phương thức="+Objects.toString(method,"");
  log(a,"EXPENSE",eid,success?("PAID".equals(newStatus)?"PAY_SUCCESS":"PAY_PARTIAL"):"PAY_FAILED",details);
  String notification=success?("PAID".equals(newStatus)?"đã ghi nhận đủ "+newTotal+" VND (mô phỏng), chờ đối soát":"đã ghi nhận một phần "+recorded+" VND (mô phỏng), còn "+((BigDecimal)e.get("amount")).subtract(newTotal)+" VND"):"mô phỏng chi trả thất bại";
  notifyUser(((Number)e.get("created_by")).longValue(),"Hồ sơ "+str(e,"code")+": "+notification);
 }
 /** Chỉ hủy một bản ghi mô phỏng sai, giữ nguyên lịch sử để truy vết. */
 @Transactional public void voidPayment(Map<String,Object> a,long eid,long paymentId,String reason){
  role(a,"ACCOUNTANT");String why=required(reason,"lý do hủy bản ghi sai",500);
  lockExpense(eid);var e=expense(eid);
  condition(Set.of("PAID","PARTIALLY_PAID","NEEDS_REVIEW").contains(str(e,"status")),"Không thể hủy bản ghi ở trạng thái này");
  var rows=activeSuccessfulPayments(eid);
  condition(!rows.isEmpty(),"Không có giao dịch chi trả để hủy");
  var last=rows.get(rows.size()-1);
  condition(id(last)==paymentId,"Chỉ được hủy giao dịch mô phỏng thành công mới nhất");
  condition(id(a)==((Number)last.get("payer_id")).longValue(),"Chỉ kế toán đã ghi nhận giao dịch được hủy bản ghi sai");
  condition(db.update("UPDATE payments SET voided=TRUE,void_reason=? WHERE id=? AND voided=FALSE",why,paymentId)==1,"Giao dịch đã bị hủy");
  var rest=activeSuccessfulPayments(eid);
  BigDecimal total=sumRecorded(rest),approved=(BigDecimal)e.get("amount");
  String newStatus=rest.isEmpty()?"APPROVED":(total.compareTo(approved)==0?"PAID":"PARTIALLY_PAID");
  db.update("UPDATE expenses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",newStatus,eid);
  log(a,"EXPENSE",eid,"VOID_PAYMENT","Hủy bản ghi mô phỏng "+str(last,"reference_code")+"; lý do: "+why);
  notifyUser(((Number)e.get("created_by")).longValue(),"Kế toán đã hủy bản ghi chi trả mô phỏng sai của "+str(e,"code")+" để ghi nhận lại");
 }
 @Transactional public void supplementLegacyPayment(Map<String,Object> actor,long eid,String amountRecorded,String payeeRecorded,String methodRecorded,String evidenceReference,String bankName,String bankAccount){
  role(actor,"ACCOUNTANT");lockExpense(eid);Map<String,Object> e=expense(eid);
  condition(Set.of("PAID","NEEDS_REVIEW").contains(str(e,"status")),"Hồ sơ không trong trạng thái chờ đối soát");
  List<Map<String,Object>> list=activeSuccessfulPayments(eid);
  condition(!list.isEmpty(),"Chưa có lần chi trả thành công để bổ sung");
  Map<String,Object> payment=list.get(list.size()-1);
  condition(id(actor)==((Number)payment.get("payer_id")).longValue(),"Chỉ kế toán từng ghi nhận giao dịch được bổ sung dữ liệu");
  condition(payment.get("paid_amount")==null,"Đã có số tiền; hãy dùng chức năng hủy bản ghi sai nếu cần");
  BigDecimal amt=amount(required(amountRecorded,"số tiền kế toán ghi nhận",30));
  condition(amt.precision()<=18,"Số tiền vượt kích thước lưu trữ");
  condition(amt.compareTo((BigDecimal)e.get("amount"))<=0,"Số tiền vượt đề nghị được duyệt");
  String recv=required(payeeRecorded,"người nhận ghi nhận",200);
  condition(normalizePerson(recv).equals(normalizePerson(str(e,"payee"))),"Tên người nhận không khớp hồ sơ được duyệt. Vui lòng nhập lại.");
  String method=required(methodRecorded,"phương thức chi",30);
  condition(Set.of("TRANSFER","CASH").contains(method),"Phương thức chi không hợp lệ");
  String bank=null,account=null;
  if("TRANSFER".equals(method)){
    bank=required(bankName,"tên ngân hàng ảo",100);
    account=required(bankAccount,"số tài khoản ngân hàng ảo",24).replace(" ","");
    condition(account.matches("[0-9]{6,20}"),"Số tài khoản ngân hàng ảo phải gồm 6–20 chữ số");
  }
  String ev=evidenceReference==null?"":evidenceReference.trim();condition(ev.length()<=100,"Mã chứng từ quá dài");
  condition(db.update("UPDATE payments SET paid_amount=?,paid_payee=?,payment_method=?,evidence_reference=?,bank_name=?,bank_account=? WHERE id=? AND paid_amount IS NULL",
    amt,recv,method,ev,bank,account,id(payment))==1,"Dữ liệu giao dịch đã có người khác bổ sung");
  var totals=paymentSummary(eid);
  String status=((BigDecimal)totals.get("remaining")).signum()>0?"PARTIALLY_PAID":"PAID";
  db.update("UPDATE expenses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,eid);
  log(actor,"EXPENSE",eid,"SUPPLEMENT_PAYMENT","Bổ sung dữ liệu mô phỏng của "+str(payment,"reference_code")+": "+amt+" VND, người nhận: "+recv);
  notifyUser(((Number)e.get("created_by")).longValue(),"Kế toán đã bổ sung dữ liệu chi trả mô phỏng hồ sơ "+str(e,"code")+" để đối soát");
 }
 @Transactional public void reconcile(Map<String,Object> a,long eid,boolean matched,String note){
  role(a,"ACCOUNTANT");lockExpense(eid);Map<String,Object> e=expense(eid);
  condition(Set.of("PAID","NEEDS_REVIEW").contains(str(e,"status")),"Chỉ đối soát khi đã ghi nhận đủ số tiền chi trả");
  var valid=activeSuccessfulPayments(eid);
  condition(!valid.isEmpty(),"Không có giao dịch thanh toán thành công");
  for(var payment:valid)condition(id(a)==((Number)payment.get("payer_id")).longValue(),"Chỉ kế toán phụ trách các lần chi trả được đối soát");
  Map<String,Object> summary=paymentSummary(eid);
  if(matched){
    condition((boolean)summary.get("dataComplete"),"Giao dịch thiếu số tiền hoặc người nhận, chưa thể đối soát");
    condition((boolean)summary.get("amountMatches"),"Chưa ghi nhận đủ số tiền được duyệt, không thể hoàn tất");
    condition((boolean)summary.get("namesMatch"),"Có giao dịch sai tên người nhận, không thể hoàn tất");
  }else note=required(note,"lý do chênh lệch",1000);
  // Mỗi lần kiểm tra liên kết tới giao dịch cuối, nhưng tổng kiểm tra tất cả giao dịch chưa hủy.
  Map<String,Object> last=valid.get(valid.size()-1);
  db.update("INSERT INTO reconciliations(payment_id,expense_id,reconciled_by,result,note) VALUES (?,?,?,?,?)",id(last),eid,id(a),matched?"MATCHED":"MISMATCHED",note==null?"":note.trim());
  condition(db.update("UPDATE expenses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=?",matched?"COMPLETED":"NEEDS_REVIEW",eid,str(e,"status"))==1,"Trạng thái hồ sơ đã thay đổi");
  log(a,"EXPENSE",eid,matched?"RECONCILE_OK":"RECONCILE_DIFF",Objects.toString(note,""));
  notifyUser(((Number)e.get("created_by")).longValue(),"Đối soát "+str(e,"code")+": "+(matched?"khớp, hoàn tất":"có chênh lệch cần xử lý"));
 }
 @Transactional public void upload(Map<String,Object> a,long eid,MultipartFile file) throws IOException {
  Map<String,Object> e=expense(eid);condition(id(a)==((Number)e.get("created_by")).longValue(),"Chỉ người lập được thêm chứng từ");
  condition(str(e,"status").equals("DRAFT")||str(e,"status").equals("REJECTED"),"Không thể thêm chứng từ sau khi gửi");
  condition(file!=null&&!file.isEmpty(),"Vui lòng chọn tệp chứng từ");condition(file.getSize()<=5*1024*1024,"Tệp vượt 5 MB");
  String original=Objects.toString(file.getOriginalFilename(),"chung-tu").replace('\\','/');original=original.substring(original.lastIndexOf('/')+1);
  condition(original.length()<=200,"Tên tệp quá dài");String lower=original.toLowerCase(Locale.ROOT);
  String content=Objects.toString(file.getContentType(),"");
  condition((lower.endsWith(".pdf")&&content.equals("application/pdf"))|| (lower.matches(".*\\.(png|jpg|jpeg)$")&&Set.of("image/png","image/jpeg").contains(content)),"Chỉ nhận PDF, PNG hoặc JPG");
  Files.createDirectories(uploads);String stored=UUID.randomUUID()+(lower.endsWith(".pdf")?".pdf":lower.endsWith(".png")?".png":".jpg");Path dest=uploads.resolve(stored).normalize();
  condition(dest.startsWith(uploads),"Đường dẫn tệp không hợp lệ");Files.copy(file.getInputStream(),dest,StandardCopyOption.REPLACE_EXISTING);
  try{db.update("INSERT INTO documents(expense_id,original_name,stored_name,content_type,size_bytes) VALUES (?,?,?,?,?)",eid,original,stored,content,file.getSize());}
  catch(RuntimeException ex){Files.deleteIfExists(dest);throw ex;}
  log(a,"EXPENSE",eid,"UPLOAD",original);
 }
 public Map<String,Object> document(Map<String,Object> a,long docId){Map<String,Object> d=db.queryForList("SELECT * FROM documents WHERE id=?",docId).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy chứng từ"));canView(a,expense(((Number)d.get("expense_id")).longValue()));return d;}
 public Path documentPath(Map<String,Object> d){return uploads.resolve(str(d,"stored_name")).normalize();}
 @Transactional public long register(String username,String fullName,String email,String password){
  username=required(username,"tên đăng nhập",60).toLowerCase(Locale.ROOT);condition(username.matches("[a-z0-9_]{4,60}"),"Tên đăng nhập 4–60 ký tự, chỉ gồm a-z, 0-9 và _");
  fullName=required(fullName,"họ tên",150);email=required(email,"email",150).toLowerCase(Locale.ROOT);condition(email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),"Email không hợp lệ");
  condition(password!=null&&password.length()>=6&&password.length()<=72,"Mật khẩu phải dài 6–72 ký tự");
  condition(db.queryForObject("SELECT COUNT(*) FROM app_users WHERE username=? OR email=?",Integer.class,username,email)==0,"Tên đăng nhập hoặc email đã tồn tại");
  try{return insert("INSERT INTO app_users(username,password_hash,full_name,email,role,limit_amount) VALUES (?,?,?,?,'USER',10000000)",username,encoder.encode(password),fullName,email);}catch(org.springframework.dao.DuplicateKeyException ex){throw new IllegalArgumentException("Tên đăng nhập hoặc email đã tồn tại");}
 }
 @Transactional public void profile(Map<String,Object> a,String name,String email){
  String e=required(email,"email",150).toLowerCase(Locale.ROOT);condition(e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),"Email không hợp lệ");
  condition(db.queryForObject("SELECT COUNT(*) FROM app_users WHERE email=? AND id<>?",Integer.class,e,id(a))==0,"Email đã được dùng");
  db.update("UPDATE app_users SET full_name=?,email=? WHERE id=?",required(name,"họ tên",150),e,id(a));log(a,"USER",id(a),"PROFILE","Cập nhật hồ sơ");
 }
 /** Doi avatar: chi chu tai khoan thao tac, khong tin ten file do trinh duyet cung cap. */
 public void changeAvatar(Map<String,Object> a,MultipartFile file) throws IOException {
  condition(file!=null&&!file.isEmpty(),"Vui lòng chọn ảnh đại diện.");
  condition(file.getSize()<=2*1024*1024,"Ảnh đại diện không được vượt 2 MB.");
  byte[] magic=file.getBytes();
  boolean png=magic.length>=8 && (magic[0]&255)==137 && magic[1]==80 && magic[2]==78 && magic[3]==71 && magic[4]==13 && magic[5]==10 && magic[6]==26 && magic[7]==10;
  boolean jpg=magic.length>=4 && (magic[0]&255)==255 && (magic[1]&255)==216 && (magic[2]&255)==255;
  condition(png||jpg,"Chỉ chấp nhận ảnh PNG hoặc JPG hợp lệ.");
  Files.createDirectories(avatarDir);
  String stored=UUID.randomUUID().toString()+(png?".png":".jpg");
  Path destination=avatarDir.resolve(stored).normalize();
  condition(destination.startsWith(avatarDir),"Đường dẫn ảnh không hợp lệ.");
  Files.write(destination,magic,StandardOpenOption.CREATE_NEW);
  try {
   String previous=Objects.toString(a.get("avatar_file"),"");
   db.update("UPDATE app_users SET avatar_file=? WHERE id=?",stored,id(a));
   log(a,"USER",id(a),"AVATAR","Cập nhật ảnh đại diện");
   if(previous.matches("[0-9a-f-]{36}\\.(png|jpg)"))Files.deleteIfExists(avatarDir.resolve(previous));
  } catch(RuntimeException ex){Files.deleteIfExists(destination);throw ex;}
 }
 public Map<String,Object> avatarOwner(long userId){
  return db.queryForList("SELECT id,avatar_file FROM app_users WHERE id=? AND active=TRUE",userId)
    .stream().findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy ảnh đại diện."));
 }
 public Path avatarPath(Map<String,Object> owner){
  String file=Objects.toString(owner.get("avatar_file"),"");
  condition(file.matches("[0-9a-f-]{36}\\.(png|jpg)"),"Chưa có ảnh đại diện.");
  Path path=avatarDir.resolve(file).normalize();condition(path.startsWith(avatarDir),"Đường dẫn ảnh không hợp lệ.");
  return path;
 }
 @Transactional public void password(Map<String,Object> a,String old,String fresh){
  condition(encoder.matches(old,str(a,"password_hash")),"Mật khẩu hiện tại không đúng");condition(fresh!=null&&fresh.length()>=6&&fresh.length()<=72,"Mật khẩu mới dài 6–72 ký tự");
  db.update("UPDATE app_users SET password_hash=? WHERE id=?",encoder.encode(fresh),id(a));log(a,"USER",id(a),"PASSWORD","Đổi mật khẩu");
 }
 public List<Map<String,Object>> users(Map<String,Object> a){role(a,"ADMIN");return db.queryForList("SELECT id,username,full_name,email,role,limit_amount,active,created_at FROM app_users ORDER BY id");}
 @Transactional public void updateUser(Map<String,Object> a,long uid,String newRole,boolean active,String limit){
  role(a,"ADMIN");condition(Set.of("USER","APPROVER","ACCOUNTANT","ADMIN","AUDITOR").contains(newRole),"Vai trò không hợp lệ");
  condition(id(a)!=uid,"Không được tự thay đổi vai trò/trạng thái tài khoản quản trị đang dùng");BigDecimal l;
  try{l=new BigDecimal(limit);condition(l.signum()>=0,"Hạn mức phải từ 0");}catch(NumberFormatException ex){throw new IllegalArgumentException("Hạn mức không hợp lệ");}
  condition(db.update("UPDATE app_users SET role=?,active=?,limit_amount=? WHERE id=?",newRole,active,l,uid)==1,"Không tìm thấy người dùng");log(a,"USER",uid,"ADMIN_UPDATE","Thay quyền, trạng thái, hạn mức");
 }
 @Transactional public void newCategory(Map<String,Object> a,String name){role(a,"ADMIN");String n=required(name,"tên loại chi",150);condition(db.queryForObject("SELECT COUNT(*) FROM categories WHERE name=?",Integer.class,n)==0,"Loại chi đã tồn tại");long id=insert("INSERT INTO categories(name) VALUES (?)",n);log(a,"CATEGORY",id,"CREATE",n);}
 @Transactional public void newDepartment(Map<String,Object> a,String name){role(a,"ADMIN");String n=required(name,"tên phòng ban",150);condition(db.queryForObject("SELECT COUNT(*) FROM departments WHERE name=?",Integer.class,n)==0,"Phòng ban đã tồn tại");long id=insert("INSERT INTO departments(name) VALUES (?)",n);log(a,"DEPARTMENT",id,"CREATE",n);}
 public List<Map<String,Object>> report(Map<String,Object> a){role(a,"ADMIN","APPROVER","ACCOUNTANT","AUDITOR");return db.queryForList("SELECT status,COUNT(*) total_count,COALESCE(SUM(amount),0) total_amount FROM expenses GROUP BY status ORDER BY status");}
 public List<Map<String,Object>> monthly(Map<String,Object> a){role(a,"ADMIN","APPROVER","ACCOUNTANT","AUDITOR");return db.queryForList("SELECT EXTRACT(YEAR FROM created_at) year_num,EXTRACT(MONTH FROM created_at) month_num,COUNT(*) total_count,COALESCE(SUM(amount),0) total_amount FROM expenses GROUP BY EXTRACT(YEAR FROM created_at),EXTRACT(MONTH FROM created_at) ORDER BY year_num DESC,month_num DESC");}
 // Giữ toàn bộ nhật ký trong PostgreSQL, chỉ lấy số dòng cần xem cho mỗi trang.
 public long auditCount(Map<String,Object> a){
  role(a,"ADMIN","AUDITOR");
  return db.queryForObject("SELECT COUNT(*) FROM audit_logs",Long.class);
 }
 public List<Map<String,Object>> auditPage(Map<String,Object> a,int page,int pageSize){
  role(a,"ADMIN","AUDITOR");
  int safePage=Math.max(0,page);
  int safeSize=Math.max(1,Math.min(pageSize,100));
  long offset=(long)safePage*safeSize;
  return db.queryForList("SELECT l.*,COALESCE(u.username,'Hệ thống') username FROM audit_logs l LEFT JOIN app_users u ON l.actor_id=u.id ORDER BY l.id DESC LIMIT ? OFFSET ?",safeSize,offset);
 }
 // Tương thích với các chỗ còn sử dụng phương thức cũ.
 public List<Map<String,Object>> audit(Map<String,Object> a){return auditPage(a,0,25);}
 public List<Map<String,Object>> notifications(Map<String,Object> a){return db.queryForList("SELECT * FROM notifications WHERE recipient_id=? ORDER BY id DESC LIMIT 100",id(a));}
 public long unread(Map<String,Object> a){return db.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND read_at IS NULL",Long.class,id(a));}
 @Transactional public void readNotification(Map<String,Object> a,long notification){db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND recipient_id=?",notification,id(a));}
 public Map<String,Object> dashboard(Map<String,Object> a){
  String condition=is(a,"USER")?" WHERE created_by=?":"";
  Object[] args=is(a,"USER")?new Object[]{id(a)}:new Object[]{};
  return db.queryForMap("SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN status='SUBMITTED' THEN 1 ELSE 0 END),0) waiting,COALESCE(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END),0) completed,COALESCE(SUM(amount),0) sum_amount FROM expenses"+condition,args);
 }
}
