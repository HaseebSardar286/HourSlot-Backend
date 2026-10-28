package com.duemet.job.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingVisit;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.job.model.Job;
import com.duemet.job.model.JobMaterial;
import com.duemet.job.repository.JobMaterialRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Printable receipt for a completed job: labour, materials, urgent fee and how it was paid. */
@Service
public class JobReceiptService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final JobService jobService;
    private final JobMaterialRepository materialRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingVisitRepository bookingVisitRepository;

    public JobReceiptService(JobService jobService,
                             JobMaterialRepository materialRepository,
                             BusinessRepository businessRepository,
                             UserRepository userRepository,
                             BookingRepository bookingRepository,
                             BookingVisitRepository bookingVisitRepository) {
        this.jobService = jobService;
        this.materialRepository = materialRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingVisitRepository = bookingVisitRepository;
    }

    public byte[] render(Long jobId, User actor, Long providerBusinessId) {
        Job job = jobService.requireAccessible(actor, jobId, providerBusinessId);
        if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
            throw new IllegalStateException("A receipt is available once the job is completed.");
        }
        Business provider = businessRepository.findById(job.getProviderId()).orElse(null);
        User customer = userRepository.findById(job.getCustomerId()).orElse(null);
        Booking booking = job.getBookingId() == null ? null : bookingRepository.findById(job.getBookingId()).orElse(null);
        BookingVisit visit = job.getBookingId() == null ? null
                : bookingVisitRepository.findByBookingId(job.getBookingId()).orElse(null);
        List<JobMaterial> materials = materialRepository.findByJob(jobId);
        String currency = job.getCurrency() == null ? "PKR" : job.getCurrency().trim();

        BigDecimal materialsTotal = materials.stream().map(JobMaterial::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal urgentFee = visit == null || visit.getUrgentFee() == null ? BigDecimal.ZERO : visit.getUrgentFee();
        BigDecimal total = job.getFinalAmount() != null ? job.getFinalAmount()
                : (job.getEstimatedAmount() == null ? BigDecimal.ZERO : job.getEstimatedAmount()).add(materialsTotal);
        // Whatever the final amount does not attribute to materials or the urgent fee is labour.
        BigDecimal labour = total.subtract(materialsTotal).subtract(urgentFee).max(BigDecimal.ZERO);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 8);

        doc.add(new Paragraph(provider != null ? provider.getName() : "Duemet provider", title));
        if (provider != null && provider.getPhone() != null) {
            doc.add(new Paragraph(provider.getPhone(), body));
        }
        doc.add(new Paragraph("Receipt #JOB-" + job.getId()
                + (booking != null && booking.getPublicCode() != null ? "  (" + booking.getPublicCode() + ")" : ""), bold));
        if (job.getCompletedAt() != null) {
            doc.add(new Paragraph("Completed " + job.getCompletedAt().format(DATE), body));
        }
        doc.add(new Paragraph(" "));
        if (customer != null) {
            doc.add(new Paragraph("Customer: " + displayName(customer), body));
        }
        if (visit != null && visit.getAddressText() != null) {
            doc.add(new Paragraph("Address: " + visit.getAddressText(), body));
        }
        doc.add(new Paragraph("Job: " + job.getTitle(), body));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[]{5, 1.2f, 2, 2});
        table.setWidthPercentage(100);
        header(table, bold, "Item", "Qty", "Price", "Total");
        row(table, body, "Labour", "", "", money(currency, labour));
        for (JobMaterial m : materials) {
            row(table, body, m.getName(), m.getQuantity().stripTrailingZeros().toPlainString(),
                    money(currency, m.getUnitPrice()), money(currency, m.getLineTotal()));
        }
        if (urgentFee.signum() > 0) {
            row(table, body, "Urgent call-out fee", "", "", money(currency, urgentFee));
        }
        PdfPCell totalLabel = new PdfPCell(new Phrase("Total", bold));
        totalLabel.setColspan(3);
        table.addCell(totalLabel);
        PdfPCell totalValue = new PdfPCell(new Phrase(money(currency, total), bold));
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalValue);
        doc.add(table);

        doc.add(new Paragraph(" "));
        String paid = booking != null && "PAID".equalsIgnoreCase(booking.getPaymentStatus())
                ? "Paid" + (booking.getPaymentMethod() != null ? " (" + booking.getPaymentMethod().replace('_', ' ').toLowerCase() + ")" : "")
                : "Payment pending";
        doc.add(new Paragraph(paid, bold));
        if (job.getCustomerConfirmedAt() != null) {
            doc.add(new Paragraph("Work confirmed by customer on " + job.getCustomerConfirmedAt().format(DATE), body));
        }
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Generated by Duemet", muted));
        doc.close();
        return out.toByteArray();
    }

    private static void header(PdfPTable table, Font font, String... labels) {
        for (String label : labels) {
            table.addCell(new PdfPCell(new Phrase(label, font)));
        }
    }

    private static void row(PdfPTable table, Font font, String item, String qty, String price, String total) {
        table.addCell(new PdfPCell(new Phrase(item, font)));
        table.addCell(new PdfPCell(new Phrase(qty, font)));
        PdfPCell priceCell = new PdfPCell(new Phrase(price, font));
        priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(priceCell);
        PdfPCell totalCell = new PdfPCell(new Phrase(total, font));
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalCell);
    }

    private static String money(String currency, BigDecimal amount) {
        return currency + " " + (amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
