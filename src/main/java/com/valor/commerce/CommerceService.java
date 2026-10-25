package com.valor.commerce;

import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.communication.EmailEventService;
import com.valor.pricing.PricingException;
import com.valor.pricing.PricingService;
import com.valor.workflow.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.hibernate.Session;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.valor.commerce.CommerceDtos.*;

@Service
@Transactional
class CommerceService {
    private static final int RECONCILIATION_BATCH_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(CommerceService.class);
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired(required = false)
    private DataSource dataSource;
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final AssetIdentityAccess identities;
    private final CommerceCustomerRepository customers;
    private final CommerceBuildingRepository buildings;
    private final CommerceLiftRepository lifts;
    private final CommerceAmcRepository amcs;
    private final CommerceRequestRepository requests;
    private final CommerceAssignmentRepository assignments;
    private final CommerceReportRepository reports;
    private final PaymentRecordRepository payments;
    private final WalletAccountRepository walletAccounts;
    private final WalletTransactionRepository walletTransactions;
    private final InvoiceRepository invoices;
    private final CashPaymentOtpRepository cashOtps;
    private final SupportTicketRepository tickets;
    private final AmcRenewalRequestRepository renewals;
    @Autowired(required = false)
    private AmcPurchaseOrderRepository amcPurchases;
    private final AssetDocumentRepository documents;
    private final PaymentRefundRepository refunds;
    private final RazorpayWebhookEventRepository webhookEvents;
    private final AssetDocumentStorage storage;
    private final PaymentGateway paymentGateway;
    private final AuditService audit;
    private final EmailEventService emails;
    private final PricingService pricing;
    @Value("${valor.refunds.same-admin-threshold:}")
    private String sameAdminRefundThreshold;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    CommerceService(AssetIdentityAccess identities, CommerceCustomerRepository customers, CommerceBuildingRepository buildings,
            CommerceLiftRepository lifts, CommerceAmcRepository amcs, CommerceRequestRepository requests,
            CommerceAssignmentRepository assignments, CommerceReportRepository reports, PaymentRecordRepository payments,
            WalletAccountRepository walletAccounts, WalletTransactionRepository walletTransactions,
            InvoiceRepository invoices, CashPaymentOtpRepository cashOtps, SupportTicketRepository tickets, AmcRenewalRequestRepository renewals,
            AssetDocumentRepository documents, PaymentRefundRepository refunds, RazorpayWebhookEventRepository webhookEvents,
            AssetDocumentStorage storage, PaymentGateway paymentGateway, AuditService audit, EmailEventService emails,
            PricingService pricing, PasswordEncoder encoder, Clock clock) {
        this.identities = identities; this.customers = customers; this.buildings = buildings; this.lifts = lifts;
        this.amcs = amcs; this.requests = requests; this.assignments = assignments; this.reports = reports;
        this.payments = payments; this.walletAccounts = walletAccounts; this.walletTransactions = walletTransactions;
        this.invoices = invoices; this.cashOtps = cashOtps; this.tickets = tickets; this.renewals = renewals;
        this.documents = documents; this.refunds = refunds; this.webhookEvents = webhookEvents; this.storage = storage; this.paymentGateway = paymentGateway; this.audit = audit; this.emails = emails; this.pricing = pricing;
        this.encoder = encoder; this.clock = clock;
    }

    PageView<PaymentView> payments(Pageable page) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Page<PaymentRecord> rows = isCustomer(actor) ? payments.findByCustomerId(customer(actor).getId(), page) : payments.findAll(page);
        return page(rows.map(this::view));
    }
    PaymentView payment(Long id) { return view(authorized(payments.findById(id).orElseThrow(() -> missing("Payment")))); }
    WalletView wallet() {
        CustomerProfile owner = customer(identities.actor());
        WalletAccount account = walletAccount(owner);
        List<WalletTransactionView> rows = walletTransactions.findByCustomerIdOrderByCreatedAtDescIdDesc(owner.getId(), PageRequest.of(0, 30))
                .map(this::walletTransactionView).getContent();
        return new WalletView(account.getBalance(), account.getCurrency(), rows);
    }
    PageView<WalletTransactionView> walletTransactions(Pageable page) {
        CustomerProfile owner = customer(identities.actor());
        return page(walletTransactions.findByCustomerIdOrderByCreatedAtDescIdDesc(owner.getId(), page).map(this::walletTransactionView));
    }
    @Transactional(noRollbackFor = {CommerceException.class, HttpClientErrorException.class})
    WalletCheckoutView createWalletTopUp(WalletTopUpCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor)) throw denied();
        if (input.amount().scale() > 2 || input.amount().compareTo(new BigDecimal("100.00")) < 0
                || input.amount().compareTo(new BigDecimal("50000.00")) > 0) {
            throw new CommerceException(400, "Wallet top-ups must be between INR 100 and INR 50,000");
        }
        if (!paymentGateway.configured()) throw new CommerceException(503, "Razorpay credentials are not configured");
        CustomerProfile owner = customer(actor);
        walletAccount(owner);
        Optional<PaymentRecord> pending = payments.findFirstByCustomerIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                owner.getId(), PaymentPurpose.WALLET_TOP_UP, List.of(PaymentStatus.PROCESSING));
        if (pending.isPresent()) {
            PaymentRecord active = pending.get();
            if (active.getAmount().compareTo(input.amount()) != 0) {
                throw new CommerceException(409, "A wallet top-up is already processing. Check its status before starting another.");
            }
            if (active.getRazorpayOrderId() == null) {
                throw new CommerceException(409, "A previous wallet top-up has an uncertain outcome and must be reviewed before retrying");
            }
            return walletCheckoutView(active);
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setCustomer(owner); payment.setCreatedBy(actor); payment.setAmount(input.amount());
        payment.setCurrency("INR"); payment.setPurpose(PaymentPurpose.WALLET_TOP_UP); payment.setStatus(PaymentStatus.PROCESSING);
        payments.saveAndFlush(payment);
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomer(owner); transaction.setPayment(payment); transaction.setType(WalletTransactionType.TOP_UP);
        transaction.setReference("TOPUP-" + payment.getId());
        transaction.setAmount(payment.getAmount()); transaction.setCurrency(payment.getCurrency());
        transaction.setStatus(PaymentStatus.PROCESSING); transaction.setDescription("Wallet top-up");
        walletTransactions.saveAndFlush(transaction);
        try {
            JsonNode order = paymentGateway.createOrder(payment.getAmount(), payment.getCurrency(), "valor-wallet-" + payment.getId());
            String orderId = text(order, "id");
            if (orderId == null || orderId.isBlank()) throw new IllegalStateException("Razorpay order response did not contain an order id");
            payment.setRazorpayOrderId(orderId); payment.setGatewayStatus(text(order, "status"));
            payment.setGatewaySyncedAt(LocalDateTime.now(clock));
            return walletCheckoutView(payment);
        } catch (RuntimeException ex) {
            if (isDefinitiveOrderFailure(ex)) {
                payment.setStatus(PaymentStatus.FAILED); payment.setFailureReason(clean(ex.getMessage(), 500));
                transaction.setStatus(PaymentStatus.FAILED);
                throw ex;
            }
            payment.setFailureReason("Razorpay order creation outcome is unknown; manual review is required");
            throw new CommerceException(503, "Razorpay order creation outcome is uncertain; retry is blocked until reviewed");
        }
    }
    WalletInvoicePaymentView payInvoiceWithWallet(Long invoiceId) {
        User actor = identities.actor();
        if (!isCustomer(actor)) throw denied();
        CustomerProfile owner = customer(actor);
        Invoice invoice = authorized(invoices.lockById(invoiceId).orElseThrow(() -> missing("Invoice")));
        if (invoice.getStatus() != InvoiceStatus.ISSUED) throw new CommerceException(409, "Invoice is not eligible for wallet payment");
        if (!"INR".equalsIgnoreCase(invoice.getCurrency())) throw new CommerceException(400, "Wallet payments are supported only for INR invoices");
        if (payments.findByInvoiceIdAndStatusIn(invoice.getId(), List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)).isPresent()) {
            throw new CommerceException(409, "This invoice already has a payment in progress or has been paid");
        }
        WalletAccount account = walletAccount(owner);
        if (refunds.existsByPaymentCustomerIdAndPaymentPurposeAndStatusIn(owner.getId(), PaymentPurpose.WALLET_TOP_UP,
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING))) {
            throw new CommerceException(409, "A wallet top-up refund is pending. Try invoice payment after it finishes.");
        }
        if (account.getBalance().compareTo(invoice.getTotalAmount()) < 0) throw new CommerceException(409, "Wallet balance is too low to pay this invoice");
        PaymentRecord payment = new PaymentRecord();
        payment.setCustomer(owner); payment.setCreatedBy(actor); payment.setInvoice(invoice);
        payment.setServiceRequest(invoice.getServiceRequest()); payment.setAmcContract(invoice.getAmcContract());
        payment.setAmount(invoice.getTotalAmount()); payment.setCurrency(invoice.getCurrency());
        payment.setPurpose(PaymentPurpose.WALLET_PAYMENT); payment.setStatus(PaymentStatus.SUCCEEDED);
        payments.saveAndFlush(payment);
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomer(owner); transaction.setPayment(payment); transaction.setType(WalletTransactionType.INVOICE_PAYMENT);
        transaction.setReference("PAYMENT-" + payment.getId());
        transaction.setAmount(payment.getAmount()); transaction.setCurrency(payment.getCurrency());
        transaction.setStatus(PaymentStatus.SUCCEEDED); transaction.setDescription("Invoice " + (invoice.getInvoiceNumber() == null ? invoice.getId() : invoice.getInvoiceNumber()));
        walletTransactions.saveAndFlush(transaction);
        account.setBalance(account.getBalance().subtract(payment.getAmount()));
        invoice.setStatus(InvoiceStatus.PAID);
        settleInstallationMilestone(payment); activateRenewalAfterSettlement(payment);
        audit.record("WALLET_INVOICE_PAYMENT", "PAYMENT", payment.getId(), "Invoice paid from customer wallet");
        return new WalletInvoicePaymentView(payment.getId(), invoice.getId(), payment.getAmount(), payment.getCurrency(), payment.getStatus().name(), account.getBalance());
    }
    private WalletAccount walletAccount(CustomerProfile owner) {
        entityManager.createNativeQuery("insert ignore into wallet_accounts (customer_id, balance, currency, created_at, updated_at) values (?, 0.00, 'INR', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                .setParameter(1, owner.getId()).executeUpdate();
        return walletAccounts.lockByCustomerId(owner.getId()).orElseThrow(() -> new CommerceException(500, "Unable to load customer wallet"));
    }
    private WalletCheckoutView walletCheckoutView(PaymentRecord payment) {
        return new WalletCheckoutView(payment.getId(), paymentGateway.keyId(), payment.getRazorpayOrderId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus().name());
    }
    private WalletTransactionView walletTransactionView(WalletTransaction row) {
        return new WalletTransactionView(row.getId(), row.getPayment().getId(), row.getType(), row.getAmount(),
                row.getCurrency(), row.getStatus(), row.getDescription(), row.getCreatedAt(), row.getUpdatedAt());
    }
    private void settleWalletTopUp(PaymentRecord payment) {
        WalletTransaction transaction = walletTransactions.lockByPaymentIdAndType(payment.getId(), WalletTransactionType.TOP_UP).orElse(null);
        if (transaction == null || transaction.getType() != WalletTransactionType.TOP_UP || transaction.getStatus() == PaymentStatus.SUCCEEDED) return;
        WalletAccount account = walletAccount(payment.getCustomer());
        account.setBalance(account.getBalance().add(transaction.getAmount()));
        transaction.setStatus(PaymentStatus.SUCCEEDED);
    }
    private void settleWalletRefund(PaymentRefund refund) {
        PaymentRecord payment = refund.getPayment();
        if (payment.getPurpose() != PaymentPurpose.WALLET_TOP_UP || refund.getStatus() != RefundStatus.SUCCEEDED) return;
        String reference = "REFUND-" + refund.getId();
        if (walletTransactions.existsByReference(reference)) return;
        WalletAccount account = walletAccount(payment.getCustomer());
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomer(payment.getCustomer()); transaction.setPayment(payment); transaction.setReference(reference);
        transaction.setType(WalletTransactionType.REFUND); transaction.setAmount(refund.getAmount());
        transaction.setCurrency(refund.getCurrency()); transaction.setStatus(PaymentStatus.SUCCEEDED);
        transaction.setDescription("Top-up refund to payment method");
        walletTransactions.saveAndFlush(transaction);
        account.setBalance(account.getBalance().subtract(refund.getAmount()));
    }
    PaymentView createPayment(PaymentCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        if (input.purpose() == PaymentPurpose.WALLET_TOP_UP || input.purpose() == PaymentPurpose.WALLET_PAYMENT) {
            throw new CommerceException(400, "Wallet payments must use the wallet checkout flow");
        }
        if (input.purpose() == PaymentPurpose.AMC_PURCHASE) {
            throw new CommerceException(400, "New AMC purchases must use the Razorpay AMC checkout flow");
        }
        Invoice invoice = input.invoiceId() == null ? null : authorized(invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice")));
        CustomerProfile customer = isCustomer(actor) ? customer(actor)
                : input.customerProfileId() != null ? customer(input.customerProfileId())
                : invoice == null ? null : invoice.getCustomer();
        if (invoice != null && !invoice.getCustomer().getId().equals(customer.getId())) throw denied();
        PaymentRecord row = new PaymentRecord();
        row.setCustomer(customer); row.setCreatedBy(actor); row.setAmount(input.amount()); row.setCurrency(currency(input.currency()));
        row.setPurpose(input.purpose() == null ? PaymentPurpose.OTHER : input.purpose());
        row.setProviderReference(clean(input.providerReference(), 120));
        if (invoice != null) {
            if (input.serviceRequestId() != null && (invoice.getServiceRequest() == null || !input.serviceRequestId().equals(invoice.getServiceRequest().getId()))) {
                throw new CommerceException(409, "Payment service request does not match invoice");
            }
            if (input.amcContractId() != null && (invoice.getAmcContract() == null || !input.amcContractId().equals(invoice.getAmcContract().getId()))) {
                throw new CommerceException(409, "Payment AMC contract does not match invoice");
            }
            row.setServiceRequest(invoice.getServiceRequest());
            row.setAmcContract(invoice.getAmcContract());
            if (!invoice.getCustomer().getId().equals(customer.getId())) throw denied();
            if (input.amount().compareTo(invoice.getTotalAmount()) != 0 || !currency(input.currency()).equals(invoice.getCurrency())) {
                throw new CommerceException(400, "Payment amount must match invoice total");
            }
            if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new CommerceException(409, "Invoice is not eligible for payment");
            }
            row.setInvoice(invoice);
        } else {
            row.setServiceRequest(input.serviceRequestId() == null ? null : request(input.serviceRequestId(), customer));
            row.setAmcContract(input.amcContractId() == null ? null : amc(input.amcContractId(), customer));
        }
        return view(payments.saveAndFlush(row));
    }
    PaymentView updatePaymentStatus(Long id, PaymentStatusUpdate input) {
        requireAdmin();
        PaymentRecord row = payments.findById(id).orElseThrow(() -> missing("Payment"));
        if (!manualTransitionAllowed(row, input.status())) {
            throw new CommerceException(409, "Gateway-backed payment status must be confirmed by Razorpay or cash OTP verification");
        }
        row.setStatus(input.status()); row.setFailureReason(clean(input.failureReason(), 500));
        row.setProviderReference(clean(input.providerReference(), 120));
        if (input.status() == PaymentStatus.FAILED || input.status() == PaymentStatus.CANCELLED) failAmcPurchaseAfterPaymentFailure(row);
        if (row.getPurpose() == PaymentPurpose.WALLET_TOP_UP) {
            walletTransactions.lockByPaymentIdAndType(row.getId(), WalletTransactionType.TOP_UP)
                    .ifPresent(transaction -> transaction.setStatus(input.status()));
        }
        emails.paymentResult(row.getCustomer().getUser().getId(), row.getCustomer().getFullName(), row.getId(), row.getStatus().name());
        return view(row);
    }

    @Transactional(noRollbackFor = {CommerceException.class, HttpClientErrorException.class})
    RazorpayCheckoutView createRazorpayCheckout(RazorpayCheckoutCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Invoice invoice = authorized(invoices.lockById(input.invoiceId()).orElseThrow(() -> missing("Invoice")));
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new CommerceException(409, "Invoice is not eligible for payment");
        }
        Optional<PaymentRecord> existing = payments.findByInvoiceIdAndStatusIn(invoice.getId(), List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED));
        if (existing.isPresent()) {
            PaymentRecord active = existing.get();
            if (active.getStatus() == PaymentStatus.SUCCEEDED) throw new CommerceException(409, "Invoice already has a successful payment");
            if (active.getRazorpayOrderId() != null) return razorpayCheckoutView(active, invoice);
            throw new CommerceException(409, "A previous Razorpay order attempt has an uncertain outcome and must be reviewed before retrying");
        }
        if (!paymentGateway.configured()) throw new CommerceException(503, "Razorpay test credentials are not configured");
        PaymentRecord row = new PaymentRecord();
        row.setCustomer(invoice.getCustomer()); row.setCreatedBy(actor); row.setInvoice(invoice);
        row.setServiceRequest(invoice.getServiceRequest()); row.setAmcContract(invoice.getAmcContract());
        row.setAmount(invoice.getTotalAmount()); row.setCurrency(invoice.getCurrency()); row.setPurpose(PaymentPurpose.INVOICE);
        row.setStatus(PaymentStatus.PROCESSING);
        payments.saveAndFlush(row);
        try {
            JsonNode order = paymentGateway.createOrder(row.getAmount(), row.getCurrency(), "valor-payment-" + row.getId());
            String orderId = text(order, "id");
            if (orderId == null || orderId.isBlank()) throw new IllegalStateException("Razorpay order response did not contain an order id");
            row.setRazorpayOrderId(orderId); row.setGatewayStatus(text(order, "status")); row.setGatewaySyncedAt(java.time.LocalDateTime.now());
            return razorpayCheckoutView(row, invoice);
        } catch (RuntimeException ex) {
            if (isDefinitiveOrderFailure(ex)) {
                row.setStatus(PaymentStatus.FAILED);
                row.setFailureReason(clean(ex.getMessage(), 500));
                throw ex;
            }
            row.setFailureReason("Razorpay order creation outcome is unknown; manual review is required");
            throw new CommerceException(503, "Razorpay order creation outcome is uncertain; retry is blocked until reviewed");
        }
    }

    private RazorpayCheckoutView razorpayCheckoutView(PaymentRecord payment, Invoice invoice) {
        return new RazorpayCheckoutView(payment.getId(), invoice.getId(), paymentGateway.keyId(), payment.getRazorpayOrderId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus().name());
    }

    @Transactional(noRollbackFor = {CommerceException.class, HttpClientErrorException.class})
    AmcPurchaseCheckoutView createAmcPurchaseCheckout(AmcPurchaseCheckoutCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor)) throw denied();
        if (!paymentGateway.configured()) throw new CommerceException(503, "Razorpay test credentials are not configured");
        AmcPlanRate plan = amcPlanRate(input.planId());
        CustomerProfile owner = customer(actor);
        Lift lift = lifts.lockWithOwner(input.liftId()).orElseThrow(() -> missing("Lift"));
        if (!lift.getBuilding().getCustomer().getId().equals(owner.getId())) throw denied();
        if (!lift.isActive()) throw new CommerceException(409, "This lift is not available for AMC purchase");
        if (amcs.existsByLiftIdAndStatusAndEndDateGreaterThanEqual(lift.getId(), AmcStatus.ACTIVE, LocalDate.now(clock))) {
            throw new CommerceException(409, "This lift already has active AMC coverage");
        }

        Optional<AmcPurchaseOrder> pending = amcPurchases.findFirstByLiftIdAndStatusOrderByCreatedAtDescIdDesc(
                lift.getId(), AmcPurchaseStatus.PAYMENT_PENDING);
        if (pending.isPresent()) {
            AmcPurchaseOrder previous = pending.get();
            if (!previous.getCustomer().getId().equals(owner.getId())) {
                throw new CommerceException(409, "Another account has an AMC payment in progress for this lift");
            }
            PaymentRecord previousPayment = previous.getPayment();
            if (previousPayment.getStatus() == PaymentStatus.SUCCEEDED) {
                activateAmcPurchaseAfterSettlement(previousPayment);
                throw new CommerceException(409, "The previous AMC payment was received. Refresh your AMC records.");
            }
            if (previousPayment.getStatus() == PaymentStatus.PROCESSING) {
                if (!previous.getPlanCode().equals(plan.code())) throw new CommerceException(409, "An AMC payment is already processing for this lift. Check its status before changing plans.");
                if (previousPayment.getRazorpayOrderId() == null) throw new CommerceException(409, "A previous Razorpay order attempt has an uncertain outcome and must be reviewed before retrying");
                return amcPurchaseCheckoutView(previous, previousPayment);
            }
            previous.setStatus(AmcPurchaseStatus.FAILED);
        }

        BigDecimal subtotal = plan.subtotal();
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);
        PaymentRecord payment = new PaymentRecord();
        payment.setCustomer(owner); payment.setCreatedBy(actor); payment.setAmount(total);
        payment.setCurrency("INR"); payment.setPurpose(PaymentPurpose.AMC_PURCHASE); payment.setStatus(PaymentStatus.PROCESSING);
        payments.saveAndFlush(payment);
        AmcPurchaseOrder purchase = new AmcPurchaseOrder();
        purchase.setCustomer(owner); purchase.setLift(lift); purchase.setCreatedBy(actor); purchase.setPayment(payment);
        purchase.setPlanCode(plan.code()); purchase.setPlanName(plan.name()); purchase.setBaseMonths(plan.months());
        purchase.setBonusMonths(plan.bonusMonths()); purchase.setSubtotal(subtotal); purchase.setTaxAmount(tax);
        purchase.setTotalAmount(total); purchase.setCurrency("INR"); purchase.setStatus(AmcPurchaseStatus.PAYMENT_PENDING);
        amcPurchases.saveAndFlush(purchase);
        try {
            JsonNode order = paymentGateway.createOrder(payment.getAmount(), payment.getCurrency(), "valor-amc-" + payment.getId());
            String orderId = text(order, "id");
            if (orderId == null || orderId.isBlank()) throw new IllegalStateException("Razorpay order response did not contain an order id");
            payment.setRazorpayOrderId(orderId); payment.setGatewayStatus(text(order, "status")); payment.setGatewaySyncedAt(LocalDateTime.now(clock));
            return amcPurchaseCheckoutView(purchase, payment);
        } catch (RuntimeException ex) {
            if (isDefinitiveOrderFailure(ex)) {
                payment.setStatus(PaymentStatus.FAILED); payment.setFailureReason(clean(ex.getMessage(), 500));
                purchase.setStatus(AmcPurchaseStatus.FAILED);
                throw ex;
            }
            payment.setFailureReason("Razorpay order creation outcome is unknown; manual review is required");
            throw new CommerceException(503, "Razorpay order creation outcome is uncertain; retry is blocked until reviewed");
        }
    }

    private record AmcPlanRate(String code, String name, int months, int bonusMonths, BigDecimal subtotal) {}
    private AmcPlanRate amcPlanRate(String code) {
        if (code == null) throw new CommerceException(400, "Choose a valid AMC plan");
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "essential" -> new AmcPlanRate("essential", "Essential care", 12, 1, new BigDecimal("12000.00"));
            case "extended" -> new AmcPlanRate("extended", "Extended care", 24, 2, new BigDecimal("22000.00"));
            case "long-term" -> new AmcPlanRate("long-term", "Long-term care", 36, 3, new BigDecimal("32000.00"));
            default -> throw new CommerceException(400, "Choose a valid AMC plan");
        };
    }
    private AmcPurchaseCheckoutView amcPurchaseCheckoutView(AmcPurchaseOrder purchase, PaymentRecord payment) {
        return new AmcPurchaseCheckoutView(payment.getId(), paymentGateway.keyId(), payment.getRazorpayOrderId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus().name(), purchase.getPlanName(),
                purchase.getSubtotal(), purchase.getTaxAmount(), purchase.getTotalAmount());
    }
    private boolean isDefinitiveOrderFailure(RuntimeException ex) {
        return ex instanceof HttpClientErrorException clientError && clientError.getStatusCode().is4xxClientError();
    }

    CashPaymentOtpView requestCashPaymentOtp(CashPaymentOtpRequest input) {
        User actor = identities.actor();
        if (!isCustomer(actor)) throw denied();
        Invoice invoice = authorized(invoices.lockById(input.invoiceId()).orElseThrow(() -> missing("Invoice")));
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new CommerceException(409, "Invoice is not eligible for cash payment");
        }
        payments.findByInvoiceIdAndStatusIn(invoice.getId(), List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED))
                .ifPresent(p -> { throw new CommerceException(409, "Invoice already has an active payment"); });
        PaymentRecord payment = new PaymentRecord();
        payment.setCustomer(invoice.getCustomer()); payment.setCreatedBy(actor); payment.setInvoice(invoice);
        payment.setServiceRequest(invoice.getServiceRequest()); payment.setAmcContract(invoice.getAmcContract());
        payment.setAmount(invoice.getTotalAmount()); payment.setCurrency(invoice.getCurrency()); payment.setPurpose(PaymentPurpose.INVOICE);
        payment.setStatus(PaymentStatus.PROCESSING); payment.setProviderReference("CASH");
        payments.saveAndFlush(payment);
        CashPaymentOtp otp = new CashPaymentOtp(); otp.payment = payment; otp.invoice = invoice; otp.serviceRequest = invoice.getServiceRequest(); otp.customer = invoice.getCustomer();
        String code = String.valueOf(100000 + random.nextInt(900000));
        otp.otpHash = encoder.encode(code); otp.expiresAt = LocalDateTime.now(clock).plusMinutes(5);
        otp.customerVisibleCode = code; otp.customerVisibleUntil = otp.expiresAt;
        cashOtps.saveAndFlush(otp);
        return cashOtpView(otp, false, true);
    }

    @Transactional(noRollbackFor = CommerceException.class)
    CashPaymentOtpView verifyCashPaymentOtp(CashPaymentOtpVerify input) {
        User actor = identities.actor();
        PaymentRecord payment = payments.lockById(input.paymentId()).orElseThrow(() -> missing("Payment"));
        if (!canVerifyCash(payment, actor)) throw denied();
        CashPaymentOtp otp = cashOtps.lockById(input.otpId()).orElseThrow(CommerceService::invalidCashOtp);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!otp.payment.getId().equals(payment.getId()) || !cashContextMatches(otp, payment) || otp.verifiedAt != null || payment.getStatus() != PaymentStatus.PROCESSING
                || !otp.expiresAt.isAfter(now) || otp.attemptsRemaining <= 0 || otp.lockedUntil != null && otp.lockedUntil.isAfter(now)) throw invalidCashOtp();
        if (input.otp() == null || !encoder.matches(input.otp(), otp.otpHash)) {
            otp.attemptsRemaining--;
            if (otp.attemptsRemaining <= 0) otp.lockedUntil = now.plusMinutes(15);
            throw invalidCashOtp();
        }
        otp.verifiedAt = now; otp.verifiedBy = actor; otp.customerVisibleCode = null; otp.customerVisibleUntil = null;
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setGatewayStatus("cash_verified"); payment.setGatewaySyncedAt(now);
        payment.setProviderReference("CASH-" + otp.id);
        if (payment.getInvoice() != null) { payment.getInvoice().setStatus(InvoiceStatus.PAID); settleInstallationMilestone(payment); }
        emails.paymentResult(payment.getCustomer().getUser().getId(), payment.getCustomer().getFullName(), payment.getId(), payment.getStatus().name());
        return cashOtpView(otp, true, false);
    }

    CashPaymentOtpView latestCashPaymentOtp(Long paymentId) {
        PaymentRecord payment = authorized(payments.findById(paymentId).orElseThrow(() -> missing("Payment")));
        boolean exposeCode = identities.actor().getRole() == Role.CUSTOMER;
        return cashOtps.findTopByPaymentIdOrderByCreatedAtDesc(payment.getId()).map(o -> cashOtpView(o, o.verifiedAt != null, exposeCode)).orElse(null);
    }

    @Transactional(noRollbackFor = CommerceException.class)
    PaymentView requestRefund(Long paymentId, RefundCreate input) {
        requireAdmin();
        PaymentRecord payment = payments.lockById(paymentId).orElseThrow(() -> missing("Payment"));
        PaymentRefund refund = createRefundRequest(payment, input, identities.actor());
        return approveRefund(refund.getId(), new RefundApproval(refund.getId(), input.reason()));
    }

    RefundView customerRequestRefund(Long paymentId, RefundCreate input) {
        User actor = identities.actor();
        if (actor.getRole() != Role.CUSTOMER) throw denied();
        PaymentRecord payment = payments.lockById(paymentId).orElseThrow(() -> missing("Payment"));
        if (payment.getCustomer() == null || !payment.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        PaymentRefund refund = createRefundRequest(payment, input, actor);
        audit.record("PAYMENT_REFUND_REQUEST", "PAYMENT", payment.getId(), "Customer requested refund amount=" + refund.getAmount());
        return view(refund);
    }

    private PaymentRefund createRefundRequest(PaymentRecord payment, RefundCreate input, User requester) {
        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) throw new CommerceException(409, "Payment is not refundable");
        if (!"INR".equalsIgnoreCase(payment.getCurrency())) throw new CommerceException(400, "Refunds are supported only for INR payments");
        if (payment.getRazorpayPaymentId() == null) throw new CommerceException(409, "Payment has no gateway payment id");
        if (input.amount().scale() > 2 || input.amount().signum() <= 0) throw new CommerceException(400, "Invalid refund amount");
        if (refunds.existsByPaymentIdAndStatusIn(payment.getId(), List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING))) throw new CommerceException(409, "Refund already in progress");
        if (payment.getPurpose() == PaymentPurpose.WALLET_TOP_UP) {
            CustomerProfile owner = payment.getCustomer();
            if (refunds.existsByPaymentCustomerIdAndPaymentPurposeAndStatusIn(owner.getId(), PaymentPurpose.WALLET_TOP_UP,
                    List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING))) {
                throw new CommerceException(409, "Another wallet top-up refund is already processing");
            }
            WalletAccount account = walletAccount(owner);
            if (account.getBalance().compareTo(input.amount()) < 0) {
                throw new CommerceException(409, "Wallet funds have already been used; the requested top-up refund exceeds the available balance");
            }
        }
        BigDecimal refunded = refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId()).stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.add(input.amount()).compareTo(payment.getAmount()) > 0) throw new CommerceException(409, "Refund exceeds paid amount");
        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment); refund.setRequestedBy(requester); refund.setAmount(input.amount()); refund.setCurrency(payment.getCurrency());
        refund.setReason(clean(input.reason(), 500)); refund.setStatus(RefundStatus.REQUESTED);
        refund.setProviderIdempotencyKey("refund-" + UUID.randomUUID());
        refunds.saveAndFlush(refund);
        return refund;
    }

    @Transactional(noRollbackFor = CommerceException.class)
    PaymentView approveRefund(Long refundId, RefundApproval input) {
        requireAdmin();
        if (input.refundId() == null || !refundId.equals(input.refundId())) throw new CommerceException(400, "Refund reference mismatch");
        User actor = identities.actor();
        // Every path that needs both rows locks the payment first, then the refund.
        // This matches requestRefund(), which already owns the payment lock.
        PaymentRefund reference = refunds.findById(refundId).orElseThrow(() -> missing("Refund"));
        PaymentRecord payment = payments.lockById(reference.getPayment().getId()).orElseThrow(() -> missing("Payment"));
        PaymentRefund refund = refunds.lockById(refundId).orElseThrow(() -> missing("Refund"));
        if (refund.getStatus() != RefundStatus.REQUESTED) throw new CommerceException(409, "Refund is not awaiting approval");
        if (refund.getProviderIdempotencyKey() == null || refund.getProviderIdempotencyKey().isBlank()) {
            refund.setProviderIdempotencyKey("refund-" + UUID.randomUUID());
            refunds.saveAndFlush(refund);
        }
        if (!thresholdConfigured()) throw new CommerceException(503, "Refund approval threshold is not configured");
        if (refund.getApprovedBy() == null) {
            refund.setApprovedBy(actor); refund.setApprovedAt(LocalDateTime.now()); refund.setApprovalReason(clean(input.reason(), 500));
            if (refund.getAmount().compareTo(configuredThreshold()) > 0) {
                audit.record("PAYMENT_REFUND_APPROVAL", "PAYMENT_REFUND", refundId, "First refund approval recorded; second Admin approval required");
                return view(payment);
            }
        } else {
            if (refund.getApprovedBy().getId().equals(actor.getId())) throw new CommerceException(403, "A second distinct Admin is required");
            refund.setSecondApprovedBy(actor); refund.setSecondApprovedAt(LocalDateTime.now()); refund.setSecondApprovalReason(clean(input.reason(), 500));
        }
        refund.setStatus(RefundStatus.PROCESSING);
        refunds.saveAndFlush(refund);
        final JsonNode node;
        try {
            node = paymentGateway.createRefund(payment.getRazorpayPaymentId(), refund.getAmount(), refund.getReason(), refund.getProviderIdempotencyKey());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 409) {
                refund.setGatewayStatus("provider_idempotency_conflict");
                audit.record("PAYMENT_REFUND_OUTCOME_UNKNOWN", "PAYMENT", payment.getId(),
                        "Refund provider reported an idempotency conflict amount=" + refund.getAmount(),
                        "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "FAILURE");
                throw new CommerceException(503, "Refund is already processing or requires reconciliation");
            }
            // A provider rejection is definitive. Keep the audit trail, but do not count
            // the attempt as a successful refund and allow a later reviewed request.
            refund.setStatus(RefundStatus.FAILED);
            refund.setGatewayStatus("rejected_" + ex.getStatusCode().value());
            audit.record("PAYMENT_REFUND_REJECTED", "PAYMENT", payment.getId(),
                    "Refund rejected by gateway amount=" + refund.getAmount(),
                    "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "FAILURE");
            throw new CommerceException(502, "Razorpay rejected the refund request");
        } catch (RuntimeException ex) {
            // A timeout/network failure does not prove that Razorpay did not create the
            // refund. Leave it PROCESSING so it cannot be blindly initiated again.
            refund.setGatewayStatus("outcome_unknown");
            audit.record("PAYMENT_REFUND_OUTCOME_UNKNOWN", "PAYMENT", payment.getId(),
                    "Refund gateway outcome is unknown amount=" + refund.getAmount(),
                    "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "FAILURE");
            throw new CommerceException(503, "Refund outcome is unknown; investigate before retrying");
        }
        String providerRefundId = text(node, "id");
        if (providerRefundId == null || providerRefundId.isBlank()) {
            refund.setGatewayStatus("outcome_unknown");
            audit.record("PAYMENT_REFUND_OUTCOME_UNKNOWN", "PAYMENT", payment.getId(),
                    "Gateway returned no refund reference amount=" + refund.getAmount(),
                    "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "FAILURE");
            throw new CommerceException(503, "Refund outcome is unknown; investigate before retrying");
        }
        refund.setRazorpayRefundId(providerRefundId); refund.setGatewayStatus(text(node, "status"));
        audit.record("PAYMENT_REFUND_REQUEST", "PAYMENT", payment.getId(), "Requested refund amount=" + refund.getAmount(),
                "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "SUCCESS");
        return view(payment);
    }

    List<RefundView> paymentRefunds(Long paymentId) {
        authorized(payments.findById(paymentId).orElseThrow(() -> missing("Payment")));
        return refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId).stream().map(this::view).toList();
    }

    Download refundDocument(Long paymentId, String format, boolean includeUnsettled) {
        PaymentRecord payment = authorized(payments.findById(paymentId).orElseThrow(() -> missing("Payment")));
        if (format == null || !"pdf".equalsIgnoreCase(format.trim())) {
            throw new CommerceException(400, "Only PDF refund documents are supported");
        }
        Invoice invoice = payment.getInvoice();
        if (invoice == null || invoice.getCustomer() == null
                || !invoice.getCustomer().getId().equals(payment.getCustomer().getId())
                || invoice.getInvoiceNumber() == null) {
            throw new CommerceException(409, "Payment invoice relationship is incomplete");
        }
        User actor = identities.actor();
        if (!isAdmin(actor) && !isCustomer(actor)) {
            throw denied();
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(12);
        List<PaymentRefund> all = refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        List<PaymentRefund> visible = isAdmin(actor)
                ? all
                : all.stream().filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(cutoff)).toList();
        List<PaymentRefund> included = includeUnsettled
                ? visible
                : visible.stream().filter(r -> r.getStatus() == RefundStatus.SUCCEEDED).toList();
        BigDecimal successfulTotal = visible.stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
                .map(PaymentRefund::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = payment.getAmount().subtract(successfulTotal);
        StringBuilder text = new StringBuilder(String.join("\n",
                "VALOR CONSOLIDATED REFUND DOCUMENT",
                "Payment: " + payment.getId(),
                "Invoice: " + invoice.getInvoiceNumber(),
                "Original payment: " + payment.getAmount() + " " + payment.getCurrency(),
                "Successful refunded total: " + successfulTotal + " " + payment.getCurrency(),
                "Net amount remaining: " + net + " " + payment.getCurrency(),
                "Included refunds: " + included.size(),
                "This document is not a credit note or tax document."));
        for (PaymentRefund refund : included) {
            text.append('\n').append("Refund ").append(refund.getId())
                    .append(" | amount=").append(refund.getAmount()).append(' ').append(refund.getCurrency())
                    .append(" | status=").append(refund.getStatus())
                    .append(" | provider=").append(refund.getRazorpayRefundId() == null ? "unavailable" : refund.getRazorpayRefundId())
                    .append(" | created=").append(refund.getCreatedAt() == null ? "unavailable" : refund.getCreatedAt());
        }
        byte[] bytes = minimalPdf(text.toString());
        return new Download("refunds-" + paymentId + ".pdf", MediaType.APPLICATION_PDF_VALUE, (long) bytes.length,
                new org.springframework.core.io.ByteArrayResource(bytes));
    }

    Download paymentReceiptPdf(Long paymentId) {
        PaymentRecord payment = authorized(payments.findById(paymentId).orElseThrow(() -> missing("Payment")));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new CommerceException(409, "A receipt is available only for a successful payment");
        }
        Invoice invoice = payment.getInvoice();
        if (invoice == null || invoice.getCustomer() == null
                || !invoice.getCustomer().getId().equals(payment.getCustomer().getId())) {
            throw new CommerceException(409, "Payment invoice relationship is incomplete");
        }
        String receiptId = "RECEIPT-PAYMENT-" + payment.getId();
        String paymentDate = payment.getGatewaySyncedAt() != null
                ? payment.getGatewaySyncedAt().toString()
                : payment.getUpdatedAt() == null ? null : payment.getUpdatedAt().toString();
        if (paymentDate == null || payment.getAmount() == null || payment.getCurrency() == null
                || invoice.getInvoiceNumber() == null) {
            throw new CommerceException(409, "Required receipt information is unavailable");
        }
        String text = String.join("\n",
                "VALOR PAYMENT RECEIPT",
                "Receipt: " + receiptId,
                "Invoice: " + invoice.getInvoiceNumber(),
                "Payment: " + payment.getId(),
                "Payment date: " + paymentDate,
                "Amount: " + payment.getAmount() + " " + payment.getCurrency(),
                "Payment status: " + payment.getStatus().name(),
                "This receipt confirms payment only and is not a tax invoice.");
        byte[] bytes = minimalPdf(text);
        return new Download("receipt-" + payment.getId() + ".pdf", MediaType.APPLICATION_PDF_VALUE, (long) bytes.length,
                new org.springframework.core.io.ByteArrayResource(bytes));
    }

    private boolean thresholdConfigured() {
        if (sameAdminRefundThreshold == null || sameAdminRefundThreshold.isBlank()) return false;
        try { return configuredThreshold().signum() >= 0; }
        catch (RuntimeException ignored) { return false; }
    }

    private BigDecimal configuredThreshold() {
        try {
            BigDecimal value = new BigDecimal(sameAdminRefundThreshold.trim());
            if (value.signum() < 0 || value.scale() > 2) throw new IllegalArgumentException("invalid threshold");
            return value;
        } catch (RuntimeException ex) {
            throw new CommerceException(503, "Refund approval threshold is not configured");
        }
    }

    void razorpayWebhook(String payload, String signature) {
        if (!paymentGateway.validWebhookSignature(payload, signature)) throw new CommerceException(400, "Invalid Razorpay signature");
        try {
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String eventId = text(root, "id"), event = text(root, "event");
            if (eventId == null || event == null) throw new CommerceException(400, "Invalid Razorpay event");
            if (webhookEvents.existsByEventId(eventId)) return;
            JsonNode paymentNode = root.at("/payload/payment/entity");
            JsonNode orderNode = root.at("/payload/order/entity");
            JsonNode refundNode = root.at("/payload/refund/entity");
            String orderId = text(paymentNode, "order_id"); if (orderId == null) orderId = text(orderNode, "id");
            String paymentId = text(paymentNode, "id");
            String refundId = text(refundNode, "id");
            if (!claimWebhookEvent(eventId, event, orderId, paymentId, refundId)) return;
            processGatewayEvent(event, paymentNode, refundNode, orderId, paymentId, refundId);
        } catch (CommerceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommerceException(400, "Invalid Razorpay webhook payload");
        }
    }

    private boolean claimWebhookEvent(String eventId, String event, String orderId, String paymentId, String refundId) {
        if (entityManager != null) {
            String lockName = webhookClaimLockName(eventId);
            boolean acquired = false;
            boolean synchronizedRelease = false;
            Connection lockConnection = null;
            try {
                Connection[] connectionHolder = new Connection[1];
                entityManager.unwrap(Session.class).doWork(connection -> connectionHolder[0] = connection);
                lockConnection = connectionHolder[0];
                Number lockResult = (Number) entityManager.createNativeQuery("select get_lock(?, 10)")
                        .setParameter(1, lockName).getSingleResult();
                acquired = lockResult != null && lockResult.intValue() == 1;
                if (!acquired) throw new CommerceException(503, "Webhook processing is busy; retry later");
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new CommerceException(503, "Webhook processing requires an active transaction");
                }
                Connection capturedLockConnection = lockConnection;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        releaseWebhookClaimLock(eventId, capturedLockConnection);
                    }
                });
                synchronizedRelease = true;
                // The transaction may have performed the fast pre-check before waiting
                // for this advisory lock. Under MySQL REPEATABLE READ that ordinary
                // read can retain a stale snapshot, so use a locking/current read
                // after acquiring the lock.
                List<?> committedEvent = entityManager.createNativeQuery(
                                "select id from razorpay_webhook_events where event_id=? for update")
                        .setParameter(1, eventId).getResultList();
                if (!committedEvent.isEmpty()) {
                    return false;
                }
                String sql = "insert into razorpay_webhook_events(event_id,event_type,razorpay_order_id,razorpay_payment_id,razorpay_refund_id,processed_at) values (?,?,?,?,?,CURRENT_TIMESTAMP)";
                entityManager.createNativeQuery(sql)
                        .setParameter(1, eventId).setParameter(2, event).setParameter(3, orderId)
                        .setParameter(4, paymentId).setParameter(5, refundId).executeUpdate();
                return true;
            } catch (RuntimeException ex) {
                if (acquired && !synchronizedRelease) releaseWebhookClaimLock(eventId, lockConnection);
                throw ex;
            }
        }
        RazorpayWebhookEvent claimed = new RazorpayWebhookEvent();
        claimed.setEventId(eventId); claimed.setEventType(event); claimed.setRazorpayOrderId(orderId);
        claimed.setRazorpayPaymentId(paymentId); claimed.setRazorpayRefundId(refundId);
        try {
            webhookEvents.saveAndFlush(claimed);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private void releaseWebhookClaimLock(String eventId, Connection lockConnection) {
        if (entityManager != null) {
            try {
                Number released = (Number) entityManager.createNativeQuery("select release_lock(?)")
                        .setParameter(1, webhookClaimLockName(eventId)).getSingleResult();
                if (released == null || released.intValue() != 1) {
                    log.error("Webhook advisory lock release failed for event {}", eventId);
                    retireWebhookLockConnection(eventId, lockConnection);
                }
            } catch (RuntimeException ex) {
                log.error("Webhook advisory lock release raised an error for event {}", eventId, ex);
                retireWebhookLockConnection(eventId, lockConnection);
            }
        }
    }

    private void retireWebhookLockConnection(String eventId, Connection lockConnection) {
        if (lockConnection == null) {
            log.error("Cannot retire webhook lock connection for event {}: connection was not captured", eventId);
            return;
        }
        if (!(dataSource instanceof HikariDataSource hikari)) {
            log.error("Cannot retire webhook lock connection for event {}: datasource is not Hikari", eventId);
            return;
        }
        try {
            hikari.evictConnection(lockConnection);
        } catch (RuntimeException ex) {
            log.error("Failed to retire webhook lock connection for event {}", eventId, ex);
        }
    }

    private String webhookClaimLockName(String eventId) {
        return "valor-webhook-" + UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8));
    }

    List<ReconciliationIssue> reconciliation() {
        requireAdmin();
        List<ReconciliationIssue> issues = new ArrayList<>();
        long lastId = 0L;
        while (true) {
            List<PaymentRecord> batch = payments.findByIdGreaterThanOrderByIdAsc(lastId,
                    org.springframework.data.domain.PageRequest.of(0, RECONCILIATION_BATCH_SIZE));
            if (batch.isEmpty()) break;
            for (PaymentRecord p : batch) {
                if (p.getRazorpayOrderId() != null && p.getGatewaySyncedAt() == null) issues.add(issue(p, "LOCAL_NOT_GATEWAY_CONFIRMED", "Gateway order exists but no synchronization timestamp"));
                if (p.getStatus() == PaymentStatus.SUCCEEDED && p.getRazorpayPaymentId() == null) issues.add(issue(p, "MISSING_GATEWAY_PAYMENT", "Successful local payment has no Razorpay payment id"));
                if (p.getInvoice() != null && p.getAmount().compareTo(p.getInvoice().getTotalAmount()) != 0) issues.add(issue(p, "AMOUNT_MISMATCH", "Payment amount differs from invoice total"));
                if (p.getGatewayStatus() != null && p.getStatus() == PaymentStatus.PROCESSING && Set.of("captured", "paid").contains(p.getGatewayStatus())) issues.add(issue(p, "STATUS_MISMATCH", "Gateway status appears paid while local status is processing"));
            }
            long nextId = batch.get(batch.size() - 1).getId();
            if (nextId <= lastId) throw new IllegalStateException("Payment reconciliation query did not advance");
            lastId = nextId;
            if (batch.size() < RECONCILIATION_BATCH_SIZE) break;
        }
        return issues;
    }

    @Transactional
    ReconciliationResult reconcilePayment(Long paymentId) {
        requireAdmin();
        PaymentRecord payment = payments.lockById(paymentId).orElseThrow(() -> missing("Payment"));
        if (payment.getRazorpayOrderId() == null) {
            return reconciliationResult(payment, false, null, false, "NO_ACTION",
                    "No Razorpay order reference exists; gateway outcome cannot be inferred safely");
        }
        try {
            JsonNode order = paymentGateway.lookupOrder(payment.getRazorpayOrderId());
            if (!payment.getRazorpayOrderId().equals(text(order, "id"))) {
                return reconciliationResult(payment, true, text(order, "status"), false, "NO_ACTION",
                        "Gateway order reference does not match the local payment");
            }
            String gatewayPaymentId = payment.getRazorpayPaymentId();
            JsonNode gatewayPayment = gatewayPaymentId == null ? null : paymentGateway.lookupPayment(gatewayPaymentId);
            if (gatewayPayment == null) {
                payment.setGatewayStatus(text(order, "status"));
                payment.setGatewaySyncedAt(LocalDateTime.now(clock));
                return reconciliationResult(payment, true, text(order, "status"), true, "NO_ACTION",
                        "Gateway order verified, but no payment capture evidence is available");
            }
            boolean referencesMatch = payment.getRazorpayPaymentId().equals(text(gatewayPayment, "id"))
                    && payment.getRazorpayOrderId().equals(text(gatewayPayment, "order_id"));
            if (!referencesMatch || !gatewayAmountMatches(payment, gatewayPayment)) {
                return reconciliationResult(payment, true, text(gatewayPayment, "status"), false, "NO_ACTION",
                        "Gateway payment references, amount, or currency do not match the local obligation");
            }
            String status = text(gatewayPayment, "status");
            payment.setGatewayStatus(status);
            payment.setGatewaySyncedAt(LocalDateTime.now(clock));
            if ("captured".equalsIgnoreCase(status)) {
                if (payment.getStatus() != PaymentStatus.REFUNDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                    payment.setStatus(PaymentStatus.SUCCEEDED);
                    if (payment.getInvoice() != null) { payment.getInvoice().setStatus(InvoiceStatus.PAID); settleInstallationMilestone(payment); }
                    settleWalletTopUp(payment);
                    activateRenewalAfterSettlement(payment);
                    activateAmcPurchaseAfterSettlement(payment);
                    audit.record("PAYMENT_RECONCILIATION_CAPTURE", "PAYMENT", payment.getId(), "Verified gateway capture applied during reconciliation");
                }
                return reconciliationResult(payment, true, status, true, "CAPTURE_CONFIRMED", "Verified gateway capture applied");
            }
            if ("failed".equalsIgnoreCase(status)
                    && payment.getStatus() != PaymentStatus.SUCCEEDED
                    && payment.getStatus() != PaymentStatus.REFUNDED
                    && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Verified gateway payment failure");
                walletTransactions.lockByPaymentIdAndType(payment.getId(), WalletTransactionType.TOP_UP)
                        .ifPresent(transaction -> transaction.setStatus(PaymentStatus.FAILED));
                return reconciliationResult(payment, true, status, true, "FAILURE_CONFIRMED", "Verified gateway failure applied");
            }
            payment.setStatus(payment.getStatus() == PaymentStatus.SUCCEEDED ? PaymentStatus.SUCCEEDED : PaymentStatus.PROCESSING);
            return reconciliationResult(payment, true, status, true, "NO_ACTION",
                    "Gateway payment is not captured; invoice remains unpaid");
        } catch (RuntimeException ex) {
            return reconciliationResult(payment, false, null, false, "NO_ACTION",
                    "Gateway lookup unavailable; payment remains unresolved");
        }
    }

    @Transactional
    RefundReconciliationResult reconcileRefund(Long refundId) {
        requireAdmin();
        PaymentRefund reference = refunds.findById(refundId).orElseThrow(() -> missing("Refund"));
        PaymentRecord payment = payments.lockById(reference.getPayment().getId()).orElseThrow(() -> missing("Payment"));
        PaymentRefund refund = refunds.lockById(refundId).orElseThrow(() -> missing("Refund"));
        String localStatus = refund.getStatus().name();
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            return refundReconciliationResult(refund, payment, false, refund.getGatewayStatus(), true,
                    "NO_ACTION", "Refund is not awaiting provider reconciliation");
        }
        if (refund.getRazorpayRefundId() == null || refund.getRazorpayRefundId().isBlank()) {
            JsonNode candidates;
            try { candidates = paymentGateway.listRefunds(payment.getRazorpayPaymentId()); }
            catch (RuntimeException ex) { candidates = null; }
            JsonNode match = uniqueRefundCandidate(candidates, payment, refund);
            if (match == null) {
                audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                        "Refund provider reference is missing or ambiguous; manual investigation required");
                return refundReconciliationResult(refund, payment, false, null, false,
                        "NO_ACTION", "Provider refund reference is missing or ambiguous");
            }
            refund.setRazorpayRefundId(text(match, "id"));
        }
        if (!"INR".equalsIgnoreCase(payment.getCurrency())
                || !payment.getCurrency().equalsIgnoreCase(refund.getCurrency())) {
            audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                    "Refund currency does not match the canonical payment currency");
            return refundReconciliationResult(refund, payment, false, null, false,
                    "NO_ACTION", "Refund currency does not match the payment");
        }
        try {
            JsonNode provider = paymentGateway.lookupRefund(refund.getRazorpayRefundId());
            String providerId = text(provider, "id");
            String providerPaymentId = text(provider, "payment_id");
            String providerCurrency = text(provider, "currency");
            boolean matches = refund.getRazorpayRefundId().equals(providerId)
                    && payment.getRazorpayPaymentId() != null
                    && payment.getRazorpayPaymentId().equals(providerPaymentId)
                    && "INR".equalsIgnoreCase(providerCurrency)
                    && refund.getCurrency().equalsIgnoreCase(providerCurrency)
                    && providerAmountMatches(refund.getAmount(), provider);
            String providerStatus = text(provider, "status");
            if (!matches) {
                audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                        "Provider refund evidence did not match the local payment obligation");
                return refundReconciliationResult(refund, payment, true, providerStatus, false,
                        "NO_ACTION", "Provider refund reference, payment, amount, or currency did not match");
            }
            refund.setGatewayStatus(providerStatus);
            if ("processed".equalsIgnoreCase(providerStatus)) {
                boolean newlySucceeded = refund.getStatus() != RefundStatus.SUCCEEDED;
                refund.setStatus(RefundStatus.SUCCEEDED);
                updateRefundedPayment(payment);
                if (newlySucceeded) emails.refundResult(payment.getCustomer().getUser().getId(), payment.getCustomer().getFullName(), refund.getId(), refund.getStatus().name(), refund.getAmount().toPlainString(), refund.getCurrency());
                audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                        "Verified provider refund status applied: processed");
                return refundReconciliationResult(refund, payment, true, providerStatus, true,
                        "REFUND_CONFIRMED", "Verified provider refund is processed");
            }
            if ("failed".equalsIgnoreCase(providerStatus)) {
                boolean newlyFailed = refund.getStatus() != RefundStatus.FAILED;
                refund.setStatus(RefundStatus.FAILED);
                if (newlyFailed) emails.refundResult(payment.getCustomer().getUser().getId(), payment.getCustomer().getFullName(), refund.getId(), refund.getStatus().name(), refund.getAmount().toPlainString(), refund.getCurrency());
                audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                        "Verified provider refund status applied: failed");
                return refundReconciliationResult(refund, payment, true, providerStatus, true,
                        "FAILURE_CONFIRMED", "Verified provider refund is failed");
            }
            audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                    "Provider refund remains unresolved with status=" + (providerStatus == null ? "unknown" : providerStatus));
            return refundReconciliationResult(refund, payment, true, providerStatus, true,
                    "NO_ACTION", "Provider refund is not in a recognized terminal state");
        } catch (RuntimeException ex) {
            audit.record("PAYMENT_REFUND_RECONCILIATION", "PAYMENT_REFUND", refundId,
                    "Provider refund lookup unavailable; manual investigation required");
            return refundReconciliationResult(refund, payment, false, null, false,
                    "NO_ACTION", "Provider refund lookup unavailable; outcome remains unresolved");
        }
    }

    private RefundReconciliationResult refundReconciliationResult(PaymentRefund refund, PaymentRecord payment,
            boolean evidence, String providerStatus, boolean referencesMatch, String action, String reason) {
        return new RefundReconciliationResult(refund.getId(), payment.getId(), refund.getStatus().name(),
                refund.getAmount(), refund.getCurrency(), evidence, providerStatus, referencesMatch, action, reason);
    }

    private JsonNode uniqueRefundCandidate(JsonNode candidates, PaymentRecord payment, PaymentRefund refund) {
        if (candidates == null || !candidates.path("items").isArray()) return null;
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode item : candidates.path("items")) {
            if (!refund.getCurrency().equalsIgnoreCase(text(item, "currency"))) continue;
            if (!item.has("amount") || !item.get("amount").isNumber()) continue;
            if (!providerAmountMatches(refund.getAmount(), item)) continue;
            if (!payment.getRazorpayPaymentId().equals(text(item, "payment_id"))) continue;
            String receipt = text(item, "receipt");
            if (receipt != null && refund.getProviderIdempotencyKey() != null && !refund.getProviderIdempotencyKey().equals(receipt)) continue;
            if (!Set.of("pending", "processed").contains(Optional.ofNullable(text(item, "status")).orElse(""))) continue;
            matches.add(item);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private boolean providerAmountMatches(BigDecimal expected, JsonNode provider) {
        JsonNode amount = provider == null ? null : provider.get("amount");
        if (amount == null || amount.isNull() || expected == null) return false;
        try {
            return expected.compareTo(new BigDecimal(amount.asText()).movePointLeft(2)) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private ReconciliationResult reconciliationResult(PaymentRecord payment, boolean evidence, String gatewayStatus,
            boolean referencesMatch, String action, String reason) {
        return new ReconciliationResult(payment.getId(), id(payment.getInvoice()), payment.getStatus().name(),
                payment.getAmount(), payment.getCurrency(), evidence, gatewayStatus, referencesMatch, action, reason);
    }

    PageView<InvoiceView> invoices(Pageable page) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Page<Invoice> rows = isCustomer(actor) ? invoices.findByCustomerId(customer(actor).getId(), page) : invoices.findAll(page);
        return page(rows.map(this::view));
    }
    InvoiceView invoice(Long id) { return view(authorized(invoices.findById(id).orElseThrow(() -> missing("Invoice")))); }

    TechnicianServicePaymentView technicianServicePayment(Long requestId) {
        User actor = identities.actor();
        if (actor.getRole() != com.valor.auth.Role.TECHNICIAN || !assignments.existsByRequestIdAndTechnicianUserId(requestId, actor.getId())) throw denied();
        Invoice invoice = invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(requestId).orElse(null);
        if (invoice == null) return new TechnicianServicePaymentView(requestId, null, null, null);
        PaymentRecord payment = payments.findFirstByServiceRequestIdOrderByCreatedAtDesc(requestId).orElse(null);
        CashPaymentOtpView cash = payment == null ? null : cashOtps.findTopByPaymentIdOrderByCreatedAtDesc(payment.getId()).map(o -> cashOtpView(o, o.verifiedAt != null, false)).orElse(null);
        return new TechnicianServicePaymentView(requestId, view(invoice), payment == null ? null : view(payment), cash);
    }
    InvoiceView createServiceInvoice(Long serviceRequestId, ServiceInvoiceCreate input) {
        requireAdmin();
        ServiceRequest serviceRequest = requests.lockWithOwner(serviceRequestId).orElseThrow(() -> missing("Service request"));
        if (serviceRequest.getStatus() != RequestStatus.COMPLETED) throw new CommerceException(409, "Service must be completed before invoice");
        Optional<Invoice> existing = invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(serviceRequestId)
                .filter(i -> i.getStatus() != InvoiceStatus.VOID && i.getStatus() != InvoiceStatus.CANCELLED);
        if (existing.isPresent()) return view(existing.get());
        Invoice row = new Invoice();
        row.setCustomer(serviceRequest.getCustomer()); row.setCreatedBy(identities.actor()); row.setServiceRequest(serviceRequest);
        row.setDescription("Service quote for " + serviceRequest.getServiceId());
        row.setSubtotal(calculatedAmount(serviceRequest, null)); row.setTaxAmount(BigDecimal.ZERO); row.setCurrency("INR");
        row.setTotalAmount(row.getSubtotal()); row.setDueDate(input == null ? null : input.dueDate());
        invoices.saveAndFlush(row);
        row.setInvoiceNumber("INV-%06d".formatted(row.getId()));
        emails.invoiceCreated(serviceRequest.getCustomer().getUser().getId(), serviceRequest.getCustomer().getFullName(), row.getId(), row.getInvoiceNumber(),
                String.valueOf(row.getTotalAmount()), row.getCurrency());
        return view(row);
    }
    InvoiceView createInvoice(InvoiceCreate input) {
        requireAdmin();
        CustomerProfile customer = customer(input.customerProfileId());
        Invoice row = new Invoice();
        row.setCustomer(customer); row.setCreatedBy(identities.actor()); row.setDescription(input.description().trim());
        ServiceRequest serviceRequest = input.serviceRequestId() == null ? null : request(input.serviceRequestId(), customer);
        AmcContract amcContract = input.amcContractId() == null ? null : amc(input.amcContractId(), customer);
        row.setServiceRequest(serviceRequest);
        row.setAmcContract(amcContract);
        if (serviceRequest != null || amcContract != null) {
            row.setSubtotal(calculatedAmount(serviceRequest, amcContract));
            row.setTaxAmount(BigDecimal.ZERO);
            row.setCurrency("INR");
        } else {
            row.setSubtotal(input.subtotal());
            row.setTaxAmount(input.taxAmount() == null ? BigDecimal.ZERO : input.taxAmount());
            row.setCurrency(currency(input.currency()));
        }
        row.setTotalAmount(row.getSubtotal().add(row.getTaxAmount()));
        row.setDueDate(input.dueDate());
        invoices.saveAndFlush(row);
        row.setInvoiceNumber("INV-%06d".formatted(row.getId()));
        emails.invoiceCreated(customer.getUser().getId(), customer.getFullName(), row.getId(), row.getInvoiceNumber(),
                String.valueOf(row.getTotalAmount()), row.getCurrency());
        return view(row);
    }
    InvoiceView updateInvoiceStatus(Long id, InvoiceStatusUpdate input) {
        requireAdmin();
        Invoice row = invoices.findById(id).orElseThrow(() -> missing("Invoice"));
        row.setStatus(input.status());
        return view(row);
    }

    PageView<SupportTicketView> tickets(Pageable page) {
        User actor = identities.actor();
        Page<SupportTicket> rows = isAdmin(actor) ? tickets.findAll(page) : tickets.findByCreatedById(actor.getId(), page);
        return page(rows.map(this::view));
    }
    SupportTicketView ticket(Long id) { return view(authorized(tickets.findById(id).orElseThrow(() -> missing("Support ticket")))); }
    SupportTicketView createTicket(SupportTicketCreate input) {
        User actor = identities.actor();
        SupportTicket row = new SupportTicket();
        row.setCreatedBy(actor); row.setSubject(input.subject().trim()); row.setDescription(input.description().trim());
        row.setCategory(input.category() == null ? SupportTicketCategory.OTHER : input.category());
        row.setPriority(input.priority() == null ? SupportTicketPriority.MEDIUM : input.priority());
        row.setPreferredContact(clean(input.preferredContact(), 80));
        if (input.serviceRequestId() != null) row.setServiceRequest(authorizedRequestForActor(input.serviceRequestId(), actor));
        tickets.saveAndFlush(row);
        row.setTicketReference("SUP-%06d".formatted(row.getId()));
        return view(row);
    }
    SupportTicketView updateTicket(Long id, SupportTicketStatusUpdate input) {
        requireAdmin();
        SupportTicket row = tickets.findById(id).orElseThrow(() -> missing("Support ticket"));
        row.setStatus(input.status()); row.setAdminNotes(clean(input.adminNotes(), 2000));
        return view(row);
    }

    PageView<RenewalView> renewals(Pageable page) {
        User actor = identities.actor();
        Page<AmcRenewalRequest> rows = isCustomer(actor) ? renewals.findByCustomerId(customer(actor).getId(), page) : renewals.findAll(page);
        return page(rows.map(this::view));
    }
    RenewalView renewal(Long id) { return view(authorized(renewals.findById(id).orElseThrow(() -> missing("AMC renewal request")))); }
    RenewalView createRenewal(Long contractId, RenewalCreate input) {
        User actor = identities.actor();
        CustomerProfile customer = customer(actor);
        AmcContract contract = amcs.lockWithOwner(contractId).orElseThrow(() -> missing("AMC contract"));
        if (!contract.getLift().getBuilding().getCustomer().getId().equals(customer.getId())) throw denied();
        if (input.requestedEndDate().isBefore(input.requestedStartDate())) throw new CommerceException(400, "Invalid renewal period");
        if (!input.requestedStartDate().isAfter(contract.getEndDate())) throw new CommerceException(400, "Renewal must start after current contract ends");
        if (renewals.existsByContractIdAndStatusIn(contractId, List.of(RenewalRequestStatus.REQUESTED, RenewalRequestStatus.QUOTED, RenewalRequestStatus.PAYMENT_PENDING))) {
            throw new CommerceException(409, "AMC renewal request already active");
        }
        AmcRenewalRequest row = new AmcRenewalRequest();
        row.setContract(contract); row.setCustomer(customer); row.setRequestedBy(actor);
        row.setRequestedStartDate(input.requestedStartDate()); row.setRequestedEndDate(input.requestedEndDate());
        row.setCustomerNotes(clean(input.customerNotes(), 1000));
        renewals.saveAndFlush(row);
        audit.record("AMC_RENEWAL_REQUEST_CREATE", "AMC_RENEWAL_REQUEST", row.getId(), "Created AMC renewal request amc=" + contract.getId(),
                null, renewalSummary(row), "SUCCESS");
        emails.amcRenewalRequest(customer.getUser().getId(), customer.getFullName(), row.getId(), contract.getId());
        return view(row);
    }
    RenewalView quoteRenewal(Long id, RenewalQuote input) {
        requireAdmin();
        AmcRenewalRequest row = renewals.lockById(id).orElseThrow(() -> missing("AMC renewal request"));
        row.setQuotedAmount(calculatedAmount(null, row.getContract())); row.setCurrency("INR"); row.setAdminNotes(clean(input.adminNotes(), 1000));
        if (input.invoiceId() != null) {
            Invoice invoice = invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice"));
            validateRenewalInvoice(row, invoice); row.setInvoice(invoice);
        }
        if (input.paymentId() != null) {
            PaymentRecord payment = payments.findById(input.paymentId()).orElseThrow(() -> missing("Payment"));
            validateRenewalPayment(row, payment); row.setPayment(payment);
        }
        String before = renewalSummary(row);
        row.setStatus(RenewalRequestStatus.QUOTED);
        audit.record("AMC_RENEWAL_QUOTE", "AMC_RENEWAL_REQUEST", row.getId(), "Quoted AMC renewal request amc=" + row.getContract().getId(),
                before, renewalSummary(row), "SUCCESS");
        return view(row);
    }
    RenewalView updateRenewal(Long id, RenewalStatusUpdate input) {
        requireAdmin();
        AmcRenewalRequest row = renewals.lockById(id).orElseThrow(() -> missing("AMC renewal request"));
        if (row.getStatus() == RenewalRequestStatus.RENEWED && input.status() == RenewalRequestStatus.RENEWED) return view(row);
        String before = renewalSummary(row);
        RenewalRequestStatus previousStatus = row.getStatus();
        row.setAdminNotes(clean(input.adminNotes(), 1000));
        if (input.invoiceId() != null) {
            Invoice invoice = invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice"));
            validateRenewalInvoice(row, invoice); row.setInvoice(invoice);
        }
        if (input.paymentId() != null) {
            PaymentRecord payment = payments.findById(input.paymentId()).orElseThrow(() -> missing("Payment"));
            validateRenewalPayment(row, payment); row.setPayment(payment);
        }
        if (input.status() == RenewalRequestStatus.RENEWED && previousStatus != RenewalRequestStatus.RENEWED) applyRenewal(row);
        row.setStatus(input.status());
        audit.record("AMC_RENEWAL_STATUS", "AMC_RENEWAL_REQUEST", row.getId(), "Changed AMC renewal status amc=" + row.getContract().getId(),
                before, renewalSummary(row), "SUCCESS");
        return view(row);
    }

    List<AssetDocumentView> listDocuments(AssetDocumentOwnerType type, Long ownerId) {
        authorizeAsset(type, ownerId);
        return documents.findByOwnerTypeAndOwnerIdOrderByCreatedAtAscIdAsc(type, ownerId).stream().map(this::view).toList();
    }
    AssetDocumentView uploadDocument(AssetDocumentOwnerType type, Long ownerId, MultipartFile file) {
        User actor = identities.actor();
        authorizeAsset(type, ownerId);
        if (file == null || file.isEmpty()) throw new CommerceException(400, "Document file is required");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!TYPES.contains(contentType)) throw new CommerceException(400, "Unsupported document type");
        if (file.getSize() <= 0 || file.getSize() > MAX_BYTES) throw new CommerceException(400, "Document exceeds size limit");
        try {
            byte[] bytes = file.getBytes();
            if (!looksLike(contentType, bytes)) throw new CommerceException(400, "Document content does not match supported file type");
            var stored = storage.store(extension(contentType), new ByteArrayInputStream(bytes));
            AssetDocument row = new AssetDocument();
            row.setOwnerType(type); row.setOwnerId(ownerId); row.setUploadedBy(actor);
            row.setOriginalFilename(filename(file.getOriginalFilename())); row.setContentType(contentType);
            row.setFileSize(file.getSize()); row.setStorageKey(stored.key());
            return view(documents.saveAndFlush(row));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    Download downloadDocument(AssetDocumentOwnerType type, Long ownerId, Long documentId) {
        authorizeAsset(type, ownerId);
        AssetDocument row = documents.findById(documentId).orElseThrow(() -> missing("Document"));
        if (row.getOwnerType() != type || !row.getOwnerId().equals(ownerId)) throw missing("Document");
        return new Download(row.getOriginalFilename(), row.getContentType(), row.getFileSize(), storage.load(row.getStorageKey()));
    }
    void deleteDocument(AssetDocumentOwnerType type, Long ownerId, Long documentId) {
        authorizeAsset(type, ownerId);
        AssetDocument row = documents.findById(documentId).orElseThrow(() -> missing("Document"));
        if (row.getOwnerType() != type || !row.getOwnerId().equals(ownerId)) throw missing("Document");
        documents.delete(row);
        try { storage.delete(row.getStorageKey()); } catch (IOException ignored) { }
        audit.record("DOCUMENT_DELETE", "DOCUMENT", row.getId(), "Deleted " + type + " document parent=" + ownerId,
                "document=" + row.getId() + ",ownerType=" + type + ",ownerId=" + ownerId + ",contentType=" + row.getContentType() + ",size=" + row.getFileSize(),
                "deleted=true", "SUCCESS");
    }

    Download serviceReportPdf(Long requestId) {
        ServiceRequest request = authorizedRequestForActor(requestId, identities.actor());
        ServiceReport report = reports.findByRequestId(requestId).orElseThrow(() -> missing("Service report"));
        String text = "Valor Service Report\nRequest: " + request.getServiceId() + "\nStatus: " + request.getStatus()
                + "\nDiagnosis: " + report.getDiagnosis() + "\nWork Performed: " + report.getWorkPerformed()
                + "\nTesting Result: " + report.getTestingResult();
        byte[] bytes = minimalPdf(text);
        return new Download("service-report-" + request.getServiceId() + ".pdf", MediaType.APPLICATION_PDF_VALUE, (long) bytes.length,
                new org.springframework.core.io.ByteArrayResource(bytes));
    }

    private void applyRenewal(AmcRenewalRequest row) {
        if (row.getStatus() == RenewalRequestStatus.RENEWED) return;
        PaymentRecord payment = row.getPayment();
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) throw new CommerceException(409, "Successful payment is required");
        if (payment.getCustomer() == null || !payment.getCustomer().getId().equals(row.getCustomer().getId())) throw denied();
        if (row.getInvoice() != null && (payment.getInvoice() == null || !payment.getInvoice().getId().equals(row.getInvoice().getId()))) throw new CommerceException(409, "Payment does not match renewal invoice");
        if (row.getInvoice() != null && row.getInvoice().getStatus() != InvoiceStatus.PAID) throw new CommerceException(409, "Renewal invoice is not paid");
        AmcContract contract = row.getContract();
        contract.setStartDate(row.getRequestedStartDate()); contract.setEndDate(row.getRequestedEndDate());
        contract.setRenewalCount(contract.getRenewalCount() + 1); contract.setStatus(AmcStatus.ACTIVE);
    }
    private void validateRenewalInvoice(AmcRenewalRequest row, Invoice invoice) {
        if (!invoice.getCustomer().getId().equals(row.getCustomer().getId()) || invoice.getAmcContract() == null
                || !invoice.getAmcContract().getId().equals(row.getContract().getId())) throw denied();
        if (invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) throw new CommerceException(409, "Invoice is not eligible for renewal");
    }
    private void validateRenewalPayment(AmcRenewalRequest row, PaymentRecord payment) {
        if (payment.getCustomer() == null || !payment.getCustomer().getId().equals(row.getCustomer().getId())
                || payment.getAmcContract() == null || !payment.getAmcContract().getId().equals(row.getContract().getId())) throw denied();
        if (payment.getInvoice() != null && row.getInvoice() != null && !payment.getInvoice().getId().equals(row.getInvoice().getId())) throw new CommerceException(409, "Payment does not match renewal invoice");
    }
    private BigDecimal calculatedAmount(ServiceRequest request, AmcContract contract) {
        try {
            if (request != null) return pricing.servicePrice(request);
            if (contract != null) return pricing.amcPrice(contract.getLift());
            throw new PricingException("Priced record is required");
        } catch (PricingException ex) {
            throw new CommerceException(400, ex.getMessage());
        }
    }
    private void processGatewayEvent(String event, JsonNode paymentNode, JsonNode refundNode, String orderId, String paymentId, String refundId) {
        if (event.startsWith("payment.")) {
            PaymentRecord located = orderId == null ? payments.findByRazorpayPaymentId(paymentId).orElse(null) : payments.findByRazorpayOrderId(orderId).orElse(null);
            PaymentRecord payment = located == null ? null : payments.lockById(located.getId()).orElse(null);
            if (payment == null) return;
            if (!gatewayReferenceMatches(payment, orderId, paymentId)) return;
            payment.setRazorpayPaymentId(paymentId); payment.setGatewayStatus(text(paymentNode, "status")); payment.setGatewaySyncedAt(java.time.LocalDateTime.now());
            switch (event) {
                case "payment.captured" -> {
                    if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
                        payment.setFailureReason("Captured event received after a refund state; review gateway history");
                    } else if (captureAmountMatchesInvoice(payment, paymentNode)) {
                        payment.setStatus(PaymentStatus.SUCCEEDED);
                        if (payment.getInvoice() != null) { payment.getInvoice().setStatus(InvoiceStatus.PAID); settleInstallationMilestone(payment); }
                        settleWalletTopUp(payment);
                        activateRenewalAfterSettlement(payment);
                        activateAmcPurchaseAfterSettlement(payment);
                        audit.record("PAYMENT_CAPTURE_SETTLED", "PAYMENT", payment.getId(), "Verified gateway capture applied from webhook");
                    } else {
                        payment.setFailureReason("Gateway capture amount does not match invoice amount");
                    }
                }
                case "payment.failed" -> {
                    if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.REFUNDED
                            && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(text(paymentNode, "error_description"));
                        walletTransactions.lockByPaymentIdAndType(payment.getId(), WalletTransactionType.TOP_UP)
                                .ifPresent(transaction -> transaction.setStatus(PaymentStatus.FAILED));
                        failAmcPurchaseAfterPaymentFailure(payment);
                    }
                }
                case "payment.authorized" -> {
                    if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.REFUNDED
                            && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) payment.setStatus(PaymentStatus.PROCESSING);
                }
                default -> { }
            }
        } else if (event.startsWith("refund.") && refundId != null) {
            PaymentRefund located = refunds.findByRazorpayRefundId(refundId).orElse(null);
            if (located == null) return;
            PaymentRecord payment = payments.lockById(located.getPayment().getId()).orElse(null);
            PaymentRefund refund = refunds.lockById(located.getId()).orElse(null);
            if (refund == null || payment == null) return;
            refund.setGatewayStatus(text(refundNode, "status"));
            // A later failed delivery must not undo a confirmed refund. A processed
            // event may still resolve a previously failed/processing local record.
            if (event.equals("refund.processed") && refund.getStatus() != RefundStatus.SUCCEEDED) {
                refund.setStatus(RefundStatus.SUCCEEDED);
                settleWalletRefund(refund);
                emails.refundResult(refund.getPayment().getCustomer().getUser().getId(), refund.getPayment().getCustomer().getFullName(), refund.getId(), refund.getStatus().name(), refund.getAmount().toPlainString(), refund.getCurrency());
            }
            if (event.equals("refund.failed") && refund.getStatus() != RefundStatus.SUCCEEDED
                    && refund.getStatus() != RefundStatus.FAILED) {
                refund.setStatus(RefundStatus.FAILED);
                emails.refundResult(refund.getPayment().getCustomer().getUser().getId(), refund.getPayment().getCustomer().getFullName(), refund.getId(), refund.getStatus().name(), refund.getAmount().toPlainString(), refund.getCurrency());
            }
            settleWalletRefund(refund);
            updateRefundedPayment(payment);
            audit.record(event.equals("refund.processed") ? "PAYMENT_REFUND_SETTLED" : "PAYMENT_REFUND_FAILED",
                    "PAYMENT_REFUND", refund.getId(), "Gateway refund event applied: " + event);
        }
    }
    private void updateRefundedPayment(PaymentRecord payment) {
        BigDecimal refunded = refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId()).stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.compareTo(BigDecimal.ZERO) > 0) payment.setStatus(refunded.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
    }
    private boolean manualTransitionAllowed(PaymentRecord payment, PaymentStatus requested) {
        if (requested == PaymentStatus.REFUNDED || requested == PaymentStatus.PARTIALLY_REFUNDED) return false;
        if (payment.getPurpose() == PaymentPurpose.AMC_PURCHASE && requested == PaymentStatus.SUCCEEDED) return false;
        if (payment.getPurpose() == PaymentPurpose.WALLET_TOP_UP && requested == PaymentStatus.SUCCEEDED) return false;
        boolean gatewayBacked = payment.getRazorpayOrderId() != null || payment.getRazorpayPaymentId() != null
                || payment.getRazorpayPaymentLinkId() != null;
        if (gatewayBacked && requested == PaymentStatus.SUCCEEDED) return false;
        PaymentStatus current = payment.getStatus();
        return switch (current) {
            case PENDING -> requested == PaymentStatus.PROCESSING || requested == PaymentStatus.FAILED || requested == PaymentStatus.CANCELLED;
            case PROCESSING -> requested == PaymentStatus.FAILED || requested == PaymentStatus.CANCELLED;
            case FAILED, CANCELLED -> requested == PaymentStatus.PROCESSING;
            case SUCCEEDED, REFUNDED, PARTIALLY_REFUNDED -> false;
        } || requested == PaymentStatus.SUCCEEDED && !gatewayBacked;
    }
    private boolean captureAmountMatchesInvoice(PaymentRecord payment, JsonNode paymentNode) {
        JsonNode amount = paymentNode == null ? null : paymentNode.get("amount");
        if (amount == null || amount.isNull()) return payment.getPurpose() != PaymentPurpose.AMC_PURCHASE;
        try {
            BigDecimal captured = new BigDecimal(amount.asText()).movePointLeft(2);
            String currency = text(paymentNode, "currency");
            if (!payment.getAmount().equals(captured) || !payment.getCurrency().equalsIgnoreCase(currency)) return false;
            return payment.getInvoice() == null || payment.getInvoice().getTotalAmount().compareTo(captured) == 0
                    && payment.getInvoice().getCurrency().equalsIgnoreCase(currency);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void settleInstallationMilestone(PaymentRecord payment) {
        if (entityManager == null || payment.getInvoice() == null || payment.getInvoice().getId() == null) return;
        Number valid = (Number) entityManager.createNativeQuery("select count(*) from invoices i join installation_milestones m on m.id=i.installation_milestone_id and m.invoice_id=i.id join installation_requests r on r.id=m.installation_request_id where i.id=? and i.customer_id=r.customer_id and m.status='INVOICED'")
                .setParameter(1, payment.getInvoice().getId()).getSingleResult();
        if (valid.longValue() != 1L) return;
        entityManager.createNativeQuery("update installation_milestones set status='PAID' where invoice_id=? and status='INVOICED'")
                .setParameter(1, payment.getInvoice().getId()).executeUpdate();
    }

    private void activateRenewalAfterSettlement(PaymentRecord payment) {
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) return;
        List<RenewalRequestStatus> eligible = List.of(RenewalRequestStatus.QUOTED, RenewalRequestStatus.PAYMENT_PENDING);
        AmcRenewalRequest candidate = renewals.findFirstByPaymentIdAndStatusIn(payment.getId(), eligible).orElseGet(() ->
                payment.getInvoice() == null ? null : renewals.findFirstByInvoiceIdAndStatusIn(payment.getInvoice().getId(), eligible).orElse(null));
        if (candidate == null) return;
        AmcRenewalRequest renewal = renewals.lockById(candidate.getId()).orElse(null);
        if (renewal == null || renewal.getStatus() == RenewalRequestStatus.RENEWED) return;
        if (renewal.getPayment() == null) renewal.setPayment(payment);
        if (renewal.getPayment() != payment && renewal.getPayment().getId() != null && !renewal.getPayment().getId().equals(payment.getId())) return;
        try {
            applyRenewal(renewal);
            renewal.setStatus(RenewalRequestStatus.RENEWED);
            audit.record("AMC_RENEWAL_AUTO_ACTIVATE", "AMC_RENEWAL_REQUEST", renewal.getId(), "Renewal activated from verified payment capture");
        } catch (RuntimeException ex) {
            audit.record("AMC_RENEWAL_AUTO_ACTIVATE_REJECTED", "AMC_RENEWAL_REQUEST", renewal.getId(), "Verified payment did not satisfy renewal activation rules", null, null, "FAILURE");
        }
    }
    private void activateAmcPurchaseAfterSettlement(PaymentRecord payment) {
        if (payment == null || payment.getPurpose() != PaymentPurpose.AMC_PURCHASE
                || payment.getStatus() != PaymentStatus.SUCCEEDED || amcPurchases == null) return;
        AmcPurchaseOrder purchase = amcPurchases.lockByPaymentId(payment.getId()).orElse(null);
        if (purchase == null || purchase.getStatus() == AmcPurchaseStatus.COMPLETED) return;
        if (!purchase.getCustomer().getId().equals(payment.getCustomer().getId())
                || payment.getAmount().compareTo(purchase.getTotalAmount()) != 0
                || !payment.getCurrency().equalsIgnoreCase(purchase.getCurrency())) {
            audit.record("AMC_PURCHASE_SETTLEMENT_REJECTED", "PAYMENT", payment.getId(), "Verified payment did not match the stored AMC purchase amount and owner", null, null, "FAILURE");
            return;
        }

        LocalDate start = LocalDate.now(clock);
        AmcContract previous = amcs.findFirstByLiftIdAndStatusOrderByEndDateDescIdDesc(purchase.getLift().getId(), AmcStatus.ACTIVE).orElse(null);
        if (previous != null && !previous.getEndDate().isBefore(start)) start = previous.getEndDate().plusDays(1);
        LocalDate end = start.plusMonths(purchase.getBaseMonths() + purchase.getBonusMonths()).minusDays(1);

        AmcContract contract = new AmcContract();
        contract.setLift(purchase.getLift()); contract.setAmcNumber("AMC-PAY-%06d".formatted(payment.getId()));
        contract.setPlan(purchase.getPlanName());
        String bonusTerm = purchase.getBonusMonths() == 1 ? " additional month" : " additional months";
        contract.setCoverageDetails(purchase.getBaseMonths() + " months of maintenance with " + purchase.getBonusMonths()
                + bonusTerm + ", subject to the agreed coverage terms.");
        contract.setStartDate(start); contract.setEndDate(end); contract.setRenewalDate(end);
        contract.setStatus(AmcStatus.ACTIVE); contract.setRenewalCount(0);
        amcs.saveAndFlush(contract);

        Invoice invoice = new Invoice();
        invoice.setCustomer(purchase.getCustomer()); invoice.setCreatedBy(purchase.getCreatedBy());
        invoice.setAmcContract(contract); invoice.setDescription(purchase.getPlanName() + " AMC for " + purchase.getLift().getName());
        invoice.setSubtotal(purchase.getSubtotal()); invoice.setTaxAmount(purchase.getTaxAmount());
        invoice.setTotalAmount(purchase.getTotalAmount()); invoice.setCurrency(purchase.getCurrency());
        invoice.setStatus(InvoiceStatus.PAID); invoice.setIssuedDate(LocalDate.now(clock));
        invoices.saveAndFlush(invoice);
        invoice.setInvoiceNumber("INV-%06d".formatted(invoice.getId()));

        payment.setAmcContract(contract); payment.setInvoice(invoice);
        purchase.setContract(contract); purchase.setInvoice(invoice); purchase.setStatus(AmcPurchaseStatus.COMPLETED);
        amcPurchases.flush();
        emails.amcCreated(purchase.getCustomer().getUser().getId(), purchase.getCustomer().getFullName(), contract.getId(), contract.getAmcNumber());
        emails.invoiceCreated(purchase.getCustomer().getUser().getId(), purchase.getCustomer().getFullName(), invoice.getId(), invoice.getInvoiceNumber(), invoice.getTotalAmount().toPlainString(), invoice.getCurrency());
        audit.record("AMC_PURCHASE_SETTLED", "AMC_PURCHASE_ORDER", purchase.getId(), "Verified Razorpay payment activated the AMC and generated its paid invoice");
    }
    private void failAmcPurchaseAfterPaymentFailure(PaymentRecord payment) {
        if (payment == null || payment.getPurpose() != PaymentPurpose.AMC_PURCHASE || amcPurchases == null) return;
        amcPurchases.lockByPaymentId(payment.getId()).ifPresent(purchase -> {
            if (purchase.getStatus() == AmcPurchaseStatus.PAYMENT_PENDING) purchase.setStatus(AmcPurchaseStatus.FAILED);
        });
    }
    private boolean gatewayAmountMatches(PaymentRecord payment, JsonNode gatewayPayment) {
        JsonNode amount = gatewayPayment == null ? null : gatewayPayment.get("amount");
        String currency = text(gatewayPayment, "currency");
        return amount != null && !amount.isNull() && currency != null
                && captureAmountMatchesInvoice(payment, gatewayPayment);
    }
    private boolean gatewayReferenceMatches(PaymentRecord payment, String orderId, String paymentId) {
        return (payment.getRazorpayOrderId() == null || payment.getRazorpayOrderId().equals(orderId))
                && (payment.getRazorpayPaymentId() == null || payment.getRazorpayPaymentId().equals(paymentId));
    }
    private ReconciliationIssue issue(PaymentRecord p, String type, String detail) { return new ReconciliationIssue(p.getId(), id(p.getInvoice()), type, detail); }
    private static String text(JsonNode node, String field) { JsonNode v = node == null ? null : node.get(field); return v == null || v.isNull() ? null : v.asText(); }
    private ServiceRequest authorizedRequestForActor(Long requestId, User actor) {
        ServiceRequest r = requests.withOwner(requestId).orElseThrow(() -> missing("Service request"));
        if (actor.getRole() == Role.CUSTOMER && !r.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (actor.getRole() == Role.TECHNICIAN && !assignments.existsByRequestIdAndTechnicianUserId(requestId, actor.getId())) throw denied();
        if (!isAdmin(actor) && actor.getRole() != Role.CUSTOMER && actor.getRole() != Role.TECHNICIAN) throw denied();
        return r;
    }
    private void authorizeAsset(AssetDocumentOwnerType type, Long ownerId) {
        User actor = identities.actor();
        if (isAdmin(actor)) {
            if (type == AssetDocumentOwnerType.BUILDING) building(ownerId); else lift(ownerId);
            return;
        }
        if (!isCustomer(actor)) throw denied();
        CustomerProfile customer = customer(actor);
        if (type == AssetDocumentOwnerType.BUILDING) {
            Building b = building(ownerId);
            if (!b.getCustomer().getId().equals(customer.getId())) throw missing("Document owner");
        } else {
            Lift l = lift(ownerId);
            if (!l.getBuilding().getCustomer().getId().equals(customer.getId())) throw missing("Document owner");
        }
    }
    private CustomerProfile customer(Long id) { return identities.activeCustomer(id); }
    private CustomerProfile customer(User actor) { return customers.findByUserId(actor.getId()).map(c -> identities.activeCustomer(c.getId())).orElseThrow(() -> denied()); }
    private ServiceRequest request(Long id, CustomerProfile customer) {
        ServiceRequest row = requests.withOwner(id).orElseThrow(() -> missing("Service request"));
        if (!row.getCustomer().getId().equals(customer.getId())) throw denied();
        return row;
    }
    private AmcContract amc(Long id, CustomerProfile customer) {
        AmcContract row = amcs.withOwner(id).orElseThrow(() -> missing("AMC contract"));
        if (!row.getLift().getBuilding().getCustomer().getId().equals(customer.getId())) throw denied();
        return row;
    }
    private Building building(Long id) { return buildings.withOwner(id).orElseThrow(() -> missing("Building")); }
    private Lift lift(Long id) { return lifts.withOwner(id).orElseThrow(() -> missing("Lift")); }
    private <T extends PaymentRecord> T authorized(T row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private <T extends Invoice> T authorized(T row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private AmcRenewalRequest authorized(AmcRenewalRequest row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private SupportTicket authorized(SupportTicket row) {
        User actor = identities.actor();
        if (isAdmin(actor) || row.getCreatedBy().getId().equals(actor.getId())) return row;
        throw denied();
    }
    private boolean owns(CustomerProfile customer) {
        User actor = identities.actor();
        return isAdmin(actor) || isCustomer(actor) && customer.getUser().getId().equals(actor.getId());
    }
    private boolean canVerifyCash(PaymentRecord payment, User actor) {
        if (isAdmin(actor)) return true;
        return actor.getRole() == Role.TECHNICIAN && payment.getServiceRequest() != null
                && assignments.existsByRequestIdAndTechnicianUserIdAndStatusIn(payment.getServiceRequest().getId(), actor.getId(),
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED));
    }
    private boolean cashContextMatches(CashPaymentOtp otp, PaymentRecord payment) {
        return otp.invoice != null && payment.getInvoice() != null
                && otp.invoice.getId().equals(payment.getInvoice().getId())
                && otp.customer != null && payment.getCustomer() != null
                && otp.customer.getId().equals(payment.getCustomer().getId())
                && Objects.equals(id(otp.serviceRequest), id(payment.getServiceRequest()));
    }
    private CashPaymentOtpView cashOtpView(CashPaymentOtp otp, boolean verified, boolean exposeCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        String status = verified || otp.verifiedAt != null ? "VERIFIED" : otp.lockedUntil != null && otp.lockedUntil.isAfter(now) ? "LOCKED" : otp.expiresAt.isAfter(now) ? "PENDING" : "EXPIRED";
        String code = exposeCode && "PENDING".equals(status) && otp.customerVisibleCode != null && otp.customerVisibleUntil != null && otp.customerVisibleUntil.isAfter(now) ? otp.customerVisibleCode : null;
        if (!"PENDING".equals(status) && otp.customerVisibleCode != null) { otp.customerVisibleCode = null; otp.customerVisibleUntil = null; }
        return new CashPaymentOtpView(otp.id, otp.payment.getId(), otp.invoice.getId(), id(otp.serviceRequest), status,
                otp.expiresAt, otp.attemptsRemaining, otp.lockedUntil, otp.verifiedAt, code);
    }
    private void requireAdmin() { identities.requireAdmin(); }
    private boolean isAdmin(User u) { return u.getRole() == Role.ADMIN || u.getRole() == Role.SUPER_ADMIN; }
    private boolean isCustomer(User u) { return u.getRole() == Role.CUSTOMER; }
    private String currency(String value) { String v = value == null ? "INR" : value.trim().toUpperCase(Locale.ROOT); if (!v.matches("[A-Z]{3}")) throw new CommerceException(400, "Invalid currency"); return v; }
    private String clean(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); return v.length() > max ? v.substring(0, max) : v; }
    private String filename(String value) { String name = value == null || value.isBlank() ? "document" : value.replaceAll("[\\\\/\\r\\n]", "_").trim(); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; }; }
    private boolean looksLike(String type, byte[] bytes) {
        if (bytes.length < 4) return false;
        if (type.equals("application/pdf")) return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (type.equals("image/png")) return bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        if (type.equals("image/jpeg")) return bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8;
        return type.equals("image/webp") && bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
    private byte[] minimalPdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "").replace("\n", ") Tj T* (");
        String stream = "BT /F1 11 Tf 50 760 Td (" + safe + ") Tj ET";
        String pdf = "%PDF-1.4\n1 0 obj<<>>endobj\n2 0 obj<</Type/Catalog/Pages 3 0 R>>endobj\n3 0 obj<</Type/Pages/Count 1/Kids[4 0 R]>>endobj\n4 0 obj<</Type/Page/Parent 3 0 R/MediaBox[0 0 612 792]/Resources<</Font<</F1 5 0 R>>>>/Contents 6 0 R>>endobj\n5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n6 0 obj<</Length " + stream.length() + ">>stream\n" + stream + "\nendstream\nendobj\ntrailer<</Root 2 0 R>>\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }
    private <T> PageView<T> page(Page<T> p) { return new PageView<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize()); }
    private PaymentView view(PaymentRecord p) { return new PaymentView(p.getId(), p.getCustomer().getId(), id(p.getServiceRequest()), id(p.getAmcContract()), id(p.getInvoice()), p.getAmount(), p.getCurrency(), p.getPurpose(), p.getStatus(), p.getProviderReference(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(), p.getRazorpayPaymentLinkId(), p.getGatewayStatus(), p.getGatewaySyncedAt(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt()); }
    private RefundView view(PaymentRefund r) { return new RefundView(r.getId(), r.getPayment().getId(), r.getAmount(), r.getCurrency(), r.getStatus(), r.getRazorpayRefundId(), r.getGatewayStatus(), r.getReason(), r.getCreatedAt(), r.getUpdatedAt()); }
    private InvoiceView view(Invoice i) { return new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getCustomer().getId(), id(i.getServiceRequest()), id(i.getAmcContract()), i.getDescription(), i.getSubtotal(), i.getTaxAmount(), i.getTotalAmount(), i.getCurrency(), i.getStatus(), i.getIssuedDate(), i.getDueDate(), i.getCreatedAt(), i.getUpdatedAt()); }
    private SupportTicketView view(SupportTicket t) { return new SupportTicketView(t.getId(), t.getTicketReference(), t.getCreatedBy().getId(), t.getCreatedBy().getRole().name(), id(t.getServiceRequest()), t.getCategory(), t.getPriority(), t.getStatus(), t.getSubject(), t.getDescription(), t.getPreferredContact(), isAdmin(identities.actor()) ? t.getAdminNotes() : null, t.getCreatedAt(), t.getUpdatedAt()); }
    private RenewalView view(AmcRenewalRequest r) { return new RenewalView(r.getId(), r.getContract().getId(), r.getCustomer().getId(), r.getStatus(), r.getRequestedStartDate(), r.getRequestedEndDate(), r.getQuotedAmount(), r.getCurrency(), id(r.getInvoice()), id(r.getPayment()), r.getCustomerNotes(), isAdmin(identities.actor()) ? r.getAdminNotes() : null, r.getCreatedAt(), r.getUpdatedAt()); }
    private AssetDocumentView view(AssetDocument d) { return new AssetDocumentView(d.getId(), d.getOwnerType(), d.getOwnerId(), d.getOriginalFilename(), d.getContentType(), d.getFileSize(), d.getUploadedBy().getId(), d.getCreatedAt()); }
    private Long id(Object entity) {
        if (entity == null) return null;
        if (entity instanceof ServiceRequest r) return r.getId();
        if (entity instanceof AmcContract a) return a.getId();
        if (entity instanceof Invoice i) return i.getId();
        if (entity instanceof PaymentRecord p) return p.getId();
        return null;
    }
    private String renewalSummary(AmcRenewalRequest r) {
        return "amc=" + r.getContract().getId()
                + ",customer=" + r.getCustomer().getId()
                + ",status=" + r.getStatus()
                + ",requestedStartDate=" + r.getRequestedStartDate()
                + ",requestedEndDate=" + r.getRequestedEndDate()
                + ",quotedAmount=" + r.getQuotedAmount()
                + ",invoice=" + id(r.getInvoice())
                + ",payment=" + id(r.getPayment());
    }
    private static CommerceException missing(String name) { return new CommerceException(404, name + " not found"); }
    private static CommerceException invalidCashOtp() { return new CommerceException(400, "Invalid cash payment OTP"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record Download(String filename, String contentType, Long size, Resource resource) {}
}
