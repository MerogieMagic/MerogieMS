package gambling;

import tools.DatabaseConnection;

import java.sql.*;
import java.util.*;

/**
 * Handles 4D bet submission, retrieval of player winnings, and claim updates.
 */
public class FourDBetManager {

    /**
     * Stores a player's 4D bet in the database.
     */
    public static void insertBet(int charId, String number, String type, String date) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO 4d_bets (char_id, bet_number, bet_type, draw_date) VALUES (?, ?, ?, ?)")) {

            ps.setInt(1, charId);
            ps.setString(2, number);
            ps.setString(3, type);
            ps.setString(4, date); // DATE type in SQL
            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[FourDBetManager] Insert failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves all unclaimed winning bets for a player.
     */
    public static List<Map<String, Object>> getUnclaimedWinningBets(int charId) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT bet_id, prize_quantity FROM 4d_bets WHERE char_id = ? AND is_winner = 1 AND claimed = 0")) {

            ps.setInt(1, charId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("bet_id", rs.getInt("bet_id"));
                entry.put("prize_quantity", rs.getInt("prize_quantity"));
                results.add(entry);
            }

        } catch (SQLException e) {
            System.out.println("[FourDBetManager] Retrieval failed: " + e.getMessage());
        }
        return results;
    }

    /**
     * Marks a winning bet as claimed after prize redemption.
     */
    public static void markBetClaimed(int betId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE 4d_bets SET claimed = 1 WHERE bet_id = ?")) {

            ps.setInt(1, betId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[FourDBetManager] Claim update failed: " + e.getMessage());
        }
    }
}
