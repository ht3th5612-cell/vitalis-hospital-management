package util;

import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import model.Billing;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Receipt {

    private static final int WIDTH = 44;
    private static final String LINE = "-".repeat(WIDTH);

    private Receipt() {
    }

    public static String text(Billing bill) {
        return text(List.of(bill));
    }

    public static String text(List<Billing> bills) {
        Billing first = bills.get(0);
        double total = 0;
        boolean allPaid = true;
        for (Billing bill : bills) {
            total += bill.getTotalAmount();
            allPaid &= "PAID".equalsIgnoreCase(bill.getPaymentStatus());
        }
        List<String> lines = new ArrayList<>();
        lines.add(center("VITALIS HOSPITAL"));
        lines.add(center("Hospital Management System"));
        lines.add(LINE);
        lines.add(row("Receipt No", String.format("R-%06d", first.getBillingId())));
        lines.add(row("Bill date", Formats.dateTime(first.getCreatedAt())));
        lines.add(row("Printed on", Formats.dateTime(LocalDateTime.now())));
        lines.add(row("Patient", first.getPatientName() + " (P-" + String.format("%04d", first.getPatientId()) + ")"));
        if (first.getBilledByName() != null) {
            lines.add(row("Billed by", first.getBilledByName()));
        }
        lines.add(LINE);
        String head = "Description";
        lines.add(head + " ".repeat(WIDTH - head.length() - 6) + "Amount");
        for (Billing bill : bills) {
            String amount = String.format("%.2f", bill.getTotalAmount());
            List<String> parts = wrap(bill.getDescription(), WIDTH - amount.length() - 3);
            for (int i = 0; i < parts.size(); i++) {
                String part = (i == 0 ? "" : "  ") + parts.get(i);
                lines.add(i == 0 ? part + " ".repeat(Math.max(1, WIDTH - part.length() - amount.length())) + amount : part);
            }
        }
        lines.add(LINE);
        String totalText = Formats.money(total);
        lines.add("TOTAL" + " ".repeat(Math.max(1, WIDTH - 5 - totalText.length())) + totalText);
        lines.add(LINE);
        lines.add(row("Status", allPaid ? "PAID" : "NOT PAID YET"));
        if (first.getPaymentMethod() != null) {
            lines.add(row("Payment method", first.getPaymentMethod()));
        }
        lines.add(LINE);
        lines.add(center("Thank you. We wish you good health!"));
        return String.join("\n", lines);
    }

    public static void show(Billing bill) {
        show(List.of(bill));
    }

    public static void show(List<Billing> bills) {
        Text content = new Text(text(bills));
        content.setFont(Font.font("Consolas", 13));
        VBox page = new VBox(content);
        page.setPadding(new Insets(16));
        page.setStyle("-fx-background-color: white;");

        ButtonType print = new ButtonType("Print", ButtonBar.ButtonData.LEFT);
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Receipt R-" + String.format("%06d", bills.get(0).getBillingId()));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(page);
        dialog.getDialogPane().getButtonTypes().setAll(print, close);

        dialog.getDialogPane().lookupButton(print).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            printPage(page);
        });
        dialog.showAndWait();
    }

    private static void printPage(Node page) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.warning("Print", "No printer was found on this computer.");
            return;
        }
        if (job.showPrintDialog(page.getScene().getWindow()) && job.printPage(page)) {
            job.endJob();
        }
    }

    private static String center(String text) {
        return " ".repeat(Math.max(0, (WIDTH - text.length()) / 2)) + text;
    }

    private static String row(String label, String value) {
        String left = String.format("%-15s: ", label);
        return left + " ".repeat(Math.max(1, WIDTH - left.length() - value.length())) + value;
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() + word.length() + (line.length() > 0 ? 1 : 0) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
