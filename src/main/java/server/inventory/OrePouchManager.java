package server.inventory;

import client.inventory.Item;
import tools.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * OrePouchManager handles loading and saving a character's ore pouch contents.
 * This stores simple stackable items (like ores) outside of the main inventory.
 */
public class OrePouchManager {

    /**
     * Loads the list of ore items from the database for a given character.
     *
     * @param characterId the character ID
     * @return list of Item objects stored in the ore pouch
     */
    public static List<Item> loadOrePouchItems(int characterId) {
        List<Item> ores = new ArrayList<>();

        // Connect to the database and fetch ore pouch rows
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM ore_pouch WHERE character_id = ?"
             )) {

            ps.setInt(1, characterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("itemid");
                    short quantity = (short) rs.getInt("quantity");

                    // Create a basic stackable item (ETC ores)
                    Item item = new Item(itemId, (byte) 0, quantity);
                    ores.add(item);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ores;
    }

    /**
     * Saves the current list of ore pouch items for a character.
     * This method wipes existing entries and inserts the new state.
     *
     * @param characterId the character ID
     * @param items       list of Item objects to save
     */
    public static void saveOrePouchItems(int characterId, List<Item> items) {
        try (Connection con = DatabaseConnection.getConnection()) {

            // First, delete any existing pouch entries for this character
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM ore_pouch WHERE character_id = ?"
            )) {
                ps.setInt(1, characterId);
                ps.executeUpdate();
            }

            // Now insert the new list of ores
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ore_pouch (character_id, itemid, quantity) VALUES (?, ?, ?)"
            )) {
                for (Item item : items) {
                    ps.setInt(1, characterId);
                    ps.setInt(2, item.getItemId());
                    ps.setInt(3, item.getQuantity());
                    ps.addBatch(); // Add to batch for performance
                }

                ps.executeBatch(); // Execute all inserts at once
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
