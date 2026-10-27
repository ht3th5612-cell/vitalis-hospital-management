package util;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Popup;

import java.util.function.Consumer;

public final class EmojiPicker {

    private static final int[] EMOJI = {
            0x1F600, 0x1F603, 0x1F604, 0x1F601, 0x1F606, 0x1F605, 0x1F602, 0x1F642,
            0x1F609, 0x1F60A, 0x1F607, 0x1F60D, 0x1F618, 0x1F60E, 0x1F914, 0x1F610,
            0x1F62E, 0x1F62F, 0x1F634, 0x1F622, 0x1F62D, 0x1F620, 0x1F621, 0x1F631,
            0x1F44D, 0x1F44E, 0x1F44F, 0x1F64F, 0x1F44C, 0x270C, 0x1F4AA, 0x1F44B,
            0x2764, 0x1F499, 0x1F49A, 0x1F494, 0x1F389, 0x1F38A, 0x1F525, 0x2B50,
            0x2705, 0x274C, 0x26A0, 0x2753, 0x2757, 0x1F4AF, 0x1F4A4, 0x1F4A1,
            0x1F3E5, 0x1F691, 0x1F489, 0x1F48A, 0x1F321, 0x1FA7A, 0x1FA79, 0x1F9EA,
            0x1F4DE, 0x1F4C5, 0x23F0, 0x1F4CB, 0x1F4CC, 0x1F4CE, 0x1F512, 0x1F3C1
    };
    private static final int COLUMNS = 8;
    private static final double CELL = 40;

    private EmojiPicker() {
    }

    public static void show(Node anchor, Consumer<String> onPick) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        TilePane grid = new TilePane(2, 2);
        grid.setPrefColumns(COLUMNS);
        grid.getStyleClass().add("emoji-grid");
        for (int code : EMOJI) {
            String symbol = new String(Character.toChars(code));
            Button cell = new Button(symbol);
            cell.getStyleClass().add("emoji-cell");
            cell.setPrefSize(CELL, CELL);
            cell.setFocusTraversable(false);
            cell.setOnAction(e -> onPick.accept(symbol));
            grid.getChildren().add(cell);
        }
        ScrollPane box = new ScrollPane(grid);
        box.getStyleClass().add("emoji-popup");
        box.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        box.setFitToWidth(true);
        double width = COLUMNS * (CELL + 2) + 34;
        double height = 5 * (CELL + 2) + 20;
        box.setPrefSize(width, height);
        var css = EmojiPicker.class.getResource("/view/style.css");
        if (css != null) {
            box.getStylesheets().add(css.toExternalForm());
        }
        popup.getContent().add(box);

        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMinX(), bounds.getMinY() - height - 8);
    }
}
