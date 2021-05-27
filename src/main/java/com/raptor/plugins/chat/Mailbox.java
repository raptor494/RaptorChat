package com.raptor.plugins.chat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import com.google.gson.Gson;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class Mailbox {
	private OfflinePlayer owner;

	private LinkedList<Mail> unread = new LinkedList<>();
	private LinkedList<Mail> read = new LinkedList<>();

	public Mailbox(@NotNull OfflinePlayer owner) {
		this.owner = Objects.requireNonNull(owner, "owner");
	}

	public OfflinePlayer getOwner() {
		return owner;
	}

	public boolean isEmpty() {
		return unread.isEmpty() && read.isEmpty();
	}

	/**
	 * @return An unmodifiable view of the unread mail in this mailbox
	 */
	public List<Mail> getUnreadMail() {
		return Collections.unmodifiableList(unread);
	}

	/**
	 * @return An unmodifiable view of the read mail in this mailbox
	 */
	public List<Mail> getReadMail() {
		return Collections.unmodifiableList(read);
	}

	/**
	 * Read some mail indicated by its index. Mail indices are counted first
	 * starting by the unread list, then by the read list.
	 * 
	 * @param  index The mail index
	 * @return       The mail that was read
	 */
	public Mail readMail(int index) {
		Mail mail = unread.remove(index);
		read.addFirst(mail);
		return mail;
	}

	/**
	 * Delete a mail entry by its index. Mail indices are counted first starting by
	 * the unread list, then by the read list.
	 * 
	 * @param index The mail index
	 */
	public void deleteMail(int index) {
		if (index >= unread.size()) {
			index -= unread.size();
			read.remove(index);
		} else {
			unread.remove(index);
		}
	}

	/**
	 * Adds a mail instance to the unread list. This method does not check if the
	 * mail instance is already in this mailbox.
	 * 
	 * @param mail The mail to add
	 */
	public void addMail(Mail mail) {
		unread.addFirst(mail);
	}

	/**
	 * Load this mailbox from {@code <mailFolder>/<uuid>.yml}.
	 * 
	 * @param  mailFolder       The folder the mail file is in
	 * @throws RuntimeException If some error occurs while loading mail
	 */
	@SuppressWarnings("unchecked")
	public void load(File mailFolder) {
		read.clear();
		unread.clear();
		File mailFile = new File(mailFolder, owner.getUniqueId().toString()+".yml");
		if (mailFile.exists()) {
			try {
				YamlConfiguration config = YamlConfiguration.loadConfiguration(mailFile);
				List<Map<?, ?>> unreadList = config.getMapList("Unread");
				for (Map<?, ?> data : unreadList) {
					unread.addLast(loadMail((Map<String, Object>) data));
				}
				List<Map<?, ?>> readList = config.getMapList("Read");
				for (Map<?, ?> data : readList) {
					read.addLast(loadMail((Map<String, Object>) data));
				}
			} catch (Exception e) {
				throw new RuntimeException("Error occurred loading mail for "+owner.getName(), e);
			}
		}
	}

	private static final Gson gson = new Gson();

	private Mail loadMail(Map<String, Object> data) {
		String fromStr = String.valueOf(data.computeIfAbsent("From", Mailbox::missingKey));
		OfflinePlayer sender = fromStr.equals("[SERVER]") ? null : Bukkit.getOfflinePlayer(UUID.fromString(fromStr));
		String subject = String.valueOf(data.computeIfAbsent("Subject", Mailbox::missingKey));
		Date sent = (Date) data.computeIfAbsent("Sent", Mailbox::missingKey);
		Component message = GsonComponentSerializer.gson()
			.deserializeFromTree(gson.toJsonTree(data.computeIfAbsent("Message", Mailbox::missingKey)));
		@SuppressWarnings("unchecked")
		Mail re = data.containsKey("RE") ? loadMail((Map<String, Object>) data.get("RE")) : null;
		return new Mail(sender, sent, subject, message, re);
	}

	private static Void missingKey(String key) throws RuntimeException {
		throw new RuntimeException("Missing key \""+key+"\"");
	}

	/**
	 * Save this mailbox to {@code <mailFolder>/<uuid>.yml}. If this mailbox is
	 * empty and the file exists, delete it instead.
	 * 
	 * @param  mailFolder  The folder to save the mailbox file to.
	 * @throws IOException If an error occurred while writing to the file.
	 */
	public void save(File mailFolder) throws IOException {
		File mailFile = new File(mailFolder, owner.getUniqueId().toString()+".yml");
		if (isEmpty() && mailFile.exists()) {
			mailFile.delete();
		} else {
			YamlConfiguration config = new YamlConfiguration();
			List<Map<String, Object>> readList = read.stream().map(this::saveMail).collect(Collectors.toList());
			List<Map<String, Object>> unreadList = unread.stream().map(this::saveMail).collect(Collectors.toList());
			config.set("Read", readList);
			config.set("Unread", unreadList);
			config.save(mailFile);
		}
	}

	private Map<String, Object> saveMail(Mail mail) {
		Map<String, Object> data = new LinkedHashMap<>(mail.re() == null ? 4 : 5);
		data.put("From", mail.sender() == null ? "[SERVER]" : mail.sender().getUniqueId().toString());
		data.put("Sent", mail.sent());
		data.put("Subject", mail.subject());
		data.put("Message", gson.fromJson(GsonComponentSerializer.gson().serializeToTree(mail.message()),
			HashMap.class));
		if (mail.re() != null)
			data.put("RE", saveMail(mail.re()));
		return data;
	}

	/**
	 * Returns a stream of all mail in this mailbox, starting with unread mail.
	 * 
	 * @return The stream of all mail in this mailbox
	 */
	public Stream<Mail> stream() {
		return Stream.concat(unread.stream(), read.stream());
	}

}
