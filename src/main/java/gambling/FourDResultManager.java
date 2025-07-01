package gambling;

import tools.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Manages 4D draw creation, result storage, and winner evaluation.
 */
public class FourDResultManager {

    /**
     * Checks if a result already exists for a given date.
     */
    public static boolean hasDrawToday(LocalDate date) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM 4d_results WHERE draw_date = ?")) {

            ps.setDate(1, java.sql.Date.valueOf(date));
            return ps.executeQuery().next();

        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Stores the official draw result for a given date.
     */
    public static void storeDraw(LocalDate date, String first, String second, String third,
                                 List<String> starters, List<String> consolations) {

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO 4d_results (draw_date, prize_1st, prize_2nd, prize_3rd, starters, consolations) VALUES (?, ?, ?, ?, ?, ?)")) {

            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.setString(2, first);
            ps.setString(3, second);
            ps.setString(4, third);
            ps.setString(5, String.join(",", starters));
            ps.setString(6, String.join(",", consolations));
            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[FourDResultManager] Failed to insert result: " + e.getMessage());
        }
    }

    /**
     * Compares all bets for a date against the result and updates winners.
     */
    public static void evaluateBets(LocalDate date) {
        try (Connection con = DatabaseConnection.getConnection()) {

            // Load results
            PreparedStatement getResult = con.prepareStatement("SELECT * FROM 4d_results WHERE draw_date = ?");
            getResult.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = getResult.executeQuery();

            if (!rs.next()) return;

            String first = rs.getString("prize_1st");
            String second = rs.getString("prize_2nd");
            String third = rs.getString("prize_3rd");
            List<String> starters = Arrays.asList(rs.getString("starters").split(","));
            List<String> consolations = Arrays.asList(rs.getString("consolations").split(","));

            Set<String> allBigWinners = new HashSet<>();
            allBigWinners.add(first);
            allBigWinners.add(second);
            allBigWinners.add(third);
            allBigWinners.addAll(starters);
            allBigWinners.addAll(consolations);

            // Fetch bets
            PreparedStatement getBets = con.prepareStatement("SELECT bet_id, bet_number, bet_type FROM 4d_bets WHERE draw_date = ?");
            getBets.setDate(1, java.sql.Date.valueOf(date));
            ResultSet bets = getBets.executeQuery();

            while (bets.next()) {
                int betId = bets.getInt("bet_id");
                String number = bets.getString("bet_number");
                String type = bets.getString("bet_type");

                int prize = 0;

                if (number.equals(first)) prize = type.equals("BIG") ? 20 : 30;
                else if (number.equals(second)) prize = type.equals("BIG") ? 10 : 15;
                else if (number.equals(third)) prize = type.equals("BIG") ? 5 : 7;
                else if (type.equals("BIG") && starters.contains(number)) prize = 2;
                else if (type.equals("BIG") && consolations.contains(number)) prize = 1;

                if (prize > 0) {
                    PreparedStatement update = con.prepareStatement(
                            "UPDATE 4d_bets SET is_winner = 1, prize_item_id = ?, prize_quantity = ? WHERE bet_id = ?");
                    update.setInt(1, 3020002); // MESO_BCOIN_ID
                    update.setInt(2, prize);
                    update.setInt(3, betId);
                    update.executeUpdate();
                    update.close();
                }
            }

            bets.close();
            getBets.close();
            rs.close();
            getResult.close();

        } catch (SQLException e) {
            System.out.println("[FourDResultManager] Evaluation failed: " + e.getMessage());
        }
    }
}
