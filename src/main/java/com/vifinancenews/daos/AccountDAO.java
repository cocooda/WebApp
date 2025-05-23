package com.vifinancenews.daos;

import com.vifinancenews.config.DatabaseConfig;
import com.vifinancenews.models.Account;
import com.vifinancenews.utilities.IDHash;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountDAO {

    public static Account insertAccount(UUID identifierId, String userName, String avatarLink, String bio) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);

        String query = "INSERT INTO account (user_id, username, avatar_link, bio) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, hashedId);
            pstmt.setString(2, userName);
            pstmt.setString(3, avatarLink);
            pstmt.setString(4, bio);

            int rowsInserted = pstmt.executeUpdate();
            System.out.println("Rows inserted into account: " + rowsInserted);
            if (rowsInserted > 0) {
                return new Account(hashedId, userName, avatarLink, bio);
            }
        }
        return null;
    }

    public static boolean deleteAccountByUserId(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);
        String query = "DELETE FROM account WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, hashedId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public static boolean moveAccountToDeleted(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);

        String insertQuery = "INSERT INTO deleted_accounts (user_id, username, avatar_link, bio, deleted_at) " +
                             "SELECT user_id, username, avatar_link, bio, CURRENT_TIMESTAMP FROM account WHERE user_id = ?";
        String deleteQuery = "DELETE FROM account WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {

                insertStmt.setString(1, hashedId);
                deleteStmt.setString(1, hashedId);

                int inserted = insertStmt.executeUpdate();
                int deleted = deleteStmt.executeUpdate();

                if (inserted > 0 && deleted > 0) {
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            }
        }
    }

    public static boolean isAccountInDeleted(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);
        String query = "SELECT COUNT(*) FROM deleted_accounts WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, hashedId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public static boolean restoreUserById(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);

        String restoreQuery = "INSERT INTO account (user_id, username, avatar_link, bio) " +
                              "SELECT user_id, username, avatar_link, bio FROM deleted_accounts WHERE user_id = ?";
        String deleteQuery = "DELETE FROM deleted_accounts WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement restoreStmt = conn.prepareStatement(restoreQuery);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {

                restoreStmt.setString(1, hashedId);
                deleteStmt.setString(1, hashedId);

                int restored = restoreStmt.executeUpdate();
                int deleted = deleteStmt.executeUpdate();

                if (restored > 0 && deleted > 0) {
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            }
        }
    }

    public static Optional<LocalDateTime> getDeletedAccountDeletedAt(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);
        String query = "SELECT deleted_at FROM deleted_accounts WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, hashedId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(rs.getTimestamp("deleted_at").toLocalDateTime());
            } else {
                return Optional.empty();
            }
        }
    }

    public static boolean deleteExpiredDeletedAccounts(int days) throws SQLException {
        String query = "DELETE FROM deleted_accounts WHERE deleted_at < NOW() - INTERVAL ? DAY";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, days);
            return stmt.executeUpdate() > 0;
        }
    }

    public static boolean deleteFromDeletedAccounts(UUID identifierId) throws SQLException {
        String hashedId = IDHash.hashUUID(identifierId);
        String query = "DELETE FROM deleted_accounts WHERE user_id = ?";
    
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, hashedId);
            return pstmt.executeUpdate() > 0;
        }
    }
    

    public static Account getAccountByUserId(UUID userId) throws SQLException {
        String hashedUserId = IDHash.hashUUID(userId);
        String query = "SELECT user_id, username, avatar_link, bio FROM account WHERE user_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, hashedUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("avatar_link"),
                        rs.getString("bio")
                    );
                }
            }
        }
        return null;
    }

    public static boolean updateAccount(UUID userId, String userName, String avatarLink, String bio) throws SQLException {
    String hashedUserId = IDHash.hashUUID(userId);

    List<String> updates = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    if (userName != null) {
        updates.add("username = ?");
        params.add(userName);
    }
    if (avatarLink != null) {
        updates.add("avatar_link = ?");
        params.add(avatarLink);
    }
    if (bio != null) {
        updates.add("bio = ?");
        params.add(bio);
    }

    if (updates.isEmpty()) {
        return false; // Nothing to update
    }

    String query = "UPDATE account SET " + String.join(", ", updates) + " WHERE user_id = ?";

    try (Connection conn = DatabaseConfig.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(query)) {

        int index = 1;
        for (Object param : params) {
            pstmt.setString(index++, (String) param);
        }
        pstmt.setString(index, hashedUserId);

        return pstmt.executeUpdate() > 0;
    }
}

}
