package info.tregmine.listeners;

import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import info.tregmine.Tregmine;
import info.tregmine.WebServer;
import info.tregmine.api.TregminePlayer;
import info.tregmine.database.DAOException;
import info.tregmine.database.IContext;
import info.tregmine.database.ILogDAO;
import info.tregmine.database.IPlayerDAO;

public class ChatListener implements Listener
{
    private Tregmine plugin;

    public ChatListener(Tregmine instance)
    {
        this.plugin = instance;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event)
    {
        TregminePlayer sender = plugin.getPlayer(event.getPlayer());
        if (sender.getChatState() != TregminePlayer.ChatState.CHAT) {
            return;
        }

        String channel = sender.getChatChannel();

        try (IContext ctx = plugin.createContext()) {
            IPlayerDAO playerDAO = ctx.getPlayerDAO();
            for (TregminePlayer to : plugin.getOnlinePlayers()) {
                if (to.getChatState() == TregminePlayer.ChatState.SETUP) {
                    continue;
                }

                if (!sender.getRank().canNotBeIgnored()) {
                    if (playerDAO.doesIgnore(to, sender)) {
                        continue;
                    }
                }

                ChatColor txtColor = ChatColor.WHITE;
                if (sender.equals(to)) {
                    txtColor = ChatColor.GRAY;
                }

                String text = event.getMessage();
                for (TregminePlayer online : plugin.getOnlinePlayers()) {
                    if (text.contains(online.getName()) &&
                        !online.hasFlag(TregminePlayer.Flags.INVISIBLE)) {

                        text = text.replaceAll(online.getName(),
                                               online.getChatName() + txtColor);
                    }
                }

                List<String> player_keywords = playerDAO.getKeywords(to);

                if (player_keywords.size() > 0 && player_keywords != null) {
                    for (String keyword : player_keywords) {
                        if (text.toLowerCase().contains(keyword.toLowerCase())) {
                            text = text.replaceAll(Pattern.quote(keyword),
                                    ChatColor.AQUA + keyword + txtColor);
                        }
                    }
                }

                String senderChan = sender.getChatChannel();
                String toChan = to.getChatChannel();
                if (senderChan.equalsIgnoreCase(toChan) ||
                    to.hasFlag(TregminePlayer.Flags.CHANNEL_VIEW)) {
                    if ("GLOBAL".equalsIgnoreCase(senderChan)) {
                        to.sendMessage("<" + sender.getChatName()
                                + ChatColor.WHITE + "> " + txtColor + text);
                    }
                    else {
                        to.sendMessage(channel + " <" + sender.getChatName()
                                + ChatColor.WHITE + "> " + txtColor + text);
                    }
                }

                if (text.contains(to.getName()) &&
                    "GLOBAL".equalsIgnoreCase(senderChan) &&
                    !"GLOBAL".equalsIgnoreCase(toChan)) {

                    to.sendMessage(ChatColor.BLUE +
                        "You were mentioned in GLOBAL by " + sender.getNameColor() +
                        sender.getChatName());
                }
            }
        } catch (DAOException e) {
            throw new RuntimeException(e);
        }

        Tregmine.LOGGER.info(channel + " <" + sender.getName() + "> " +
                             event.getMessage());

        try (IContext ctx = plugin.createContext()) {
            ILogDAO logDAO = ctx.getLogDAO();
            logDAO.insertChatMessage(sender, channel, event.getMessage());
        } catch (DAOException e) {
            throw new RuntimeException(e);
        }

        event.setCancelled(true);

        WebServer server = plugin.getWebServer();
        server.executeChatAction(new WebServer.ChatMessage(sender,
                                                           channel,
                                                           event.getMessage()));
    }
}
