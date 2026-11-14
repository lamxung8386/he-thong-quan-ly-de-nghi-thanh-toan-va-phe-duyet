package vn.fin01;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.fin01.core.FinanceService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:fin01test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=always","app.upload-dir=./target/test-uploads"})
@Transactional
public class FinanceServiceTest {
 @Autowired FinanceService f;
 Map<String,Object> user,approver,accountant,admin;
 @BeforeEach void setup(){user=f.actor("nguoilap");approver=f.actor("nguoiduyet");accountant=f.actor("ketoan");admin=f.actor("admin");}
 private long draft(){return f.create(user,1,"Mua giấy in", "120000", "Nhà cung cấp A","NCC-"+UUID.randomUUID(),"HD-01","2026-10-20");}
 private void addDocument(long eid){assertDoesNotThrow(()->f.upload(user,eid,new MockMultipartFile("file","hoa-don.pdf","application/pdf","%PDF-demo".getBytes(StandardCharsets.UTF_8))));}
 private long submitted(){long id=draft();addDocument(id);f.submit(user,id);return id;}
 private long approved(){long id=submitted();f.decide(approver,id,"APPROVED","");return id;}
 @Test void TC01_createDraft(){long id=draft();assertEquals("DRAFT",f.expense(id).get("status"));}
 @Test void TC02_amountMustBePositive(){assertThrows(IllegalArgumentException.class,()->f.create(user,1,"Bút","-1","NCC","A","5","2026-10-20"));}
 @Test void TC03_limitEnforced(){assertThrows(IllegalArgumentException.class,()->f.create(user,1,"Mua máy","10000001","A","S","9","2026-10-20"));}
 @Test void TC04_duplicateInvoiceBlocked(){f.create(user,1,"A","100","B","ncc","HD22","2026-10-20");assertThrows(IllegalArgumentException.class,()->f.create(user,1,"B","100","B","NCC","hd22","2026-10-20"));}
 @Test void TC05_documentRequiredBeforeSubmit(){long eid=draft();assertThrows(IllegalArgumentException.class,()->f.submit(user,eid));}
 @Test void TC06_uploadExtensionBlocked(){long eid=draft();assertThrows(IllegalArgumentException.class,()->f.upload(user,eid,new MockMultipartFile("file","evil.svg","image/svg+xml","<svg></svg>".getBytes())));}
 @Test void TC07_submitMovesState(){long eid=submitted();assertEquals("SUBMITTED",f.expense(eid).get("status"));}
 @Test void TC08_approved(){long eid=approved();assertEquals("APPROVED",f.expense(eid).get("status"));assertEquals(1,f.approvals(eid).size());}
 @Test void TC09_rejectRequiresReason(){long eid=submitted();assertThrows(IllegalArgumentException.class,()->f.decide(approver,eid,"REJECTED",""));}
 @Test void TC10_rejectAndResubmit(){long eid=submitted();f.decide(approver,eid,"REJECTED","Thiếu thông tin");assertEquals("REJECTED",f.expense(eid).get("status"));f.edit(user,eid,1,"Bổ sung","130000","Nhà cung cấp A");f.submit(user,eid);assertEquals("SUBMITTED",f.expense(eid).get("status"));}
 @Test void TC11_cannotPayBeforeApproval(){long eid=submitted();assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456"));}
 @Test void TC12_paymentFailsThenRetries(){long eid=approved();f.pay(accountant,eid,false,"Giả lập lỗi","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");assertEquals("PAYMENT_FAILED",f.expense(eid).get("status"));f.pay(accountant,eid,true,"Thử lại","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");assertEquals("PAID",f.expense(eid).get("status"));assertEquals(2,f.payments(eid).size());}
 @Test void TC13_reconciliationCompletes(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");f.reconcile(accountant,eid,true,"Khớp số liệu");assertEquals("COMPLETED",f.expense(eid).get("status"));}
 @Test void TC14_mismatchRequiresExplanation(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");assertThrows(IllegalArgumentException.class,()->f.reconcile(accountant,eid,false,""));}
 @Test void TC15_mismatchAndCorrect(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");f.reconcile(accountant,eid,false,"Sai mã giao dịch");assertEquals("NEEDS_REVIEW",f.expense(eid).get("status"));f.reconcile(accountant,eid,true,"Đã kiểm tra lại");assertEquals("COMPLETED",f.expense(eid).get("status"));}
 @Test void TC16_completedCannotEdit(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");f.reconcile(accountant,eid,true,"");assertThrows(IllegalArgumentException.class,()->f.edit(user,eid,1,"Sửa lén","100000","NCC"));}
 @Test void TC17_approverCannotBeCreator(){long eid=submitted();Map<String,Object> fake=new HashMap<>(user);fake.put("role","APPROVER");assertThrows(IllegalArgumentException.class,()->f.decide(fake,eid,"APPROVED",""));}
 @Test void TC18_reconciliationNotAllowedBeforePay(){long eid=approved();assertThrows(IllegalArgumentException.class,()->f.reconcile(accountant,eid,true,""));}
 @Test void TC19_newUserIsUser(){String id="test"+UUID.randomUUID().toString().substring(0,8);f.register(id,"Người thử",id+"@example.com","123456");assertEquals("USER",f.actor(id).get("role"));}
 @Test void TC20_adminCannotDisableSelf(){assertThrows(IllegalArgumentException.class,()->f.updateUser(admin,f.id(admin),"USER",false,"0"));}
 @Test void TC21_roleBlocksPayment(){long eid=approved();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->f.pay(user,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456"));assertTrue(f.payments(eid).isEmpty(),"Không được phát sinh giao dịch chi trả nếu thiếu quyền");}
 @Test void TC24_recordedAmountDisplayedForReconciliation(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");var p=f.payments(eid).get(0);assertEquals(0,new BigDecimal("120000").compareTo((BigDecimal)p.get("paid_amount")));assertEquals("Nhà cung cấp A",p.get("paid_payee"));}
 @Test void TC25_incorrectPaidAmountBlocksCompletion(){long eid=approved();f.pay(accountant,eid,true,"","90000","Nhà cung cấp A","TRANSFER","","Ngân hàng demo","000000123456");assertThrows(IllegalArgumentException.class,()->f.reconcile(accountant,eid,true,""));assertEquals("PARTIALLY_PAID",f.expense(eid).get("status"));}
 @Test void TC26_wrongRecipientBlocksCompletion(){long eid=approved();assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","120000","Người khác","TRANSFER","","Ngân hàng demo","000000123456"));assertEquals("APPROVED",f.expense(eid).get("status"));assertTrue(f.payments(eid).isEmpty());}
 @Test void TC27_failedPaymentDoesNotPretendMoneySent(){long eid=approved();f.pay(accountant,eid,false,"Giả lập thất bại","","","","","","");assertNull(f.payments(eid).get(0).get("paid_amount"));assertEquals("PAYMENT_FAILED",f.expense(eid).get("status"));}
 @Test void TC22_auditRecordsLifecycle(){long eid=approved();assertTrue(f.history(eid).size()>=4);}
 @Test void TC23_notificationsOnSubmit(){long eid=submitted();assertFalse(f.notifications(approver).isEmpty());}
 @Test void TC28_paymentOverApprovedRejected(){long eid=approved();assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","120001","Nhà cung cấp A","TRANSFER","","NH ảo","000000123456"));assertTrue(f.payments(eid).isEmpty());}
 @Test void TC29_partialPaymentTopup(){long eid=approved();f.pay(accountant,eid,true,"","90000","Nhà cung cấp A","TRANSFER","","NH ảo","000000123456");assertEquals("PARTIALLY_PAID",f.expense(eid).get("status"));assertEquals(new BigDecimal("30000.00"),((BigDecimal)f.paymentSummary(eid).get("remaining")).setScale(2));f.pay(accountant,eid,true,"","30000","Nhà cung cấp A","CASH","","","");assertEquals("PAID",f.expense(eid).get("status"));f.reconcile(accountant,eid,true,"");assertEquals("COMPLETED",f.expense(eid).get("status"));}
 @Test void TC30_overpayRemainderRejected(){long eid=approved();f.pay(accountant,eid,true,"","90000","Nhà cung cấp A","CASH","","","");assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","30001","Nhà cung cấp A","CASH","","",""));assertEquals(1,f.payments(eid).size());}
 @Test void TC31_virtualBankAccountRequired(){long eid=approved();assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","NH ảo",""));assertTrue(f.payments(eid).isEmpty());}
 @Test void TC32_virtualBankAccountDigitsOnly(){long eid=approved();assertThrows(IllegalArgumentException.class,()->f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","TRANSFER","","NH ảo","abc123"));}
 @Test void TC33_cashIgnoresBankFields(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","CASH","","Không lưu", "12345678");var p=f.payments(eid).get(0);assertNull(p.get("bank_name"));assertNull(p.get("bank_account"));}
 @Test void TC34_voidWrongOldMockRecord(){long eid=approved();f.pay(accountant,eid,true,"","90000","Nhà cung cấp A","TRANSFER","","NH ảo","000000123456");long payId=f.id(f.payments(eid).get(0));f.voidPayment(accountant,eid,payId,"Ghi nhận nhầm số tiền");assertEquals("APPROVED",f.expense(eid).get("status"));assertEquals(BigDecimal.ZERO,((BigDecimal)f.paymentSummary(eid).get("paidTotal")));assertTrue((Boolean)f.payments(eid).get(0).get("voided"));}
 @Test void TC35_voidRequiresReason(){long eid=approved();f.pay(accountant,eid,true,"","120000","Nhà cung cấp A","CASH","","","");assertThrows(IllegalArgumentException.class,()->f.voidPayment(accountant,eid,f.id(f.payments(eid).get(0)),""));}
 @Test void TC36_failureAfterPartialKeepsProgress(){long eid=approved();f.pay(accountant,eid,true,"","40000","Nhà cung cấp A","CASH","","","");f.pay(accountant,eid,false,"Thanh toán lỗi","","","","","","");assertEquals("PARTIALLY_PAID",f.expense(eid).get("status"));assertEquals(0,new BigDecimal("80000").compareTo((BigDecimal)f.paymentSummary(eid).get("remaining")));}
 @Test void TC37_nameNormalization(){long eid=approved();f.pay(accountant,eid,true,"","120000","  NHÀ   CUNG CẤP A ","CASH","","","");assertEquals("PAID",f.expense(eid).get("status"));}

 @Test void TC41_auditPageIsLimitedAndKeepsOlderRecords(){
  int before=(int)f.auditCount(admin);
  for(int i=0;i<36;i++) f.updateUser(admin,f.id(user),"USER",true,"10000000");
  long total=f.auditCount(admin);
  assertEquals(before+36,total);
  var first=f.auditPage(admin,0,25);
  var second=f.auditPage(admin,1,25);
  assertEquals(25,first.size());
  assertTrue(second.size()>0);
  assertTrue(((Number)first.get(first.size()-1).get("id")).longValue() > ((Number)second.get(0).get("id")).longValue());
 }
 @Test void TC42_regularUserCannotReadAudit(){
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->f.auditPage(user,0,25));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->f.auditCount(user));
 }
 @Test void TC38_avatarRejectsInvalidContent(){
  assertThrows(IllegalArgumentException.class,()->f.changeAvatar(user,new MockMultipartFile("file","wrong.png","image/png","bad-data".getBytes(StandardCharsets.UTF_8))));
 }
 @Test void TC39_avatarStoredAndLinkedToAccount(){
  assertDoesNotThrow(()->f.changeAvatar(user,new MockMultipartFile("file","portrait.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,(byte)217})));
  assertNotNull(f.actor("nguoilap").get("avatar_file"));
  assertTrue(java.nio.file.Files.isRegularFile(f.avatarPath(f.avatarOwner(f.id(user)))));
 }
 @Test void TC40_avatarRejectsOversize(){
  byte[] large=new byte[2*1024*1024+1]; large[0]=(byte)255;large[1]=(byte)216;large[2]=(byte)255;
  assertThrows(IllegalArgumentException.class,()->f.changeAvatar(user,new MockMultipartFile("file","big.jpg","image/jpeg",large)));
 }

}
