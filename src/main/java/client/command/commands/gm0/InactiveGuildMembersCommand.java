/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;

import tools.DatabaseConnection;

import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InactiveGuildMembersCommand extends Command {
    {
        setDescription("Check last login by user name");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();

        // Ensure inactive days passed in
        if (params.length != 1) {
            player.yellowMessage("Invalid format: @inactiveguildmembers <inactiveDays>");
        }

        // Verify the user is guild master
        if (player.getGuildId() < 1 || player.getGuildRank() != 1) {
            player.yellowMessage("You must be the guild master to use this command");
            return;
        }

        int inactiveDays;

        // Attempt to parse number of days inactive
        try {
            inactiveDays = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            player.yellowMessage("Invalid number format");
            return;
        }

        // Verify the inactive check is for 7 days or more
        if (inactiveDays < 7) {
            player.yellowMessage("Number of days inactive must be at least 7");
            return;
        }

        // Get Timestamp of "now" minus inactiveDays
        ZonedDateTime now = ZonedDateTime.now().minusDays(inactiveDays);
        Timestamp nowTimeStamp = new Timestamp(now.toInstant().toEpochMilli());

        // Query users
        List<String> users = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT name FROM characters WHERE guildid = ? AND `lastLogoutTime` < ?")) {
            ps.setInt(1, player.getGuildId());
            ps.setTimestamp(2, nowTimeStamp);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (!users.isEmpty()) {
            player.yellowMessage("Users inactive for " + inactiveDays + " or more: " +
                    Arrays.toString(users.toArray()).replace("[", "").replace("]", ""));
        } else {
            player.yellowMessage("No users inactive for " + inactiveDays + " or more");
        }
    }
}
