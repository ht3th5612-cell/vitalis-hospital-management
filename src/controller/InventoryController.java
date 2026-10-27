package controller;

import dao.InventoryDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.InventoryItem;
import util.AlertUtil;
import util.Formats;
import util.UiFactory;
import util.Validators;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import util.Permissions;

public class InventoryController {

    @FXML
    private TableView<InventoryItem> table;
    @FXML
    private TableColumn<InventoryItem, Integer> colId;
    @FXML
    private TableColumn<InventoryItem, String> colItem;
    @FXML
    private TableColumn<InventoryItem, String> colCategory;
    @FXML
    private TableColumn<InventoryItem, String> colPrice;
    @FXML
    private TableColumn<InventoryItem, String> colCost;
    @FXML
    private TableColumn<InventoryItem, String> colStock;
    @FXML
    private TableColumn<InventoryItem, Integer> colReorder;
    @FXML
    private TableColumn<InventoryItem, String> colExpiry;

    @FXML
    private TextField txtSearch;
    @FXML
    private VBox formCard;
    @FXML
    private VBox restockCard;
    @FXML
    private CheckBox chkLowOnly;
    @FXML
    private TextField txtRestockQty;
    @FXML
    private TextField txtRestockCost;
    @FXML
    private TextField txtCost;
    @FXML
    private Button btnDelete;
    @FXML
    private TextField txtItemName;
    @FXML
    private TextField txtCategory;
    @FXML
    private TextField txtPrice;
    @FXML
    private TextField txtStock;
    @FXML
    private TextField txtReorder;
    @FXML
    private DatePicker dpExpiry;
    @FXML
    private Label lblFormTitle;
    @FXML
    private Label lblSummary;
    @FXML
    private Label lblStatus;

    private InventoryItem selectedItem;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getItemId()));
        colItem.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getItemName()));
        colCategory.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCategory()));
        colPrice.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPriceText()));
        colCost.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCostText()));
        colStock.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStockText()));
        colReorder.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getReorderLevel()));
        colExpiry.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.date(cd.getValue().getExpiryDate())));

        table.setPlaceholder(new Label("No inventory items found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("low-stock");
                if (!empty && item != null && item.isLowStock()) {
                    getStyleClass().add("low-stock");
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        chkLowOnly.selectedProperty().addListener((obs, oldValue, newValue) -> refresh());

        formCard.setVisible(Permissions.has(Permissions.INVENTORY_EDIT));
        formCard.setManaged(Permissions.has(Permissions.INVENTORY_EDIT));
        btnDelete.setDisable(!Permissions.has(Permissions.INVENTORY_EDIT));
        restockCard.setVisible(Permissions.has(Permissions.INVENTORY_RESTOCK));
        restockCard.setManaged(Permissions.has(Permissions.INVENTORY_RESTOCK));
        colCost.setVisible(Permissions.has(Permissions.INVENTORY_RESTOCK));

        txtStock.setText("0");
        txtReorder.setText("10");
        txtCost.setText("0");
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            InventoryItem item = new InventoryItem();
            if (selectedItem != null) {
                item.setItemId(selectedItem.getItemId());
            }
            item.setItemName(Validators.requireText(txtItemName.getText(), "Item name"));
            item.setCategory(Validators.requireText(txtCategory.getText(), "Category"));
            item.setUnitPrice(Validators.requireMoney(txtPrice.getText(), "Unit price"));
            item.setUnitCost(Validators.optionalNonNegative(txtCost.getText(), "Unit cost"));
            item.setStockQuantity(Validators.requireInt(txtStock.getText(), "Stock quantity", 0, 1_000_000));
            item.setReorderLevel(Validators.requireInt(txtReorder.getText(), "Reorder level", 0, 1_000_000));
            item.setExpiryDate(dpExpiry.getValue());

            if (selectedItem == null) {
                InventoryDAO.create(item);
                setStatus("Inventory item added.", true);
            } else {
                InventoryDAO.update(item);
                setStatus("Inventory item updated.", true);
            }
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the item");
        }
    }

    @FXML
    private void handleRestock() {
        if (selectedItem == null) {
            AlertUtil.warning("Nothing selected", "Select the item that was delivered in the table first.");
            return;
        }
        try {
            int quantity = Validators.requireInt(txtRestockQty.getText(), "Quantity to add", 1, 1_000_000);
            Double cost = txtRestockCost.getText().isBlank() ? null
                    : Validators.requireMoney(txtRestockCost.getText(), "Cost per unit");
            InventoryDAO.restock(selectedItem.getItemId(), quantity, cost);
            setStatus(quantity + " x " + selectedItem.getItemName() + " added to the stock.", true);
            txtRestockQty.clear();
            txtRestockCost.clear();
            refresh();
            clearForm();
            table.getSelectionModel().clearSelection();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the delivery");
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedItem == null) {
            AlertUtil.warning("Nothing selected", "Select a inventory item from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Delete item",
                "Delete " + selectedItem.getItemName() + " from inventory?")) {
            return;
        }
        try {
            InventoryDAO.delete(selectedItem.getItemId());
            refresh();
            clearForm();
            setStatus("Inventory item deleted.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the item");
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            var items = InventoryDAO.findAll(txtSearch.getText()).stream()
                    .filter(item -> !chkLowOnly.isSelected() || item.isLowStock()).toList();
            table.getItems().setAll(items);
            table.refresh();
            long lowStock = items.stream().filter(InventoryItem::isLowStock).count();
            lblSummary.setText("Items: " + items.size() + "    |    Low stock: " + lowStock);
        } catch (RuntimeException e) {
            setStatus("Could not load inventory: " + e.getMessage(), false);
        }
    }

    private void edit(InventoryItem item) {
        selectedItem = item;
        txtItemName.setText(item.getItemName());
        txtCategory.setText(item.getCategory());
        txtPrice.setText(String.format("%.2f", item.getUnitPrice()));
        txtCost.setText(String.format("%.2f", item.getUnitCost()));
        txtStock.setText(String.valueOf(item.getStockQuantity()));
        txtReorder.setText(String.valueOf(item.getReorderLevel()));
        dpExpiry.setValue(item.getExpiryDate());
        lblFormTitle.setText("Edit item #" + item.getItemId());
        setStatus("", true);
    }

    private void clearForm() {
        selectedItem = null;
        txtItemName.clear();
        txtCategory.clear();
        txtPrice.clear();
        txtCost.setText("0");
        txtStock.setText("0");
        txtReorder.setText("10");
        dpExpiry.setValue(null);
        lblFormTitle.setText("New inventory item");
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
