package util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

public final class IconFactory {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private static final Map<String, String> NAV = Map.ofEntries(
            Map.entry("/view/dashboard.fxml", "dashboard.gif"),
            Map.entry("/view/records.fxml", "allitems.gif"),
            Map.entry("/view/messages.fxml", "staffchat.gif"),
            Map.entry("/view/patients.fxml", "patients.gif"),
            Map.entry("/view/appointments.fxml", "appointments.gif"),
            Map.entry("/view/billing.fxml", "billing.gif"),
            Map.entry("/view/consultation.fxml", "myclinic.gif"),
            Map.entry("/view/medical-records.fxml", "mypatients.gif"),
            Map.entry("/view/vitals.fxml", "myward.png"),
            Map.entry("/view/departments.fxml", "departments.gif"),
            Map.entry("/view/users.fxml", "staff.gif"),
            Map.entry("/view/rooms.fxml", "rooms.gif"),
            Map.entry("/view/inventory.fxml", "medicines.gif"),
            Map.entry("/view/reports.fxml", "reports.gif"));

    private IconFactory() {
    }

    public static ImageView nav(String fxml) {
        String file = NAV.get(fxml);
        return file == null ? new ImageView() : view("/images/icons/nav/" + file, 26);
    }

    public static ImageView stat(String title, double size) {
        String text = title == null ? "" : title.toLowerCase();
        String file;
        if (text.contains("admitted")) {
            file = "patients-admitted.gif";
        } else if (text.contains("appointments")) {
            file = "appt-today.gif";
        } else if (text.contains("medical records")) {
            file = "profile-record.gif";
        } else if (text.contains("vitals")) {
            file = "dash-clinic-today.gif";
        } else if (text.contains("inventory")) {
            file = "store-carried.gif";
        } else if (text.contains("low stock")) {
            file = "store-waiting.gif";
        } else if (text.contains("expiring")) {
            file = "store-expiring.gif";
        } else if (text.contains("revenue")) {
            file = "dash-collected.gif";
        } else if (text.contains("pending")) {
            file = "billing-outstanding.gif";
        } else {
            file = "dash-patients.gif";
        }
        return view("/images/icons/stat/" + file, size);
    }

    public static ImageView statIcon(String file, double size) {
        return view("/images/icons/stat/" + file, size);
    }

    public static ImageView logo(double size) {
        return view("/images/icons/nav/patients.gif", size);
    }

    private static ImageView view(String path, double size) {
        Image image = CACHE.computeIfAbsent(path + "@" + size, key -> {
            var url = IconFactory.class.getResource(path);
            return url == null ? null : new Image(url.toExternalForm(), size * 2, size * 2, true, true);
        });
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
