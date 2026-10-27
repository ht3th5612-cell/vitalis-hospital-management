package controller;

import dao.AppointmentDAO;
import dao.BillingDAO;
import dao.InventoryDAO;
import dao.ReportDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import model.Appointment;
import model.Billing;
import model.DashboardStats;
import model.InventoryItem;
import util.AlertUtil;
import util.Formats;
import util.Permissions;
import util.Receipt;
import util.Session;
import util.UiFactory;
import util.Validators;

import java.util.ArrayList;
import java.util.List;

public class BillingController {

    @FXML
    private TableView<Billing> table;
    @FXML
    private TableColumn<Billing, Integer> colId;
    @FXML
    private TableColumn<Billing, String> colPatient;
    @FXML
    private TableColumn<Billing, String> colDescription;
    @FXML
    private TableColumn<Billing, String> colType;
    @FXML
    private TableColumn<Billing, String> colAmount;
    @FXML
    private TableColumn<Billing, String> colStatus;
    @FXML
    private TableColumn<Billing, String> colMethod;
    @FXML
    private TableColumn<Billing, String> colDate;

    @FXML
    private TextField txtSearch;
    @FXML
    private ScrollPane formCard;
    @FXML
    private ComboBox<Appointment> cmbVisit;
    @FXML
    private ComboBox<InventoryItem> cmbMedicine;
    @FXML
    private TextField txtQuantity;
    @FXML
    private ListView<Billing> lstMedicines;
    @FXML
    private TextArea txtBill;
    @FXML
    private Label lblTotal;
    @FXML
    private ComboBox<String> cmbMethod;
    @FXML
    private Button btnPay;
    @FXML
    private Button btnMarkPaid;
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblSubtitle;
    @FXML
    private Label lblSummary;
    @FXML
    private Label lblStatus;

    private final List<Billing> medicines = new ArrayList<>();

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getBillingId()));
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPatientName()));
        colDescription.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDescription()));
        colType.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBillType()));
        colAmount.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAmountText()));
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPaymentStatus()));
        colMethod.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPaymentMethod()));
        colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().getCreatedAt())));

        cmbMethod.getItems().setAll(BillingDAO.PAYMENT_METHODS);
        cmbMethod.getSelectionModel().selectFirst();

        cmbVisit.setConverter(new StringConverter<>() {
            @Override
            public String toString(Appointment visit) {
                return visit == null ? "" : "#" + visit.getAppointmentId() + " " + visit.getPatientName() + " - "
                        + visit.getDoctorName() + " (" + visit.getStatus() + ")";
            }

            @Override
            public Appointment fromString(String text) {
                return null;
            }
        });
        lstMedicines.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Billing item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });

        table.setPlaceholder(new Label("No bills yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        cmbVisit.valueProperty().addListener((obs, oldValue, newValue) -> {
            medicines.clear();
            lstMedicines.getItems().clear();
            showBill();
        });

        boolean canPay = Permissions.has(Permissions.BILL_CREATE);
        formCard.setVisible(canPay);
        formCard.setManaged(canPay);
        btnMarkPaid.setDisable(!canPay);
        btnMarkPaid.setVisible(canPay);
        btnMarkPaid.setManaged(canPay);
        if (!canPay) {
            lblTitle.setText("Bills");
        }

        loadChoices();
        refresh();
        showBill();
    }

    @FXML
    private void handleAddMedicine() {
        try {
            if (cmbVisit.getValue() == null) {
                throw new IllegalArgumentException("Select the visit first.");
            }
            InventoryItem item = cmbMedicine.getValue();
            if (item == null) {
                throw new IllegalArgumentException("Select a medicine.");
            }
            int quantity = Validators.requireInt(txtQuantity.getText(), "Quantity", 1, 100000);
            Billing request = findRequest(item.getItemId());
            if (request == null) {
                request = new Billing();
                request.setInventoryId(item.getItemId());
                medicines.add(request);
            }
            int before = request.getQuantity() == null ? 0 : request.getQuantity();
            request.setQuantity(before + quantity);
            request.setDescription(item.getItemName() + " x " + request.getQuantity());
            try {
                BillingDAO.preview(cmbVisit.getValue().getAppointmentId(), medicines);
            } catch (RuntimeException e) {
                if (before == 0) {
                    medicines.remove(request);
                } else {
                    request.setQuantity(before);
                    request.setDescription(item.getItemName() + " x " + before);
                }
                throw e;
            }
            lstMedicines.getItems().setAll(medicines);
            txtQuantity.clear();
            showBill();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the medicine");
        }
    }

    @FXML
    private void handleRemoveMedicine() {
        Billing chosen = lstMedicines.getSelectionModel().getSelectedItem();
        if (chosen == null) {
            AlertUtil.warning("Nothing selected", "Select a medicine of the list first.");
            return;
        }
        medicines.remove(chosen);
        lstMedicines.getItems().setAll(medicines);
        showBill();
    }

    @FXML
    private void handlePay() {
        Appointment visit = cmbVisit.getValue();
        if (visit == null) {
            AlertUtil.warning("Nothing selected", "Select the visit to pay first.");
            return;
        }
        try {
            List<Billing> lines = BillingDAO.preview(visit.getAppointmentId(), medicines);
            double total = 0;
            for (Billing line : lines) {
                total += line.getTotalAmount();
            }
            String note = "Admitted".equals(visit.getStatus()) ? "\nThe patient will be discharged." : "";
            if (!AlertUtil.confirm("Take payment", visit.getPatientName() + " pays " + Formats.money(total) + " ("
                    + cmbMethod.getValue() + ")." + note + "\nA paid bill cannot be changed. Continue?")) {
                return;
            }
            BillingDAO.checkout(visit.getAppointmentId(), cmbMethod.getValue(), medicines,
                    Session.getUser() == null ? 0 : Session.getUser().getUserId());
            List<Billing> saved = BillingDAO.findByVisit(visit.getAppointmentId());
            setStatus("Payment of " + Formats.money(total) + " received from " + visit.getPatientName() + ".", true);
            handleClear();
            refresh();
            loadChoices();
            Receipt.show(saved);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the payment");
        }
    }

    @FXML
    private void handleReceipt() {
        Billing selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a bill line from the table first.");
            return;
        }
        if (!"Paid".equals(selected.getPaymentStatus())) {
            AlertUtil.warning("No receipt yet", "A receipt is available only after the bill is paid.");
            return;
        }
        Receipt.show(BillingDAO.receiptLines(selected));
    }

    @FXML
    private void handleMarkPaid() {
        Billing selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a pending bill line from the table first.");
            return;
        }
        try {
            BillingDAO.markPaid(selected.getBillingId(), cmbMethod.getValue());
            setStatus("Bill #" + selected.getBillingId() + " marked as paid.", true);
            refresh();
            Receipt.show(BillingDAO.receiptLines(BillingDAO.findById(selected.getBillingId())));
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the bill");
        }
    }

    @FXML
    private void handleClear() {
        medicines.clear();
        lstMedicines.getItems().clear();
        cmbVisit.getSelectionModel().clearSelection();
        cmbMedicine.getSelectionModel().clearSelection();
        txtQuantity.clear();
        cmbMethod.getSelectionModel().selectFirst();
        showBill();
    }

    @FXML
    private void handleRefresh() {
        loadChoices();
        refresh();
        showBill();
    }

    private Billing findRequest(int itemId) {
        for (Billing request : medicines) {
            if (request.getInventoryId() != null && request.getInventoryId() == itemId) {
                return request;
            }
        }
        return null;
    }

    private void loadChoices() {
        try {
            UiFactory.refillKeepingChoice(cmbVisit, AppointmentDAO.findToPay(),
                    (a, b) -> a.getAppointmentId() == b.getAppointmentId());
            cmbMedicine.getItems().setAll(InventoryDAO.findAll("").stream()
                    .filter(item -> item.getStockQuantity() > 0).toList());
        } catch (RuntimeException e) {
            setStatus("Could not load the visits or the medicines: " + e.getMessage(), false);
        }
    }

    private void showBill() {
        Appointment visit = cmbVisit.getValue();
        if (visit == null) {
            txtBill.setText("Select a visit to see its bill.");
            lblTotal.setText("Total: " + Formats.money(0));
            btnPay.setDisable(true);
            return;
        }
        try {
            List<Billing> lines = BillingDAO.preview(visit.getAppointmentId(), medicines);
            StringBuilder text = new StringBuilder();
            double total = 0;
            for (Billing line : lines) {
                text.append(line.getDescription()).append("  =  ").append(Formats.money(line.getTotalAmount())).append('\n');
                total += line.getTotalAmount();
            }
            if ("Admitted".equals(visit.getStatus())) {
                text.append("(Room days are counted until now. The patient is discharged when you pay.)");
            }
            txtBill.setText(text.toString());
            lblTotal.setText("Total: " + Formats.money(total));
            btnPay.setDisable(false);
        } catch (RuntimeException e) {
            txtBill.setText(e.getMessage());
            lblTotal.setText("Total: " + Formats.money(0));
            btnPay.setDisable(true);
        }
    }

    private void refresh() {
        try {
            table.getItems().setAll(BillingDAO.findAll(txtSearch.getText()));
            table.refresh();
            DashboardStats stats = ReportDAO.loadStats();
            lblSummary.setText("Collected: " + Formats.money(stats.getRevenue())
                    + "    |    Pending (old) bills: " + stats.getPendingBills());
        } catch (RuntimeException e) {
            setStatus("Could not load bills: " + e.getMessage(), false);
        }
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
