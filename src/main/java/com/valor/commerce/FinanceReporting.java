package com.valor.commerce;

import java.time.LocalDate;
import java.util.*;

/** Job-based reporting; dates select service creation dates, never mix currencies. */
final class FinanceReporting {
    private final FinanceAccess a;
    FinanceReporting(FinanceAccess a){this.a=a;}
    Map<String,Object> report(Map<String,String> filters){
        a.admin();var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1");
        try {
            LocalDate from=date(filters.get("dateFrom")),to=date(filters.get("dateTo"));
            if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException();
            if(from!=null){where.append(" AND r.created_at>=?");args.add(from.atStartOfDay());}
            if(to!=null){where.append(" AND r.created_at<?");args.add(to.plusDays(1).atStartOfDay());}
            for(var column:Map.of("customerProfileId","r.customer_id","liftId","r.lift_id").entrySet()){
                if(present(filters,column.getKey())){long id=Long.parseLong(filters.get(column.getKey()));if(id<=0)throw new IllegalArgumentException();where.append(" AND ").append(column.getValue()).append("=?");args.add(id);}
            }
            if(present(filters,"technicianProfileId")){long id=Long.parseLong(filters.get("technicianProfileId"));if(id<=0)throw new IllegalArgumentException();where.append(" AND EXISTS(SELECT 1 FROM technician_assignments t WHERE t.service_request_id=r.id AND t.technician_id=?)");args.add(id);}
            for(var column:Map.of("serviceType","r.service_type","jobStatus","r.status").entrySet())if(present(filters,column.getKey())){where.append(" AND ").append(column.getValue()).append("=?");args.add(FinanceAccess.text(filters.get(column.getKey()),40));}
            if(present(filters,"amc")){String value=filters.get("amc");if(!List.of("true","false").contains(value))throw new IllegalArgumentException();where.append(value.equals("false")?" AND NOT EXISTS":" AND EXISTS").append("(SELECT 1 FROM amc_contracts c WHERE c.lift_id=r.lift_id AND c.start_date<=CAST(r.created_at AS DATE) AND c.end_date>=CAST(r.created_at AS DATE))");}
            if(present(filters,"paymentStatus")||present(filters,"paymentMethod")){
                where.append(" AND EXISTS(SELECT 1 FROM payment_records fp WHERE fp.service_request_id=r.id AND fp.currency='INR'");
                if(present(filters,"paymentStatus")){String status=filters.get("paymentStatus");PaymentStatus.valueOf(status);where.append(" AND fp.status=?");args.add(status);}
                if(present(filters,"paymentMethod")){switch(filters.get("paymentMethod")){case "CASH"->where.append(" AND fp.provider_reference LIKE 'CASH%'");case "RAZORPAY"->where.append(" AND fp.razorpay_order_id IS NOT NULL");case "WALLET"->where.append(" AND fp.purpose='WALLET_PAYMENT'");default->throw new IllegalArgumentException();}}
                where.append(")");
            }
        }catch(IllegalArgumentException | java.time.DateTimeException ex){throw new CommerceException(400,"Invalid finance report filter or date range");}
        String scope="SELECT r.id FROM service_requests r"+where;
        Object[] values=args.toArray();
        var totals=new LinkedHashMap<String,Object>();
        String invoice=" FROM invoices i WHERE i.currency='INR' AND i.service_request_id IN ("+scope+")";
        totals.put("invoiceValue",a.sum("SELECT COALESCE(SUM(i.total_amount),0)"+invoice+" AND i.status IN ('ISSUED','PAID')",values));
        totals.put("receivable",a.sum("SELECT COALESCE(SUM(i.total_amount),0)"+invoice+" AND i.status='ISSUED'",values));
        totals.put("overdueInvoices",a.count("SELECT COUNT(*)"+invoice+" AND i.status='ISSUED' AND i.due_date<CURRENT_DATE",values));
        totals.put("quotationsPending",a.count("SELECT COUNT(*) FROM service_quotations q WHERE q.release_status='RELEASED' AND q.status='ISSUED' AND q.service_request_id IN ("+scope+")",values));
        totals.put("quotationsApproved",a.count("SELECT COUNT(*) FROM service_quotations q WHERE q.status='ACCEPTED' AND q.service_request_id IN ("+scope+")",values));
        String jobs="SELECT r.id,r.title,r.status,r.created_at,"+
            "COALESCE((SELECT SUM(p.amount) FROM payment_records p WHERE p.service_request_id=r.id AND p.currency='INR' AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED')),0) AS collected,"+
            "COALESCE((SELECT SUM(f.amount) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.service_request_id=r.id AND f.currency='INR' AND f.status='SUCCEEDED'),0) AS refunded,"+
            "COALESCE((SELECT SUM(c.approved_amount+COALESCE((SELECT SUM(x.adjustment_amount) FROM commission_adjustments x WHERE x.commission_id=c.id),0)) FROM technician_commissions c WHERE c.service_request_id=r.id AND c.status IN ('READY','PAID')),0) AS commissions,"+
            "COALESCE((SELECT SUM(s.quantity*s.unit_cost) FROM supplier_purchases s WHERE s.service_request_id=r.id),0) AS procurement,"+
            "COALESCE((SELECT SUM(e.amount) FROM technician_expense_claims e WHERE e.service_request_id=r.id AND e.status IN ('APPROVED','REIMBURSED')),0) AS expenses FROM service_requests r"+where;
        var aggregate=a.one("SELECT COUNT(*) AS total_jobs,COALESCE(SUM(collected),0) AS collected,COALESCE(SUM(refunded),0) AS refunded,COALESCE(SUM(commissions),0) AS commissions_approved,COALESCE(SUM(procurement),0) AS procurement_cost,COALESCE(SUM(expenses),0) AS expenses_approved,COALESCE(SUM(collected-refunded-commissions-procurement-expenses),0) AS recorded_contribution FROM ("+jobs+") scoped",values);
        totals.putAll(aggregate);
        String cash=" FROM company_cash_receipts cr WHERE cr.service_request_id IN ("+scope+")";
        totals.put("companyCashHeld",a.sum("SELECT COALESCE(SUM(cr.amount-COALESCE((SELECT SUM(mi.amount) FROM company_cash_remittance_items mi JOIN company_cash_remittances m ON m.id=mi.remittance_id WHERE mi.receipt_id=cr.id AND m.status='VERIFIED'),0)),0)"+cash,values));
        totals.put("cashPendingVerification",a.sum("SELECT COALESCE(SUM(COALESCE((SELECT SUM(mi.amount) FROM company_cash_remittance_items mi JOIN company_cash_remittances m ON m.id=mi.remittance_id WHERE mi.receipt_id=cr.id AND m.status='PENDING_VERIFICATION'),0)),0)"+cash,values));
        totals.put("payoutsConfirmed",a.sum("SELECT COALESCE(SUM(i.amount),0) FROM technician_payout_items i JOIN technician_payout_batches b ON b.id=i.batch_id JOIN technician_commissions c ON c.id=i.commission_id WHERE b.status='SUCCEEDED' AND c.service_request_id IN ("+scope+")",values).add(a.sum("SELECT COALESCE(SUM(o.amount),0) FROM technician_payout_adjustments o JOIN technician_payout_batches b ON b.id=o.batch_id JOIN commission_adjustments x ON x.id=o.adjustment_id JOIN technician_commissions c ON c.id=x.commission_id WHERE b.status='SUCCEEDED' AND c.service_request_id IN ("+scope+")",values)));
        totals.put("commissionsAwaitingPayout",a.sum("SELECT COALESCE(SUM(c.approved_amount+COALESCE((SELECT SUM(x.adjustment_amount) FROM commission_adjustments x WHERE x.commission_id=c.id),0)),0) FROM technician_commissions c WHERE c.status='READY' AND c.service_request_id IN ("+scope+")",values));
        totals.put("advancesReceived",a.sum("SELECT COALESCE(SUM(p.amount),0) FROM payment_records p JOIN service_payment_milestones m ON m.invoice_id=p.invoice_id WHERE p.currency='INR' AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') AND m.due_condition IN ('ON_APPROVAL','BEFORE_REPAIR') AND m.service_request_id IN ("+scope+")",values));
        totals.put("approvedNetLabourBasis",a.sum("SELECT COALESCE(SUM(b.net_labour_amount),0) FROM invoice_commission_bases b JOIN invoices i ON i.id=b.invoice_id WHERE i.currency='INR' AND i.service_request_id IN ("+scope+")",values));
        var rows=a.rows("SELECT j.*,j.collected-j.refunded-j.commissions-j.procurement-j.expenses AS recorded_contribution FROM ("+jobs+") j ORDER BY j.id DESC LIMIT 500",values);
        var trends=a.rows("SELECT YEAR(created_at) AS report_year,MONTH(created_at) AS report_month,SUM(collected) AS collected,SUM(refunded) AS refunded,SUM(commissions) AS commissions FROM ("+jobs+") j GROUP BY YEAR(created_at),MONTH(created_at) ORDER BY report_year,report_month",values);
        return Map.of("summary",totals,"jobs",rows,"monthly",trends,"currency","INR","countMetrics",List.of("overdueInvoices","quotationsPending","quotationsApproved","totalJobs"),"note","Filters select jobs by service creation date and matching attributes. Summary includes every matching job; detail/export shows the latest 500. Monthly values group by service creation month. Contribution excludes unallocated procurement, overhead and unrecorded costs. Approved net labour is a reviewed basis, not collected revenue.");
    }
    private static boolean present(Map<String,String> values,String key){return values.get(key)!=null&&!values.get(key).isBlank();}
    private static LocalDate date(String value){return value==null||value.isBlank()?null:LocalDate.parse(value);}
}
