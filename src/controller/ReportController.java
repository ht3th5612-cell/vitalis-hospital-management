package controller;

import dao.InventoryDAO;
import dao.ReportDAO;
import dao.ReportDAO.MedicineUsage;
import dao.ReportDAO.MonthFinance;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import model.DashboardStats;
import model.InventoryItem;
import util.Formats;

import java.util.List;

public class ReportController {

    @FXML
    private Label lblIncome;
    @FXML
    private Label lblExpense;
    @FXML
    private Label lblProfit;
    @FXML
    private Label lblLowStock;
    @FXML
    private Label lblExplain;
    @FXML
    private Label lblUpdated;

    @FXML
    private StackPane chartBox;
    @FXML
    private CheckBox chkAllTime;
    @FXML
    private TableView<MedicineUsage> usageTable;
    @FXML
    private TableColumn<MedicineUsage, String> colUsedItem;
    @FXML
    private TableColumn<MedicineUsage, Integer> colUsedQty;
    @FXML
    private TableColumn<MedicineUsage, String> colUsedSales;
    @FXML
    private TableColumn<MedicineUsage, String> colUsedCost;
    @FXML
    private TableView<InventoryItem> lowStockTable;
    @FXML
    private TableColumn<InventoryItem, String> colItem;
    @FXML
    private TableColumn<InventoryItem, Integer> colStock;
    @FXML
    private TableColumn<InventoryItem, Integer> colReorder;

    private BarChart<String, Number> financeChart;

    @FXML
    private void initialize() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("USD");
        financeChart = new BarChart<>(xAxis, yAxis);
        financeChart.setAnimated(false);
        chartBox.getChildren().setAll(financeChart);

        colUsedItem.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));
        colUsedQty.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().quantity()));
        colUsedSales.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.money(cd.getValue().sales())));
        colUsedCost.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.money(cd.getValue().cost())));
        usageTable.setPlaceholder(new Label("No medicine was used yet."));
        usageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        chkAllTime.selectedProperty().addListener((obs, oldValue, newValue) -> refresh());

        colItem.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getItemName()));
        colStock.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getStockQuantity()));
        colReorder.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getReorderLevel()));
        lowStockTable.setPlaceholder(new Label("No low-stock items."));
        lowStockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            List<MonthFinance> months = ReportDAO.monthlyFinance();
            MonthFinance now = months.get(months.size() - 1);
            lblIncome.setText(Formats.money(now.income()));
            lblExpense.setText(Formats.money(now.expense()));
            lblProfit.setText(Formats.money(now.profit()));
            DashboardStats stats = ReportDAO.loadStats();
            lblLowStock.setText(String.valueOf(stats.getLowStockItems()));
            lblExplain.setText("Income = paid bills.  Expenses = salaries of the active staff (" + Formats.money(now.salary())
                    + " a month) + the cost of the medicines that were used.  Profit = income - expenses.");

            XYChart.Series<String, Number> income = new XYChart.Series<>();
            income.setName("Income");
            XYChart.Series<String, Number> salary = new XYChart.Series<>();
            salary.setName("Salaries");
            XYChart.Series<String, Number> medicine = new XYChart.Series<>();
            medicine.setName("Medicine cost");
            for (MonthFinance month : months) {
                income.getData().add(new XYChart.Data<>(month.label(), month.income()));
                salary.getData().add(new XYChart.Data<>(month.label(), month.salary()));
                medicine.getData().add(new XYChart.Data<>(month.label(), month.medicineCost()));
            }
            financeChart.getData().setAll(List.of(income, salary, medicine));

            usageTable.getItems().setAll(ReportDAO.medicineUsage(!chkAllTime.isSelected()));
            lowStockTable.getItems().setAll(InventoryDAO.findLowStock());
            lowStockTable.refresh();
            lblUpdated.setText("Updated " + Formats.dateTime(java.time.LocalDateTime.now()));
        } catch (RuntimeException e) {
            lblUpdated.setText("Could not load reports: " + e.getMessage());
        }
    }
}
