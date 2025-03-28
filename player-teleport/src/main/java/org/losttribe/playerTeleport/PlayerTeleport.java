package org.losttribe.playerTeleport;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class PlayerTeleport extends Plugin implements Listener {

    // TODO change DB consts to be the right information
    private static final String MYSQL_HOST = "localhost";
    private static final String MYSQL_PORT = "3306";
    private static final String MYSQL_DB   = "mydatabase";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "password";

    private List<String> altServers = new ArrayList<>();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);

        // TODO add the server names that are in list A of servers
        altServers.add("lobby");

        getLogger().info("MyBungeePlugin has been enabled!");
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
                        getLogger().warning("Server 'smp' not found in BungeeCord config!");
                        return;
                    }
                } else {
                    if (altServers.isEmpty()) {
                        getLogger().warning("No servers in altServers list!");
                        return;
                    }
                    String firstChoice = altServers.get(0);
                    target = ProxyServer.getInstance().getServerInfo(firstChoice);
                    if (target == null) {
                        getLogger().warning("Server '" + firstChoice + "' not found in BungeeCord config!");
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
            String url = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_DB;

            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = DriverManager.getConnection(url, MYSQL_USER, MYSQL_PASS);
                // TODO get the correct column name in the db where the player names are fetched
                String sql = "SELECT * FROM allowed_players WHERE player_name = ?";
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

    @Override
    public void onDisable() {
        getLogger().info("MyBungeePlugin is shutting down.");
    }
}
