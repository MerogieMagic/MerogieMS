package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadConfigCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(ReloadConfigCommand.class);
    
    {
        setDescription("Reload server configuration from config.yaml");
    }

    @Override
    public void execute(Client c, String[] params) {
        try {
            YamlConfig.reloadConfig();
            c.getPlayer().yellowMessage("Configuration reloaded successfully.");
            log.info("Configuration reloaded by GM {}", c.getPlayer().getName());
        } catch (Exception e) {
            c.getPlayer().yellowMessage("Failed to reload configuration: " + e.getMessage());
            log.error("Failed to reload configuration", e);
        }
    }
} 