package com.raptor.plugins.chat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gson.JsonParseException;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.util.HSVLike;

public class RaptorChat extends JavaPlugin implements Listener {

	private static /* final */ RaptorChat instance;

	@Deprecated
	public RaptorChat() {
		instance = this;
	}

	public static RaptorChat instance() {
		return instance;
	}

	private HashMap<UUID, NicknameData> playerData = new HashMap<>();
	private HashMap<String, String> nameColorPermissions = new HashMap<>();
	private HashMap<UUID, Mailbox> mailboxes = new HashMap<>();
	private Pattern nicknamePattern;
	private boolean allowDuplicateNicknames;
	private ArrayList<TextReplacementConfig> chatReplacements = new ArrayList<>();
	private /* final */ File mailFolder;

	@Override
	public void onEnable() {
		mailFolder = new File(getDataFolder(), "Mail");
		mailFolder.mkdirs();
		getServer().getPluginManager().registerEvents(this, this);
		saveDefaultConfig();
		reload();

		PluginCommand cmd;

		CommandRaptorChat cmd_raptorchat = new CommandRaptorChat();
		cmd = getCommand("raptorchat");
		cmd.setExecutor(cmd_raptorchat);
		cmd.setTabCompleter(cmd_raptorchat);

		CommandNick cmd_nick = new CommandNick();
		cmd = getCommand("nick");
		cmd.setExecutor(cmd_nick);
		cmd.setTabCompleter(cmd_nick);

		CommandNameColor cmd_namecolor = new CommandNameColor();
		cmd = getCommand("namecolor");
		cmd.setExecutor(cmd_namecolor);
		cmd.setTabCompleter(cmd_namecolor);

		CommandRealName cmd_realname = new CommandRealName();
		cmd = getCommand("realname");
		cmd.setExecutor(cmd_realname);
		cmd.setTabCompleter(cmd_realname);

		CommandChatItem cmd_chatitem = new CommandChatItem();
		cmd = getCommand("chatitem");
		cmd.setExecutor(cmd_chatitem);
		cmd.setTabCompleter(cmd_chatitem);

		CommandMail cmd_mail = new CommandMail();
		cmd = getCommand("mail");
		cmd.setExecutor(cmd_mail);
		cmd.setTabCompleter(cmd_mail);

		CommandReply cmd_reply = new CommandReply();
		cmd = getCommand("reply");
		cmd.setExecutor(cmd_reply);
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((JavaPlugin) this);
	}

	/**
	 * Matches if a string contains either a dollar sign ('$') or a backslash ('\')
	 */
	private static final Pattern REPLACEMENT_SPECIAL_REGEX = Pattern.compile("[$\\\\]");
	private static final TextReplacementConfig SPECIAL_TEXT_REPLACEMENT_CONFIG = TextReplacementConfig.builder()
		.match(
			"\\{((?:-?b(?:old)?|-?i(?:talic)?|-?u(?:nderline|l)?|-?s(?:trikethr(?:ough|u))?|-?o(?:bfuscated)?|sga|#[0-9a-fA-F]{6}|black|dark_(?:blue|green|aqua|red|purple|gray)|gold|gray|blue|green|aqua|red|light_purple|yellow|white)(?: (?:-?b(?:old)?|-?i(?:talic)?|-?u(?:nderline|l)?|-?s(?:trikethr(?:ough|u))?|-?o(?:bfuscated)?|sga|#[0-9a-fA-F]{6}|black|dark_(?:blue|green|aqua|red|purple|gray)|gold|gray|blue|green|aqua|red|light_purple|yellow|white))*)?:([^}]+)\\}")
		.replacement((matchResult, builder) -> {
			String[] options = matchResult.group(1).split(" ");
			String text = matchResult.group(2);
			Style.Builder styleBuilder = Style.style();
			for (String option : options) {
				if (option.startsWith("#")) {
					styleBuilder.color(TextColor.fromCSSHexString(option));
				} else {
					switch (option) {
						case "bold":
						case "b":
							styleBuilder.decoration(TextDecoration.BOLD, true);
							break;
						case "-bold":
						case "-b":
							styleBuilder.decoration(TextDecoration.BOLD, false);
							break;
						case "italic":
						case "i":
							styleBuilder.decoration(TextDecoration.ITALIC, true);
							break;
						case "-italic":
						case "-i":
							styleBuilder.decoration(TextDecoration.ITALIC, false);
							break;
						case "obfuscated":
						case "o":
							styleBuilder.decoration(TextDecoration.OBFUSCATED, true);
							break;
						case "-obfuscated":
						case "-o":
							styleBuilder.decoration(TextDecoration.OBFUSCATED, false);
							break;
						case "underline":
						case "u":
							styleBuilder.decoration(TextDecoration.UNDERLINED, true);
							break;
						case "-underline":
						case "-u":
							styleBuilder.decoration(TextDecoration.UNDERLINED, false);
							break;
						case "strikethrough":
						case "strikethru":
						case "s":
							styleBuilder.decoration(TextDecoration.STRIKETHROUGH, true);
							break;
						case "-strikethrough":
						case "-strikethru":
						case "-s":
							styleBuilder.decoration(TextDecoration.STRIKETHROUGH, false);
							break;
						case "sga":
							styleBuilder.font(Key.key("minecraft:alt"));
							break;
						default:
							styleBuilder.color(NamedTextColor.NAMES.value(option));
					}
				}
			}
			return Component.text(text, styleBuilder.build());
		})
		.build();

	private static final TextReplacementConfig RAINBOW_TEXT_REPLACEMENT_CONFIG = TextReplacementConfig.builder()
		.match("\\{rainbow:([^}]+)\\}")
		.replacement((matchResult, builder) -> {
			String text = matchResult.group(1);
			Component result = Component.text("");
			for (int i = 0, ch; i < text.length(); i += Character.charCount(ch)) {
				ch = text.codePointAt(i);
				String ch_str = text.substring(i, i + Character.charCount(ch));
				Component component = Component.text(ch_str)
					.color(TextColor.color(HSVLike.of((float) i / text.length(), 1.0f, 1.0f)));
				result = result.append(component);
			}
			return result;
		})
		.build();

	private static final TextReplacementConfig FULLWIDTH_TEXT_REPLACEMENT_CONFIG = TextReplacementConfig.builder()
		.match("\\{fullwidth:([^}]+)\\}")
		.replacement((matchResult, builder) -> {
			String text = matchResult.group(1);
			StringBuilder sb = new StringBuilder(text.length());
			for (int i = 0, ch; i < text.length(); i += Character.charCount(ch)) {
				ch = text.codePointAt(i);
				if ('\u0021' <= ch && ch <= '\u007E') {
					sb.appendCodePoint(ch + ('！' - '!'));
				} else {
					sb.appendCodePoint(ch);
				}
			}
			return Component.text(sb.toString());
		})
		.build();

	void reload() {
		reloadConfig();
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);

		if (config.getBoolean("ChatFormatEnabled", true)) {
			File chatFormatFile = new File(getDataFolder(), config.getString("ChatFormatFile", "chat_format.json"));
			if (!chatFormatFile.exists() && chatFormatFile.getName().equals("chat_format.json")) {
				saveResource("chat_format.json", false);
			}
			try {
				setChatFormat(GsonComponentSerializer.gson()
					.deserialize(Files.toString(chatFormatFile, Charset.defaultCharset())));
			} catch (IOException | JsonParseException e) {
				throw new RuntimeException(e);
			}
		} else {
			setChatFormat(null);
		}

		nicknamePattern = Pattern.compile(config.getString("NicknamePattern", ".+"));
		allowDuplicateNicknames = config.getBoolean("AllowDuplicateNicknames", false);

		chatReplacements.clear();
		for (Map<?, ?> map : config.getMapList("ChatReplacements")) {
			TextReplacementConfig.Builder builder = TextReplacementConfig.builder();
			final Pattern pattern = Pattern.compile((String) map.get("Match"));
			builder.match(pattern);
			@Nullable
			String replacement = (String) map.get("Replacement");
			Style.Builder styleBuilder = Style.style();
			if (map.containsKey("Style")) {
				Map<?, ?> styleMap = (Map<?, ?>) map.get("Style");
				if (styleMap.containsKey("Color")) {
					styleBuilder.color(parseColor((String) styleMap.get("Color")));
				}
				if (styleMap.containsKey("Bold")) {
					styleBuilder.decoration(TextDecoration.BOLD, parseBoolean(styleMap.get("Bold")));
				}
				if (styleMap.containsKey("Italic")) {
					styleBuilder.decoration(TextDecoration.ITALIC, parseBoolean(styleMap.get("Italic")));
				}
				if (styleMap.containsKey("Underline")) {
					styleBuilder.decoration(TextDecoration.UNDERLINED, parseBoolean(styleMap.get("Underline")));
				}
				if (styleMap.containsKey("Obfuscated")) {
					styleBuilder.decoration(TextDecoration.OBFUSCATED, parseBoolean(styleMap.get("Obfuscated")));
				}
				if (styleMap.containsKey("Strikethru")) {
					styleBuilder.decoration(TextDecoration.STRIKETHROUGH, parseBoolean(styleMap.get("Strikethru")));
				} else if (styleMap.containsKey("Strikethrough")) {
					styleBuilder.decoration(TextDecoration.STRIKETHROUGH, parseBoolean(styleMap.get("Strikethrough")));
				}
				if (styleMap.containsKey("Font")) {
					styleBuilder.font(Key.key((String) styleMap.get("Font")));
				}
			}
			Style style = styleBuilder.build();
			if (replacement != null && REPLACEMENT_SPECIAL_REGEX.matcher(replacement).find()) {
				builder.replacement((matchResult, b) -> {
					return Component.text(doReplacement(pattern, matchResult, replacement), style);
				});
			} else {
				builder.replacement(Component.text(replacement, style));
			}
			chatReplacements.add(builder.build());
		}

		ConfigurationSection nameColorSection = config.getConfigurationSection("NameColorPermissions");
		nameColorPermissions.clear();
		if (nameColorSection != null) {
			for (String key : nameColorSection.getKeys(false)) {
				String permission = nameColorSection.getString(key);
				nameColorPermissions.put(key, permission);
			}
		}

		YamlConfiguration data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
		playerData.clear();
		for (String uuidStr : data.getKeys(false)) {
			UUID uuid = UUID.fromString(uuidStr);
			ConfigurationSection section = data.getConfigurationSection(uuidStr);
			playerData.put(uuid,
				new NicknameData(section.getString("Nickname", null), parseColor(section.getString("Color", null))));
		}

		if (!allowDuplicateNicknames) {
			boolean modified = false;
			for (Player player : getServer().getOnlinePlayers()) {
				NicknameData playerData = this.playerData.get(player.getUniqueId());
				if (playerData == null || Strings.isNullOrEmpty(playerData.getNickname())
					|| playerData.getNickname().equals(player.getName()))
					continue;
				Player otherPlayer = getServer().getPlayer(playerData.getNickname());
				if (otherPlayer != null && !otherPlayer.getUniqueId().equals(player.getUniqueId())
					&& otherPlayer.isOnline())
				{
					playerData.setNickname(null);
					playerData.updateDisplayNameFor(player);
					modified = true;
					player.sendMessage(Component
						.text("Your nickname was reset because another player with that name joined the server.")
						.color(NamedTextColor.RED));
				}
			}
			if (modified) {
				try {
					savePlayerData();
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, e, () -> "Error occurred saving player data");
				}
			}
		}
	}

	// This is just a slightly modified version of the replacement
	// function for Pattern/String.replaceAll().
	private static String doReplacement(Pattern parentPattern, MatchResult matchResult, String replacement) {
		int cursor = 0;
		StringBuilder result = new StringBuilder();

		while (cursor < replacement.length()) {
			char nextChar = replacement.charAt(cursor);
			if (nextChar == '\\') {
				cursor++;
				if (cursor == replacement.length())
					throw new IllegalArgumentException("character to be escaped is missing");
				nextChar = replacement.charAt(cursor);
				result.append(nextChar);
				cursor++;
			} else if (nextChar == '$') {
				// Skip past $
				cursor++;
				// Throw IAE if this "$" is the last character in replacement
				if (cursor == replacement.length())
					throw new IllegalArgumentException("Illegal group reference: group index is missing");
				nextChar = replacement.charAt(cursor);
				int refNum = -1;
				// The first number is always a group
				refNum = (int) nextChar - '0';
				if ((refNum < 0) || (refNum > 9))
					throw new IllegalArgumentException("Illegal group reference");
				cursor++;
				// Capture the largest legal group string
				boolean done = false;
				while (!done) {
					if (cursor >= replacement.length()) {
						break;
					}
					int nextDigit = replacement.charAt(cursor) - '0';
					if ((nextDigit < 0) || (nextDigit > 9)) { // not a number
						break;
					}
					int newRefNum = (refNum * 10) + nextDigit;
					if (matchResult.groupCount() < newRefNum) {
						done = true;
					} else {
						refNum = newRefNum;
						cursor++;
					}
				}

				// Append group
				result.append(matchResult.group(refNum));
			} else {
				result.append(nextChar);
				cursor++;
			}
		}
		return result.toString();
	}

	/**
	 * Attempts to convert an object of unknown type to a boolean.
	 * 
	 * @param  obj                      The object to convert to a boolean
	 * @return                          {@code true} if the object is the string
	 *                                  "true", {@code false} if the object is the
	 *                                  string "false", otherwise returns the object
	 *                                  casted to Boolean.
	 * @throws IllegalArgumentException If the object is a string but not "true" or
	 *                                  "false"
	 * @throws ClassCastException       If the object is not "true", "false", or an
	 *                                  instance of {@linkplain Boolean}.
	 */
	private static boolean parseBoolean(Object obj) {
		if (obj instanceof String) {
			switch ((String) obj) {
				case "true":
					return true;
				case "false":
					return false;
				default:
					throw new IllegalArgumentException();
			}
		}
		return (Boolean) obj;
	}

	/**
	 * Attempts to save all player data from the field {@linkplain #playerData} to
	 * the {@code data.yml} file. If a NicknameData instance's
	 * {@link NicknameData#isEmpty isEmpty()} method returns {@code false}, the
	 * instance does not get saved.
	 * 
	 * @throws   IOException If an error occurred writing to the file
	 * @implNote             This also sets the internal field
	 *                       {@link NicknameData#changed} to {@code false} for all
	 *                       NicknameData instances.
	 */
	public void savePlayerData() throws IOException {
		YamlConfiguration data = new YamlConfiguration();
		for (Entry<UUID, NicknameData> entry : playerData.entrySet()) {
			UUID playerUUID = entry.getKey();
			NicknameData nicknameData = entry.getValue();
			nicknameData.changed = false;
			if (nicknameData.isEmpty())
				continue;
			ConfigurationSection section = data.createSection(playerUUID.toString());
			section.set("Nickname", nicknameData.getNickname());
			if (nicknameData.getNameColor() != null)
				section.set("Color", nicknameData.getNameColor().toString());
		}
		data.save(new File(getDataFolder(), "data.yml"));
	}

	private static final Pattern HEX_PATTERN = Pattern.compile("\\#[0-9a-fA-F]{6}");

	/**
	 * Attempts to parse a String into a TextColor. The string may be a CSS hex
	 * color code in the form of #XXXXXX or it may be the name of one of Minecraft's
	 * built in text colors.
	 * 
	 * @param  str The string to parse. May be null.
	 * @return     the parsed TextColor instance or {@code null} if the string
	 *             couldn't be parsed.
	 * @see        NamedTextColor
	 * @see        TextColor#fromCSSHexString
	 */
	private static TextColor parseColor(@Nullable String str) {
		if (str == null)
			return null;
		if (HEX_PATTERN.matcher(str).matches())
			return TextColor.fromCSSHexString(str);
		return NamedTextColor.NAMES.value(str);
	}

	/**
	 * The chat format to use. If {@code null}, the chat format will not be changed.
	 */
	private @Nullable Component chatFormat;

	/** The ChatRenderer to use instead of the default. */
	private final ChatRenderer chatRenderer = (source, sourceName, message, viewer) -> {
		final NicknameData playerData = getPlayerData(source);
		Component result = chatFormat
			.replaceText(b -> b.matchLiteral("${player}").replacement(playerData.getComponent(sourceName)))
			.replaceText(b -> b.matchLiteral("${message}").replacement(formatMessage(message)));
		return result;
	};

	/**
	 * Sets the chat format to use.
	 * 
	 * @param chatFormat The chat format to use. If {@code null}, no chat formatting
	 *                   will be done.
	 */
	public void setChatFormat(@Nullable Component chatFormat) {
		this.chatFormat = chatFormat;
	}

	@EventHandler
	public void onChat(AsyncChatEvent event) {
		if (chatFormat != null) {
			event.renderer(chatRenderer);
		}
	}

	private static final UUID UUID1 = UUID.fromString("c3e6871e-8e60-490a-8a8d-2bbe35ad1604");
	private static Random rand = new Random();

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		NicknameData playerData = getPlayerData(event.getEntity());
		if (!playerData.isEmpty()) {
			event.deathMessage(event.deathMessage()
				.replaceText(builder -> builder
					.match("\\b"+event.getEntity().getName()+"\\b")
					.replacement(event.getEntity().displayName())));
		}
		if (event.getEntity().getUniqueId().equals(UUID1) && rand.nextFloat() < 0.1) {
			ItemStack egg = new ItemStack(Material.EGG, 2);
			ItemMeta itemMeta = egg.getItemMeta();
			itemMeta.displayName(Component.text("ｅｇｇ"));
			egg.setItemMeta(itemMeta);
			event.getDrops().add(egg);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		NicknameData playerData = getPlayerData(event.getPlayer());
		if (!playerData.isEmpty()) {
			playerData.updateDisplayNameFor(event.getPlayer()); // set the custom display name
			event.joinMessage(event.joinMessage()
				.replaceText(
					b -> b.match("\\b"+event.getPlayer().getName()+"\\b")
						.replacement(event.getPlayer().displayName())));
		}
		Mailbox mailbox = getMailbox(event.getPlayer());
		if (!mailbox.getUnreadMail().isEmpty()) {
			event.getPlayer()
				.sendMessage(Component.text("You have new mail messages! ")
					.color(NamedTextColor.RED)
					.append(Component.text("[Read]").clickEvent(ClickEvent.runCommand("/mail read"))));
		}
		if (allowDuplicateNicknames)
			return;
		boolean modified = false;
		boolean clearedJoiningPlayersNickname = false;
		if (!Strings.isNullOrEmpty(playerData.getNickname())) {
			Player otherPlayer = getServer().getPlayer(playerData.getNickname());
			if (otherPlayer != null && !otherPlayer.getUniqueId().equals(event.getPlayer().getUniqueId())
				&& otherPlayer.isOnline())
			{
				playerData.setNickname(null);
				playerData.updateDisplayNameFor(event.getPlayer());
				modified = clearedJoiningPlayersNickname = true;
				event.getPlayer()
					.sendMessage(Component
						.text("Your nickname was reset because another player with that name joined the server.")
						.color(NamedTextColor.RED));
			}
		}
		for (Player otherPlayer : getServer().getOnlinePlayers()) {
			if (otherPlayer.getUniqueId().equals(event.getPlayer().getUniqueId()))
				continue;
			NicknameData otherPlayerData = this.playerData.get(otherPlayer.getUniqueId());
			if (otherPlayerData == null || Strings.isNullOrEmpty(otherPlayerData.getNickname()))
				continue;
			if (!clearedJoiningPlayersNickname && otherPlayerData.getNickname().equals(playerData.getNickname())) {
				playerData.setNickname(null);
				playerData.updateDisplayNameFor(event.getPlayer());
				modified = true;
				event.getPlayer()
					.sendMessage(Component
						.text("Your nickname was reset because another player with that name joined the server.")
						.color(NamedTextColor.RED));
			}
			if (otherPlayerData.getNickname().equals(event.getPlayer().getName())) {
				otherPlayerData.setNickname(null);
				otherPlayerData.updateDisplayNameFor(otherPlayer);
				modified = true;
				otherPlayer.sendMessage(
					Component.text("Your nickname was reset because another player with that name joined the server.")
						.color(NamedTextColor.RED));
			}
		}
		if (modified) {
			try {
				savePlayerData();
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, e, () -> "Error occurred saving player data");
			}
		}
	}

	public NicknameData getPlayerData(OfflinePlayer player) {
		return playerData.computeIfAbsent(player.getUniqueId(), uuid -> new NicknameData());
	}

	/**
	 * Get or create the mailbox of some player.
	 * 
	 * @param  player
	 * @return
	 */
	public Mailbox getMailbox(OfflinePlayer player) {
		return mailboxes.computeIfAbsent(player.getUniqueId(), uuid -> {
			Mailbox mailbox = new Mailbox(player);
			mailbox.load(mailFolder);
			return mailbox;
		});
	}

	/**
	 * Format a chat message by applying all the replacements defined in
	 * {@code config.yml}.
	 * 
	 * @param  message The message to format
	 * @return         The new chat message
	 */
	Component formatMessage(Component message) {
		message = message.replaceText(SPECIAL_TEXT_REPLACEMENT_CONFIG)
			.replaceText(RAINBOW_TEXT_REPLACEMENT_CONFIG)
			.replaceText(FULLWIDTH_TEXT_REPLACEMENT_CONFIG);
		for (TextReplacementConfig replacementConfig : chatReplacements) {
			message = message.replaceText(replacementConfig);
		}
		return message;
	}

	class CommandRaptorChat implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			if (sender.hasPermission("raptorchat.command.raptorchat")) {
				if (args.length == 0 || args.length == 1 && "reload".startsWith(args[0])) {
					return Arrays.asList("reload");
				}
			}
			return Collections.emptyList();
		}

		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.raptorchat"))
				return true;
			if (args.length == 0) {
				// info message
				sender.sendMessage(Component.text("RaptorChat ")
					.color(NamedTextColor.GREEN)
					.append(Component.text("v4.0.0").color(NamedTextColor.BLUE))
					.append(Component.text("\nBy ").color(NamedTextColor.GREEN))
					.append(Component.text("Raptor4694").color(NamedTextColor.RED)));
			} else if (args.length == 1 && args[0].equals("reload")) {
				// reload the plugin
				reload();
				sender.sendMessage(Component.text("Reloaded RaptorChat"));
			} else {
				// error message
				sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
			}
			return true;
		}

	}

	class CommandNick implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			if (sender.hasPermission("raptorchat.command.nick")) {
				if (sender instanceof Player) {
					if (args.length == 0 || args.length == 1 && ((Player) sender).getName().startsWith(args[0])) {
						return Arrays.asList(((Player) sender).getName());
					}
				}
				if (args.length == 2 && sender.hasPermission("raptorchat.command.nick.others")) {
					return null;
				}
			}
			return Collections.emptyList();
		}

		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.nick"))
				return true;
			Player player;
			String nickname = args[0];
			if (args.length == 1) {
				if (!(sender instanceof Player)) {
					// error message
					sender.sendMessage(
						Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
					return true;
				} else {
					player = (Player) sender;
				}
			} else if (args.length == 2) {
				if (sender.hasPermission("raptorchat.command.nick.others")) {
					// change another player's nickname
					try {
						UUID uuid = UUID.fromString(args[1]);
						player = getServer().getPlayer(uuid);
					} catch (IllegalArgumentException e) {
						player = getServer().getPlayer(args[1]);
					}

					if (player == null || !player.isOnline()) {
						// error message
						sender.sendMessage(Component.text("Player not found").color(NamedTextColor.RED));
						return true;
					}
				} else {
					// error message
					sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
					return false;
				}
			} else {
				// error message
				sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
				return false;
			}

			NicknameData playerData = getPlayerData(player);
			if (nickname.equals(player.getName())) {
				playerData.setNickname(null);
				sender.sendMessage(Component.text("Nickname cleared"));
			} else {
				if (nickname.equalsIgnoreCase("herobrine")) {
					nickname = nickname.substring(0, 7) + 'a' + nickname.substring(8, 9);
				} else if (nickname.equalsIgnoreCase("notch")) {
					nickname = nickname.substring(0, 2) + nickname.substring(1);
				}
				if (nicknamePattern.matcher(nickname).matches()) {
					if (!allowDuplicateNicknames) {
						Player otherPlayer = getServer().getPlayer(nickname);
						if (otherPlayer != null && !otherPlayer.getUniqueId().equals(player.getUniqueId())
							&& otherPlayer.isOnline()
							|| RaptorChat.this.playerData.values()
								.stream()
								.filter(data -> !Strings.isNullOrEmpty(data.getNickname()))
								.map(data -> data.getNickname())
								.anyMatch(nickname::equals))
						{
							sender.sendMessage(
								Component.text("Another player already has that name.").color(NamedTextColor.RED));
							return true;
						}
					}
					playerData.setNickname(nickname);
					playerData.updateDisplayNameFor(player);
					sender.sendMessage(Component.text("Nickname changed"));
				} else {
					// error message
					sender.sendMessage(Component.text("Nickname contains invalid characters.")
						.color(NamedTextColor.RED));
				}
			}
			if (playerData.changed) {
				try {
					savePlayerData();
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, e, () -> "Error occurred saving player data");
				}
			}
			return true;
		}

	}

	class CommandNameColor implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			if (sender.hasPermission("raptorchat.command.namecolor")) {
				if (args.length == 0 || args.length == 1 && args[0].isEmpty()) {
					ArrayList<String> options = new ArrayList<>(17);
					options.addAll(NamedTextColor.NAMES.keys());
					options.add("reset");
					options.add("#");
					return options;
				}
				if (args.length == 1 && !args[0].startsWith("#")) {
					ArrayList<String> options = new ArrayList<>(17);
					String arg = args[0].toLowerCase();
					for (String name : NamedTextColor.NAMES.keys()) {
						if (name.startsWith(arg)) {
							options.add(name);
						}
					}
					return options;
				}
				if (args.length == 2 && sender.hasPermission("raptorchat.command.namecolor.others")) {
					return null;
				}
			}
			return Collections.emptyList();
		}

		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.namecolor"))
				return true;
			Player player;
			TextColor nameColor = parseColor(args[0]);
			if (args.length == 1) {
				if (!(sender instanceof Player)) {
					// error message
					sender.sendMessage(
						Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
					return true;
				} else {
					player = (Player) sender;
				}
			} else if (args.length == 2 && sender.hasPermission("raptorchat.command.namecolor.others")) {
				// change another player's name color
				try {
					UUID uuid = UUID.fromString(args[1]);
					player = getServer().getPlayer(uuid);
				} catch (IllegalArgumentException e) {
					player = getServer().getPlayer(args[1]);
				}

				if (player == null || !player.isOnline()) {
					// error message
					sender.sendMessage(Component.text("Player not found").color(NamedTextColor.RED));
					return true;
				}
			} else {
				// error message
				sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
				return false;
			}
			if (nameColor == null && !args[0].equals("reset")) {
				// error message
				sender.sendMessage(Component.text("Invalid color").color(NamedTextColor.RED));
				return false;
			}

			NicknameData playerData = getPlayerData(player);
			playerData.setNameColor(nameColor);
			playerData.updateDisplayNameFor(player);
			sender
				.sendMessage(Component.text("Name color changed to ").append(Component.text(args[0]).color(nameColor)));
			if (playerData.changed) {
				try {
					savePlayerData();
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, e, () -> "Error occurred saving player data");
				}
			}
			return true;
		}

	}

	class CommandRealName implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			if (sender.hasPermission("raptorchat.command.realname")) {
				if (args.length <= 1) {
					String arg = args.length == 0 ? "" : args[0].toLowerCase();
					ArrayList<String> options = new ArrayList<>();
					for (NicknameData data : playerData.values()) {
						if (!Strings.isNullOrEmpty(data.getNickname())
							&& data.getNickname().toLowerCase().startsWith(arg))
						{
							options.add(data.getNickname());
						}
					}
					return options;
				}
			}
			return Collections.emptyList();
		}

		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.realname"))
				return true;
			String realname = args[0];
			for (Entry<UUID, NicknameData> entry : playerData.entrySet()) {
				if (realname.equals(entry.getValue().getNickname())) {
					UUID uuid = entry.getKey();
					OfflinePlayer player = getServer().getOfflinePlayer(uuid);
					sender.sendMessage(Component.text(realname+"'s username is "+player.getName()));
					return true;
				}
			}
			sender.sendMessage(Component.text("Nobody on this server has that nickname.").color(NamedTextColor.RED));
			return true;
		}

	}

	class CommandChatItem implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			return Collections.emptyList();
		}

		@SuppressWarnings("deprecation")
		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.chatitem"))
				return true;
			if (!(sender instanceof Player)) {
				sender.sendMessage(
					Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
				return true;
			}
			Player player = (Player) sender;
			ItemStack item = player.getInventory().getItemInMainHand();
			if (item == null || item.getAmount() == 0) {
				sender.sendMessage(Component.text("You aren't holding anything.").color(NamedTextColor.RED));
				return true;
			}
			Component message = item.displayName().hoverEvent(item.asHoverEvent());
			Component chat;
			if (chatFormat == null) {
				Optional<Team> teamOpt = player.getScoreboard()
					.getTeams()
					.stream()
					.filter(team -> team.hasPlayer(player) && team.color() != null)
					.findFirst();
				if (teamOpt.isPresent()) {
					chat = Component.text("<")
						.append(Component.text(player.getName()).color(teamOpt.get().color()))
						.append(Component.text("> "))
						.append(message);
				} else {
					chat = Component.text("<"+player.getName()+"> ").append(message);
				}
			} else {
				chat = chatFormat
					.replaceText(b -> b.matchLiteral("${player}").replacement(player.displayName()))
					.replaceText(b -> b.matchLiteral("${message}").replacement(formatMessage(message)));
			}
			getServer().sendMessage(player, chat, MessageType.CHAT);
			return true;
		}

	}

	private static final int MAX_MAIL_PER_PAGE = 19;
	private static final int MAX_MAIL_SUBJECT_LENGTH = 30;

	private static final String[] MAIL_SUBCOMMANDS = { "list", "read", "delete", "send", "reply" };

	class CommandMail implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			if (sender.hasPermission("raptorchat.command.mail")) {
				if (args.length <= 1) {
					String arg = args.length == 0 ? "" : args[0];
					ArrayList<String> options = new ArrayList<>();
					for (String subcommand : MAIL_SUBCOMMANDS) {
						if (subcommand.startsWith(arg)) {
							options.add(subcommand);
						}
					}
					return options;
				}
				if (args.length == 2) {
					switch (args[0]) {
						case "send":
							return null;
						default:
							return Collections.emptyList();
					}
				}
				if (args.length == 3 && args[0].equals("send") && args[2].isEmpty()) {
					return Collections.singletonList("\"");
				}
				if (args.length >= 3 && args[0].equals("send") && args[2].startsWith("\"") && !args[2].endsWith("\"")) {
					for (int i = 3; i < args.length; i++) {
						if (args[i].endsWith("\"")) {
							if (i + 1 == args.length)
								return Collections.singletonList(args[i]);
							else
								return Collections.emptyList();
						}
					}
					return Collections.singletonList(args[args.length - 1]+"\"");
				}
			}
			return Collections.emptyList();
		}

		@SuppressWarnings("deprecation")
		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.mail"))
				return true;
			if (args.length == 0) {
				if (!(sender instanceof Player)) {
					// error message
					sender.sendMessage(
						Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
				} else {
					mailList((Player) sender, 0);
				}
			} else if (args.length == 1) {
				switch (args[0]) {
					case "read": {
						if (!(sender instanceof Player)) {
							// error message
							sender.sendMessage(Component.text("You must be a player to execute this command")
								.color(NamedTextColor.RED));
						} else {
							mailRead((Player) sender, 0);
						}
						break;
					}
					case "list": {
						if (!(sender instanceof Player)) {
							// error message
							sender.sendMessage(Component.text("You must be a player to execute this command")
								.color(NamedTextColor.RED));
						} else {
							mailList((Player) sender, 0);
						}
						break;
					}
					default:
						return false;
				}
			} else if (args.length == 2) {
				switch (args[0]) {
					case "read": {
						if (!(sender instanceof Player)) {
							// error message
							sender.sendMessage(Component.text("You must be a player to execute this command")
								.color(NamedTextColor.RED));
						} else {
							int mailNum;
							try {
								mailNum = Integer.parseInt(args[1]);
							} catch (NumberFormatException e) {
								mailNum = -1;
							}
							if (mailNum < 0) {
								// error message
								sender.sendMessage(Component.text("Error: expected a positive integer after 'read'")
									.color(NamedTextColor.RED));
								break;
							}
							mailRead((Player) sender, mailNum);
						}
						break;
					}
					case "list": {
						if (!(sender instanceof Player)) {
							// error message
							sender.sendMessage(Component.text("You must be a player to execute this command")
								.color(NamedTextColor.RED));
						} else {
							int pageNum;
							try {
								pageNum = Integer.parseInt(args[1]);
							} catch (NumberFormatException e) {
								pageNum = -1;
							}
							if (pageNum < 0) {
								// error message
								sender.sendMessage(Component.text("Error: expected a positive integer after 'list'")
									.color(NamedTextColor.RED));
								break;
							}
							mailList((Player) sender, pageNum);
						}
						break;
					}
					case "delete": {
						if (!(sender instanceof Player)) {
							// error message
							sender.sendMessage(Component.text("You must be a player to execute this command")
								.color(NamedTextColor.RED));
						} else {
							int mailNum;
							try {
								mailNum = Integer.parseInt(args[1]);
							} catch (NumberFormatException e) {
								mailNum = -1;
							}
							if (mailNum < 0) {
								// error message
								sender.sendMessage(Component.text("Error: expected a positive integer after 'delete'")
									.color(NamedTextColor.RED));
								break;
							}
							mailDelete((Player) sender, mailNum);
						}
						break;
					}
					default:
						return false;
				}
			} else if (args.length >= 3 && args[0].equals("reply")) {
				if (!(sender instanceof Player)) {
					// error message
					sender.sendMessage(
						Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
				} else {
					int mailNum;
					try {
						mailNum = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
						mailNum = -1;
					}
					if (mailNum < 0) {
						// error message
						sender.sendMessage(Component.text("Error: expected a positive integer after 'reply'")
							.color(NamedTextColor.RED));
						return true;
					}
					StringBuilder message = new StringBuilder();
					message.append(args[2]);
					for (int i = 3; i < args.length; i++) {
						message.append(' ').append(args[i]);
					}
					Mailbox mailbox = getMailbox((Player) sender);
					Optional<Mail> RE_opt = mailbox.stream().skip(mailNum).findFirst();
					if (RE_opt.isPresent()) {
						Mail RE = RE_opt.get();
						if (RE.sender() == null) {
							// error message
							sender.sendMessage(Component.text("You cannot reply to messages sent by the Server.")
								.color(NamedTextColor.RED));
						} else {
							Mail mail = new Mail((Player) sender, new Date(), "RE: "+RE.subject(),
								formatMessage(Component.text(message.toString())), RE);
							sendMail(mail, RE.sender());
						}
					} else {
						// error message
						sender.sendMessage(Component.text("Invalid mail index, too large").color(NamedTextColor.RED));
					}
				}
			} else if (args.length >= 4 && args[0].equals("send")) {
				StringBuilder subject = new StringBuilder();
				OfflinePlayer player;
				try {
					UUID uuid = UUID.fromString(args[1]);
					player = getServer().getOfflinePlayer(uuid);
				} catch (IllegalArgumentException e) {
					player = getServer().getOfflinePlayer(args[1]);
				}

				if (player == null) {
					// error message
					sender.sendMessage(Component.text("Player not found").color(NamedTextColor.RED));
					return true;
				}
				int messageStart;
				if (args[2].startsWith("\"")) {
					if (args[2].endsWith("\"")) {
						subject.append(args[2], 1, args[2].length() - 1);
						messageStart = 3;
					} else {
						subject.append(args[2], 1, args[2].length());
						for (messageStart = 3; messageStart < args.length; messageStart++) {
							subject.append(' ');
							if (args[messageStart].endsWith("\"")) {
								subject.append(args[messageStart], 0, args[messageStart].length() - 1);
								messageStart++;
								break;
							} else {
								subject.append(args[messageStart]);
							}
						}
						if (messageStart == args.length) {
							// error message
							sender.sendMessage(
								Component.text("Error: no message given after subject").color(NamedTextColor.RED));
							return true;
						}
					}
				} else {
					subject.append(args[2]);
					messageStart = 3;
				}
				StringBuilder message = new StringBuilder(args[messageStart++]);
				for (; messageStart < args.length; messageStart++) {
					message.append(' ').append(args[messageStart]);
				}
				Mail mail = new Mail(sender instanceof Player ? (Player) sender : null, new Date(), subject.toString(),
					formatMessage(Component.text(message.toString())), null);
				sendMail(mail, player);
			}
			return true;
		}

		private void mailRead(Player sender, int index) {
			Mailbox mailbox = getMailbox(sender);
			Mail mailToRead;
			List<Mail> unreadMail = mailbox.getUnreadMail();
			if (index >= unreadMail.size()) {
				index -= unreadMail.size();
				List<Mail> readMail = mailbox.getReadMail();
				if (index >= readMail.size()) {
					// error message
					sender.sendMessage(
						Component.text(index == 0 && unreadMail.isEmpty() && readMail.isEmpty() ? "You have no mail"
							: "Invalid mail index, too large.").color(NamedTextColor.RED));
					return;
				} else {
					mailToRead = readMail.get(index);
				}
			} else {
				try {
					mailToRead = mailbox.readMail(index);
				} catch (IndexOutOfBoundsException e) {
					// error message
					sender.sendMessage(Component
						.text(index == 0 && unreadMail.isEmpty() && mailbox.getReadMail().isEmpty() ? "You have no mail"
							: "Invalid mail index, too large.")
						.color(NamedTextColor.RED));
					return;
				}
				try {
					mailbox.save(mailFolder);
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, e, () -> "Failed to save mailbox for "+mailbox.getOwner().getName());
				}
			}
			Component message = Component
				.text("From: "+mailToRead.sender().getName()+"\nDate: "+mailToRead.sent()+"\nSubject: ")
				.color(NamedTextColor.RED)
				.append(Component.text(mailToRead.subject()).color(NamedTextColor.WHITE))
				.append(Component.text("\nMessage:\n").color(NamedTextColor.RED))
				.append(mailToRead.message().colorIfAbsent(NamedTextColor.WHITE));
			sender.sendMessage(message);
		}

		private void mailDelete(Player sender, int index) {
			Mailbox mailbox = getMailbox(sender);
			try {
				mailbox.deleteMail(index);
				sender.sendMessage(Component.text("Message deleted."));
				try {
					mailbox.save(mailFolder);
				} catch (IOException e) {
					getLogger().log(Level.SEVERE, e, () -> "Failed to save mailbox for "+mailbox.getOwner().getName());
				}
			} catch (IndexOutOfBoundsException e) {
				// error message
				sender.sendMessage(
					Component.text(index == 0 && mailbox.getUnreadMail().isEmpty() && mailbox.getReadMail().isEmpty()
						? "You have no mail"
						: "Invalid mail index, too large.").color(NamedTextColor.RED));
				return;
			}
		}

		private void mailList(Player sender, int pageNum) {
			Mailbox mailbox = getMailbox(sender);
			getLogger().log(Level.INFO,
				"Mailbox for "+sender.getName()+": unread: "+mailbox.getUnreadMail()+"; read: "+mailbox.getReadMail());
			int unreadCount = mailbox.getUnreadMail().size();
			AtomicInteger index = new AtomicInteger(MAX_MAIL_PER_PAGE * pageNum);
			Component message = mailbox.stream()
				.skip(MAX_MAIL_PER_PAGE * pageNum)
				.limit(MAX_MAIL_PER_PAGE)
				.map(mail -> formatMailHeader(index.getAndIncrement(), index.get() > unreadCount, mail))
				.collect(() -> {
					TextComponent.Builder result = Component.text().content("----- ").color(NamedTextColor.RED);
					if (pageNum > 0) {
						result = result.append(Component.text("<<")
							.color(NamedTextColor.RED)
							.clickEvent(ClickEvent.runCommand("/mail list "+(pageNum - 1))));
					} else {
						result = result.append(Component.text("<<").color(NamedTextColor.GRAY));
					}
					result = result.append(Component.text(" "+pageNum+" ").color(NamedTextColor.RED))
						.append(Component.text(">>")
							.color(NamedTextColor.RED)
							.clickEvent(ClickEvent.runCommand("/mail list "+(pageNum + 1))))
						.append(Component.text(" -----").color(NamedTextColor.RED));
					return result;
				}, (result, mail) -> {
					getLogger().log(Level.INFO, "Mail "+mail);
					result.append(Component.newline()).append(mail);
				}, (c1, c2) -> c1.append(c2))
				.build();
			sender.sendMessage(message);
		}

		private Component formatMailHeader(int index, boolean read, Mail mail) {
			String subject = mail.subject();
			if (subject.length() > MAX_MAIL_SUBJECT_LENGTH) {
				subject = subject.substring(0, MAX_MAIL_SUBJECT_LENGTH - 1)+"…";
			}
			ClickEvent clickEvent = ClickEvent.runCommand("/mail read "+index);
			return Component.text(index+". ")
				.color(NamedTextColor.GRAY)
				.clickEvent(clickEvent)
				.append(Component.text(mail.sender().getName()+": ")
					.style(builder -> builder.color(NamedTextColor.GRAY).decoration(TextDecoration.STRIKETHROUGH, read))
					.clickEvent(clickEvent)
					.append(Component.text(subject)
						.style(
							builder -> builder.color(NamedTextColor.WHITE)
								.decoration(TextDecoration.STRIKETHROUGH, read))
						.clickEvent(clickEvent)));
		}

		private void sendMail(Mail mail, OfflinePlayer recipient) {
			Mailbox mailbox = getMailbox(recipient);
			mailbox.addMail(mail);
			if (recipient.isOnline()) {
				getServer().getPlayer(recipient.getUniqueId())
					.sendMessage(Component.text("You have new mail messages! ")
						.color(NamedTextColor.RED)
						.append(Component.text("[Read]").clickEvent(ClickEvent.runCommand("/mail read"))));
			}
			try {
				mailbox.save(mailFolder);
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, e, () -> "Failed to save mailbox for "+mailbox.getOwner().getName());
			}
		}

	}

	private final HashMap<UUID, String> lastDMs = new HashMap<>();
	private static final Pattern WHISPER_PATTERN = Pattern.compile("^/(?:w(?:hisper)?|tell) ([a-zA-Z0-9_]+)(?:$| )");

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
		Matcher m = WHISPER_PATTERN.matcher(event.getMessage());
		if (m.find()) {
			lastDMs.put(event.getPlayer().getUniqueId(), m.group(1));
		}
	}

	class CommandReply implements CommandExecutor, TabCompleter {

		@Override
		public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
			return Collections.emptyList();
		}

		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
			if (!sender.hasPermission("raptorchat.command.reply"))
				return true;
			if (!(sender instanceof Player)) {
				// error message
				sender.sendMessage(
					Component.text("You must be a player to execute this command").color(NamedTextColor.RED));
				return true;
			}
			String lastDMName = lastDMs.get(((Player) sender).getUniqueId());
			if (lastDMName == null) {
				// error message
				sender.sendMessage(Component.text("Nobody has sent you a private message yet")
					.color(NamedTextColor.RED));
				return true;
			}
			getServer().dispatchCommand(sender, "tell "+lastDMName+" "+String.join(" ", args));
			return true;
		}

	}

}
