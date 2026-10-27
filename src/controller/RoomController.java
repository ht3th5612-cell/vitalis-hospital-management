package controller;

import dao.RoomDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.InventoryItem;
import util.AlertUtil;
import util.Formats;
import util.Permissions;
import util.UiFactory;
import util.Validators;

import java.util.Map;

public class RoomController {

    @FXML
    private TableView<InventoryItem> table;
    @FXML
    private TableColumn<InventoryItem, String> colName;
    @FXML
    private TableColumn<InventoryItem, Integer> colBeds;
    @FXML
    private TableColumn<InventoryItem, Integer> colUsed;
    @FXML
    private TableColumn<InventoryItem, Integer> colFree;
    @FXML
    private TableColumn<InventoryItem, String> colPrice;

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtBeds;
    @FXML
    private TextField txtPrice;
    @FXML
    private Label lblFormTitle;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;

    private InventoryItem selected;
    private Map<String, Integer> occupied = Map.of();

    @FXML
    private void initialize() {
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getItemName()));
        colBeds.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getStockQuantity()));
        colUsed.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(used(cd.getValue())));
        colFree.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getStockQuantity() - used(cd.getValue())));
        colPrice.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.money(cd.getValue().getUnitPrice())));

        table.setPlaceholder(new Label("No rooms yet. Add the first room."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });

        boolean canManage = Permissions.has(Permissions.ROOM_MANAGE);
        btnSave.setDisable(!canManage);
        btnDelete.setDisable(!canManage);
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            String name = Validators.requireText(txtName.getText(), "Room name");
            int beds = Validators.requireInt(txtBeds.getText(), "Number of beds", 0, 500);
            double price = Validators.requireMoney(txtPrice.getText(), "Price per day");
            if (selected == null) {
                RoomDAO.create(name, beds, price);
                setStatus("Room " + name + " added.", true);
            } else {
                RoomDAO.update(selected.getItemId(), name, beds, price);
                setStatus("Room " + name + " updated.", true);
            }
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the room");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a room from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Remove room", "Remove room " + selected.getItemName() + "?")) {
            return;
        }
        try {
            RoomDAO.delete(selected.getItemId());
            refresh();
            clearForm();
            setStatus("Room removed.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the room");
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

    private int used(InventoryItem room) {
        return occupied.getOrDefault(room.getItemName(), 0);
    }

    private void refresh() {
        try {
            occupied = RoomDAO.occupiedBeds();
            table.getItems().setAll(RoomDAO.findAll());
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load the rooms: " + e.getMessage(), false);
        }
    }

    private void edit(InventoryItem room) {
        selected = room;
        txtName.setText(room.getItemName());
        txtBeds.setText(String.valueOf(room.getStockQuantity()));
        txtPrice.setText(String.format("%.2f", room.getUnitPrice()));
        lblFormTitle.setText("Edit room " + room.getItemName());
    }

    private void clearForm() {
        selected = null;
        txtName.clear();
        txtBeds.clear();
        txtPrice.clear();
        lblFormTitle.setText("New room");
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
