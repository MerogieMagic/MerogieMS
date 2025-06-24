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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LastLoginCommand extends Command {
    {
        setDescription("Check last login by user name");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length != 1) {
            player.yellowMessage("Command format: @lastlogin <username>");
            return;
        }

        String lastlogin = "";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT lastlogin FROM accounts WHERE name = ?")) {
            ps.setString(1, params[0].trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lastlogin = rs.getTimestamp("lastlogin").toString();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        player.yellowMessage(params[0].trim() + " last logged in at " + lastlogin);
    }
}
