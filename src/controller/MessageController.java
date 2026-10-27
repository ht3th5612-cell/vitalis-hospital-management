package controller;

import dao.MessageDAO;
import dao.MessageDAO.Contact;
import dao.UserDAO;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import model.Message;
import util.AvatarView;
import util.ChatContent;
import util.ChatFiles;
import util.EmojiPicker;
import util.Session;
import util.UiFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class MessageController {

    private static final int POLL_SECONDS = 4;
    private static final int CACHE_LIMIT = 3 * 1024 * 1024;

    @FXML
    private TextField txtSearch;
    @FXML
    private ListView<Contact> contactList;
    @FXML
    private Label lblChatTitle;
    @FXML
    private HBox pinnedBar;
    @FXML
    private Label lblPinnedText;
    @FXML
    private Label lblPinnedCount;
    @FXML
    private ScrollPane chatScroll;
    @FXML
    private VBox chatBox;
    @FXML
    private HBox inputBar;
    @FXML
    private Button btnAttach;
    @FXML
    private Button btnEmoji;
    @FXML
    private TextField txtMessage;
    @FXML
    private Button btnSend;
    @FXML
    private Label lblStatus;

    private final Map<Integer, Optional<byte[]>> photos = new HashMap<>();
    private final Map<Integer, byte[]> files = new HashMap<>();
    private final Map<Integer, Node> rows = new HashMap<>();
    private List<Contact> contacts = List.of();
    private Contact selected;
    private List<Message> shown = List.of();
    private List<Message> pinned = List.of();
    private int pinnedIndex;
    private boolean refreshing;
    private boolean attached;
    private final Timeline poll = new Timeline();

    @FXML
    private void initialize() {
        contactList.setCellFactory(list -> new ContactCell());
        contactList.setPlaceholder(new Label("Nobody found."));
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                open(newValue);
            }
        });
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> showContacts());
        txtMessage.setOnAction(e -> handleSend());
        setChatEnabled(false);

        poll.getKeyFrames().setAll(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> tick()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        loadContacts();
    }

    @FXML
    private void handleSend() {
        if (selected == null) {
            return;
        }
        try {
            MessageDAO.send(selected.userId(), txtMessage.getText());
            txtMessage.clear();
            sent();
        } catch (RuntimeException e) {
            UiFactory.setStatus(lblStatus, e.getMessage(), false);
        }
    }

    @FXML
    private void handleAttach() {
        File file = ChatFiles.chooseMedia(window());
        if (file == null) {
            return;
        }
        if (ChatFiles.isPhoto(file)) {
            sendFile(Message.IMAGE, ChatFiles.photoName(file), () -> ChatFiles.photoBytes(file));
        } else {
            sendFile(Message.VIDEO, file.getName(), () -> ChatFiles.videoBytes(file));
        }
    }

    @FXML
    private void handleEmoji() {
        EmojiPicker.show(btnEmoji, symbol -> {
            txtMessage.insertText(txtMessage.getCaretPosition(), symbol);
            txtMessage.requestFocus();
        });
    }

    @FXML
    private void handlePinnedClick() {
        if (pinned.isEmpty()) {
            return;
        }
        scrollTo(pinned.get(pinnedIndex).getMessageId());
        pinnedIndex = (pinnedIndex - 1 + pinned.size()) % pinned.size();
        showPinnedText();
    }

    private void sendFile(String kind, String name, Supplier<byte[]> content) {
        if (selected == null) {
            return;
        }
        try {
            MessageDAO.sendFile(selected.userId(), kind, name, content.get(), txtMessage.getText());
            txtMessage.clear();
            sent();
        } catch (RuntimeException e) {
            UiFactory.setStatus(lblStatus, e.getMessage(), false);
        }
    }

    private void sent() {
        lblStatus.setText("");
        loadConversation();
        loadContacts();
    }

    private Window window() {
        return btnSend.getScene().getWindow();
    }

    private void tick() {
        if (chatBox.getScene() != null) {
            attached = true;
        } else if (attached) {
            poll.stop();
            return;
        }
        try {
            loadContacts();
            if (selected != null) {
                loadConversation();
            }
        } catch (RuntimeException e) {
            // the next check tries again
        }
    }

    private void loadContacts() {
        try {
            contacts = MessageDAO.findContacts();
            showContacts();
        } catch (RuntimeException e) {
            UiFactory.setStatus(lblStatus, "Could not load the staff list: " + e.getMessage(), false);
        }
    }

    private void showContacts() {
        String term = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        List<Contact> visible = contacts.stream()
                .filter(c -> term.isEmpty() || c.name().toLowerCase().contains(term)).toList();
        refreshing = true;
        try {
            contactList.getItems().setAll(visible);
            if (selected != null) {
                visible.stream().filter(c -> c.userId() == selected.userId()).findFirst()
                        .ifPresent(c -> contactList.getSelectionModel().select(c));
            }
        } finally {
            refreshing = false;
        }
    }

    private void open(Contact contact) {
        selected = contact;
        shown = List.of();
        lblChatTitle.setText(contact.name() + "  -  " + UiFactory.roleTitle(contact.role())
                + (contact.department() == null ? "" : " (" + contact.department() + ")"));
        setChatEnabled(true);
        lblStatus.setText("");
        loadConversation();
        txtMessage.requestFocus();
    }

    private void setChatEnabled(boolean enabled) {
        for (Node control : List.of(txtMessage, btnSend, btnAttach, btnEmoji)) {
            control.setDisable(!enabled);
        }
        if (!enabled) {
            chatBox.getChildren().clear();
            pinned = List.of();
            showPinnedBar();
        }
    }

    private void loadConversation() {
        if (selected == null) {
            return;
        }
        int other = selected.userId();
        List<Message> messages = MessageDAO.findConversation(other);
        boolean unreadFromThem = messages.stream().anyMatch(m -> m.getSenderId() == other && !m.isRead());
        if (unreadFromThem) {
            MessageDAO.markRead(other);
            messages = MessageDAO.findConversation(other);
            loadContacts();
        }
        if (sameAsShown(messages)) {
            return;
        }
        boolean newMessages = shown.isEmpty() || messages.size() != shown.size();
        double position = chatScroll.getVvalue();
        shown = messages;
        rows.clear();
        chatBox.getChildren().setAll(messages.stream().map(this::bubble).toList());
        pinned = MessageDAO.findPinned(other);
        pinnedIndex = pinned.size() - 1;
        showPinnedBar();
        Platform.runLater(() -> chatScroll.setVvalue(newMessages ? 1.0 : position));
    }

    private boolean sameAsShown(List<Message> messages) {
        if (messages.size() != shown.size()) {
            return false;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageId() != shown.get(i).getMessageId()
                    || messages.get(i).isRead() != shown.get(i).isRead()
                    || messages.get(i).isPinned() != shown.get(i).isPinned()) {
                return false;
            }
        }
        return true;
    }

    private void showPinnedBar() {
        pinnedBar.setVisible(!pinned.isEmpty());
        pinnedBar.setManaged(!pinned.isEmpty());
        if (!pinned.isEmpty()) {
            showPinnedText();
        }
    }

    private void showPinnedText() {
        lblPinnedText.setText(preview(pinned.get(pinnedIndex)));
        lblPinnedCount.setText((pinnedIndex + 1) + " / " + pinned.size());
    }

    private String preview(Message message) {
        String label = switch (message.getKind()) {
            case Message.IMAGE -> "[Photo] ";
            case Message.VIDEO -> "[Video] ";
            default -> "";
        };
        String text = label + message.getBody();
        return text.length() > 90 ? text.substring(0, 90) + "..." : text.trim();
    }

    private void scrollTo(int messageId) {
        Node row = rows.get(messageId);
        double hidden = chatBox.getHeight() - chatScroll.getViewportBounds().getHeight();
        if (row != null && hidden > 0) {
            chatScroll.setVvalue(Math.max(0, Math.min(1, (row.getBoundsInParent().getMinY() - 8) / hidden)));
        }
    }

    private byte[] fileOf(int messageId) {
        byte[] cached = files.get(messageId);
        if (cached != null) {
            return cached;
        }
        byte[] data = MessageDAO.findFile(messageId);
        if (data.length <= CACHE_LIMIT) {
            files.put(messageId, data);
        }
        return data;
    }

    private HBox bubble(Message message) {
        boolean mine = message.getSenderId() == Session.getUser().getUserId();
        String style = mine ? "chat-bubble-me" : "chat-bubble-other";
        Pos side = mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;

        VBox bubble = new VBox(6);
        bubble.getStyleClass().add(style);
        Node content = fileContent(message);
        if (content != null) {
            bubble.getChildren().add(content);
            bubble.getStyleClass().add("chat-bubble-media");
        }
        if (!message.getBody().isEmpty()) {
            Label text = new Label(message.getBody());
            text.setWrapText(true);
            text.setMaxWidth(420);
            bubble.getChildren().add(text);
        }
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        LocalDate day = message.getSentAt().toLocalDate();
        String time = (day.equals(LocalDate.now()) ? "" : util.Formats.date(day) + " ") + util.Formats.time(message.getSentAt().toLocalTime())
                + (mine && message.isRead() ? "  ✓ Seen" : "");
        Label stamp = new Label(time);
        stamp.getStyleClass().add("chat-time");
        Button pin = new Button(message.isPinned() ? "Unpin" : "Pin");
        pin.getStyleClass().add("chat-pin-link");
        pin.setOnAction(e -> togglePin(message));
        HBox footer = new HBox(8, stamp, pin);
        footer.setAlignment(side);

        VBox box = new VBox(2);
        box.setAlignment(side);
        if (message.isPinned()) {
            Label tag = new Label("Pinned");
            tag.getStyleClass().add("chat-pin-tag");
            box.getChildren().add(tag);
        }
        box.getChildren().addAll(bubble, footer);
        HBox row = new HBox(box);
        row.setAlignment(side);
        rows.put(message.getMessageId(), row);
        return row;
    }

    private Node fileContent(Message message) {
        int id = message.getMessageId();
        try {
            return switch (message.getKind()) {
                case Message.IMAGE -> ChatContent.photo(message, fileOf(id));
                case Message.VIDEO -> ChatContent.video(message, () -> fileOf(id));
                default -> null;
            };
        } catch (RuntimeException e) {
            return new Label("This file could not be loaded.");
        }
    }

    private void togglePin(Message message) {
        try {
            MessageDAO.setPinned(message.getMessageId(), !message.isPinned());
            lblStatus.setText("");
            loadConversation();
        } catch (RuntimeException e) {
            UiFactory.setStatus(lblStatus, e.getMessage(), false);
        }
    }

    private final class ContactCell extends ListCell<Contact> {
        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            if (empty || contact == null) {
                setGraphic(null);
                return;
            }
            byte[] photo = photos.computeIfAbsent(contact.userId(), id -> Optional.ofNullable(UserDAO.findPhoto(id))).orElse(null);
            Label name = new Label(contact.name());
            name.getStyleClass().add("chat-name");
            String preview = contact.lastMessage() == null ? UiFactory.roleTitle(contact.role()) : contact.lastMessage();
            Label last = new Label(preview.length() > 34 ? preview.substring(0, 34) + "..." : preview);
            last.getStyleClass().add(contact.unread() > 0 ? "chat-preview-new" : "chat-preview");
            VBox texts = new VBox(1, name, last);
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            HBox row = new HBox(10, AvatarView.create(38, contact.name(), photo, true), texts, grow);
            row.setAlignment(Pos.CENTER_LEFT);
            if (contact.unread() > 0) {
                Label badge = new Label(String.valueOf(contact.unread()));
                badge.getStyleClass().add("nav-badge");
                row.getChildren().add(badge);
            }
            setGraphic(row);
        }
    }
}
