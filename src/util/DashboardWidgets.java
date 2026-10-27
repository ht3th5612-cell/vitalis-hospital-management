package util;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class DashboardWidgets {

    private static final double ROW_HEIGHT = 46;
    private static final double HEADER_HEIGHT = 36;

    private DashboardWidgets() {
    }

    public static Node kpi(String title, String value, String note, String icon, String tone) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-label");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add("kpi-note");

        VBox texts = new VBox(3, titleLabel, valueLabel, noteLabel);
        texts.setAlignment(Pos.CENTER_LEFT);
        StackPane tile = new StackPane(IconFactory.statIcon(icon, 34));
        tile.getStyleClass().addAll("stat-icon-tile", "tile-" + tone);
        HBox card = new HBox(14, tile, texts);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    public static GridPane row(double[] percents, Node... nodes) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        for (int i = 0; i < nodes.length; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(percents[i]);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
            grid.add(nodes[i], i, 0);
            GridPane.setFillWidth(nodes[i], true);
            GridPane.setFillHeight(nodes[i], true);
        }
        return grid;
    }

    public static GridPane evenRow(Node... nodes) {
        double[] percents = new double[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            percents[i] = 100.0 / nodes.length;
        }
        return row(percents, nodes);
    }

    public static VBox panel(String title, String badge, Node body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox head = new HBox(8, titleLabel, grow);
        head.setAlignment(Pos.CENTER_LEFT);
        if (badge != null) {
            Label badgeLabel = new Label(badge);
            badgeLabel.getStyleClass().addAll("pill", "pill-slate");
            head.getChildren().add(badgeLabel);
        }
        VBox card = new VBox(12, head, body);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(body, Priority.ALWAYS);
        return card;
    }

    public static Node areaChart(Map<String, Integer> values, String seriesName) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelGap(6);
        NumberAxis yAxis = new NumberAxis();
        int max = values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double unit = Math.max(1, Math.ceil(max / 4.0));
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(unit * 4);
        yAxis.setTickUnit(unit);
        yAxis.setMinorTickVisible(false);

        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.getStyleClass().add("dash-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(250);
        chart.setMinHeight(200);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        values.forEach((label, count) -> series.getData().add(new XYChart.Data<>(label, count)));
        chart.getData().add(series);
        return chart;
    }

    public static Node donut(Map<String, Integer> values, Function<String, String> colorOf,
                             String centerBig, String centerSmall) {
        List<PieChart.Data> slices = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            total += entry.getValue();
            PieChart.Data slice = new PieChart.Data(entry.getKey(), entry.getValue());
            String color = colorOf.apply(entry.getKey());
            slice.nodeProperty().addListener((o, was, node) -> {
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + color + ";");
                }
            });
            slices.add(slice);
        }
        if (total == 0) {
            PieChart.Data empty = new PieChart.Data("None", 1);
            empty.nodeProperty().addListener((o, was, node) -> {
                if (node != null) {
                    node.setStyle("-fx-pie-color: #E2E8F0;");
                }
            });
            slices.add(empty);
        }

        PieChart pie = new PieChart();
        pie.getStyleClass().add("donut");
        pie.setLabelsVisible(false);
        pie.setLegendVisible(false);
        pie.setAnimated(false);
        pie.setStartAngle(90);
        pie.setPrefSize(190, 190);
        pie.setMinSize(190, 190);
        pie.setMaxSize(190, 190);
        pie.setData(javafx.collections.FXCollections.observableArrayList(slices));

        Circle hole = new Circle(60);
        hole.getStyleClass().add("donut-hole");
        Label big = new Label(centerBig);
        big.getStyleClass().add("donut-big");
        Label small = new Label(centerSmall);
        small.getStyleClass().add("kpi-note");
        VBox center = new VBox(0, big, small);
        center.setAlignment(Pos.CENTER);
        center.setMouseTransparent(true);
        StackPane ring = new StackPane(pie, hole, center);
        ring.setMaxSize(190, 190);
        ring.setAlignment(Pos.CENTER);

        VBox legend = new VBox(8);
        legend.setAlignment(Pos.CENTER);
        legend.setMaxWidth(260);
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            Region dot = new Region();
            dot.getStyleClass().add("legend-dot");
            dot.setStyle("-fx-background-color: " + colorOf.apply(entry.getKey()) + ";");
            Label name = new Label(entry.getKey());
            name.getStyleClass().add("muted");
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            Label count = new Label(String.valueOf(entry.getValue()));
            count.getStyleClass().add("legend-value");
            HBox line = new HBox(8, dot, name, grow, count);
            line.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(line);
        }

        VBox box = new VBox(14, ring, legend);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    public static String statusColor(String status) {
        return switch (status) {
            case "Scheduled" -> "#F59E0B";
            case "Admitted" -> "#3B82F6";
            case "Completed" -> "#16A34A";
            case "Discharged" -> "#94A3B8";
            case "Cancelled" -> "#EF4444";
            case "Occupied" -> "#16A34A";
            case "Available" -> "#D9E2EC";
            default -> "#6366F1";
        };
    }

    public static String pillClass(String text) {
        String value = text == null ? "" : text.toLowerCase();
        return switch (value) {
            case "scheduled", "expiring", "low stock", "pending" -> "pill-amber";
            case "admitted", "inpatient" -> "pill-blue";
            case "completed", "outpatient", "ok", "paid" -> "pill-green";
            case "cancelled", "out of stock", "expired" -> "pill-red";
            default -> "pill-slate";
        };
    }

    public static final class Column<T> {
        private final String title;
        private final Function<T, String> text;
        private final String kind;

        private Column(String title, Function<T, String> text, String kind) {
            this.title = title;
            this.text = text;
            this.kind = kind;
        }

        public static <T> Column<T> text(String title, Function<T, String> text) {
            return new Column<>(title, text, "text");
        }

        public static <T> Column<T> pill(String title, Function<T, String> text) {
            return new Column<>(title, text, "pill");
        }

        public static <T> Column<T> person(String title, Function<T, String> text) {
            return new Column<>(title, text, "person");
        }
    }

    @SafeVarargs
    public static <T> TableView<T> table(List<T> rows, String emptyText, Column<T>... columns) {
        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label(emptyText));
        for (Column<T> column : columns) {
            TableColumn<T, String> tableColumn = new TableColumn<>(column.title);
            tableColumn.setSortable(false);
            tableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(column.text.apply(cd.getValue())));
            tableColumn.setPrefWidth(column.kind.equals("person") ? 200 : (column.kind.equals("pill") ? 120 : 130));
            if (column.kind.equals("pill")) {
                tableColumn.setMinWidth(112);
            }
            if (!column.kind.equals("text")) {
                tableColumn.setCellFactory(col -> new StyledCell<>(column.kind));
            }
            table.getColumns().add(tableColumn);
        }
        table.getItems().setAll(rows);
        double height = HEADER_HEIGHT + Math.max(rows.size(), 2) * ROW_HEIGHT + 8;
        table.setPrefHeight(height);
        table.setMinHeight(height);
        table.setMaxHeight(height);
        return table;
    }

    private static final class StyledCell<T> extends TableCell<T, String> {
        private final String kind;

        StyledCell(String kind) {
            this.kind = kind;
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setText(null);
            if (kind.equals("pill")) {
                Label pill = new Label(item);
                pill.getStyleClass().addAll("pill", pillClass(item));
                setGraphic(pill);
            } else {
                Label name = new Label(item);
                HBox person = new HBox(10, AvatarView.create(30, item, null, true), name);
                person.setAlignment(Pos.CENTER_LEFT);
                setGraphic(person);
            }
        }
    }
}
