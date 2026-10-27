USE hospital_db;

CREATE TABLE messages (
    message_id  INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    sent_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at     DATETIME NULL,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    INDEX idx_message_receiver (receiver_id, read_at),
    INDEX idx_message_pair (sender_id, receiver_id)
) ENGINE = InnoDB;
