package com.raptor.plugins.chat;

import java.util.Date;
import java.util.Objects;

import org.bukkit.OfflinePlayer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

public final class Mail {
	/**
	 * Who sent the message. If null, was sent by the Server.
	 */
	private final @Nullable OfflinePlayer sender;
	
	private final Date sent;
	private final String subject;
	private final Component message;
	
	private final @Nullable Mail re;
	
	public Mail(@Nullable OfflinePlayer sender, @NonNull Date sent, @NonNull String subject, @NonNull Component message, @Nullable Mail re) {
		this.sender = sender;
		this.sent = Objects.requireNonNull(sent, "sent");
		this.subject = Objects.requireNonNull(subject, "subject");
		this.message = Objects.requireNonNull(message, "message");
		this.re = re;
	}
	
	public @Nullable OfflinePlayer sender() { return sender; }
	
	public Date sent() { return sent; }
	
	public String subject() { return subject; }
	
	public Component message() { return message; }
	
	public @Nullable Mail re() { return re; }
	
}
