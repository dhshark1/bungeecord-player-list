package org.losttribe.playerTeleport;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class PlayerTeleport extends Plugin implements Listener {

    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDb;
    private String mysqlUser;
    private String mysqlPass;

    private List<String> altServers;

    private Configuration config;

    @Override
    public void onEnable() {
        loadConfig();

        this.mysqlHost = config.getString("mysql.host", "localhost");
        this.mysqlPort = config.getInt("mysql.port", 3306);
        this.mysqlDb   = config.getString("mysql.db", "losttribedb");
        this.mysqlUser = config.getString("mysql.user", "root");
        this.mysqlPass = config.getString("mysql.pass", "password");

        this.altServers = config.getStringList("altServers");

        getProxy().getPluginManager().registerListener(this, this);

        getLogger().info("player teleport has been enabled!");
        getLogger().info("Loaded " + altServers.size() + " altServers from config.yml.");
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        if (event.getReason() == ServerConnectEvent.Reason.JOIN_PROXY) {
            ProxiedPlayer player = event.getPlayer();

            event.setCancelled(true);

            checkPlayerInDatabaseAsync(player.getName(), (isInDB) -> {
                ServerInfo target;
                if (isInDB) {
                    target = ProxyServer.getInstance().getServerInfo("smp");
                    if (target == null) {
                        getLogger().warning("Could not find server 'smp' in BungeeCord config!");
                        return;
                    }
                } else {
                    if (altServers.isEmpty()) {
                        getLogger().warning("No altServers configured! Cannot send player anywhere.");
                        return;
                    }

                    Random rand = new Random();
                    String randomServerName = altServers.get(rand.nextInt(altServers.size()));

                    target = ProxyServer.getInstance().getServerInfo(randomServerName);
                    if (target == null) {
                        getLogger().warning("Could not find server '" + randomServerName + "' in BungeeCord config!");
                        return;
                    }
                }

                if (player.isConnected()) {
                    player.connect(target);
                }
            });
        }
    }

    private void checkPlayerInDatabaseAsync(String playerName, Consumer<Boolean> callback) {
        getProxy().getScheduler().runAsync(this, () -> {
            boolean inDB = false;
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDb;

            try {
                conn = DriverManager.getConnection(url, mysqlUser, mysqlPass);
                // TODO is this the right to column name to select from?
                String sql = "SELECT * FROM players WHERE player_name = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, playerName);
                rs = stmt.executeQuery();

                if (rs.next()) {
                    inDB = true;
                }
            } catch (SQLException e) {
                getLogger().severe("MySQL error: " + e.getMessage());
            } finally {
                try { if (rs != null) rs.close(); } catch (Exception ignored) {}
                try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.close(); } catch (Exception ignored) {}
            }

            callback.accept(inDB);
        });
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in == null) {
                    getLogger().warning("No default config.yml found inside the plugin jar!");
                } else {
                    Files.copy(in, configFile.toPath());
                    getLogger().info("Default config.yml has been created!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MyBungeePlugin is shutting down.");
    }
}