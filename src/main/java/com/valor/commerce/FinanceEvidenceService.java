package com.valor.commerce;

import java.io.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
class FinanceEvidenceService {
    final FinanceAccess a;final AssetDocumentStorage storage;
    FinanceEvidenceService(FinanceAccess a,AssetDocumentStorage storage){this.a=a;this.storage=storage;}
    Map<String,Object> upload(Long batch,Long job,MultipartFile file){
        if(batch!=null){a.admin();a.one("SELECT id FROM technician_payout_batches WHERE id=?",batch);}else{a.job(job,false);long tech=a.technician();if(a.count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status IN ('ACCEPTED','COMPLETED')",job,tech)==0)throw new CommerceException(403,"Assigned job access required");}
        return store(batch,job,null,file);
    }
    Map<String,Object> uploadHelper(long allowanceId,MultipartFile file){
        a.admin();var allowance=a.one("SELECT x.id,m.service_request_id FROM service_helper_allowances x JOIN service_team_members m ON m.id=x.team_member_id WHERE x.id=?",allowanceId);
        if(a.count("SELECT COUNT(*) FROM service_helper_allowances WHERE id=? AND status='APPROVED'",allowanceId)==0)throw new CommerceException(409,"Approve the helper allowance before uploading bank evidence");
        return store(null,((Number)allowance.get("serviceRequestId")).longValue(),allowanceId,file);
    }
    private Map<String,Object> store(Long batch,Long job,Long helperAllowance,MultipartFile file){
        if(file==null||file.isEmpty()||file.getSize()>10*1024*1024)throw new CommerceException(400,"Evidence must be a file smaller than 10 MB");
        String type=Optional.ofNullable(file.getContentType()).orElse("");
        String extension=switch(type){case "application/pdf"->".pdf";case "image/jpeg"->".jpg";case "image/png"->".png";case "image/webp"->".webp";default->throw new CommerceException(400,"Use a PDF, JPEG, PNG or WebP evidence file");};
        try{byte[] bytes=file.getBytes();boolean valid=bytes.length>=12&&switch(type){case "application/pdf"->new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");case "image/jpeg"->(bytes[0]&255)==255&&(bytes[1]&255)==216;case "image/png"->(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;default->new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");};
            if(!valid)throw new CommerceException(400,"Evidence content does not match its file type");
            var stored=storage.store(extension,new ByteArrayInputStream(bytes));String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("finance-evidence"+extension).replaceAll("[\\\\/\\r\\n]","_");if(filename.length()>255)filename=filename.substring(filename.length()-255);
            long id=a.insert("INSERT INTO finance_evidence(payout_batch_id,service_request_id,helper_allowance_id,uploaded_by_user_id,original_filename,content_type,storage_key,file_size) VALUES(?,?,?,?,?,?,?,?)",batch,job,helperAllowance,a.actor().getId(),filename,type,stored.key(),bytes.length);
            return a.one("SELECT id,original_filename,content_type,file_size FROM finance_evidence WHERE id=?",id);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    CommerceService.Download download(long id){a.admin();var row=a.one("SELECT * FROM finance_evidence WHERE id=?",id);return new CommerceService.Download((String)row.get("originalFilename"),(String)row.get("contentType"),((Number)row.get("fileSize")).longValue(),storage.load((String)row.get("storageKey")));}
    CommerceService.Download expenseReceipt(long claim){a.admin();var row=a.one("SELECT e.id FROM finance_evidence e JOIN technician_expense_claims c ON c.receipt_storage_key=e.storage_key WHERE c.id=?",claim);return download(((Number)row.get("id")).longValue());}
}
