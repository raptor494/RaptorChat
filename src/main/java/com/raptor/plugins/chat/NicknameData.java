package com.raptor.plugins.chat;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Strings;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

public final class NicknameData {
	private @Nullable String nickname;
	private @Nullable TextColor nameColor;
	boolean changed = false;
	
	public NicknameData() {}
	
	public NicknameData(@Nullable String nickname, @Nullable TextColor nameColor) {
		this.nickname = nickname;
		this.nameColor = nameColor;
	}
	
	public @Nullable String getNickname() {
		return nickname;
	}
	
	public void setNickname(@Nullable String nickname) {
		if (nickname != null) {
			nickname = nickname.trim();
		}
		if (!(Objects.equals(this.nickname, nickname) || Strings.isNullOrEmpty(this.nickname) && Strings.isNullOrEmpty(nickname))) {
			this.nickname = nickname;
			changed = true;
		}
	}
	
	public @Nullable TextColor getNameColor() {
		return nameColor;
	}
	
	public void setNameColor(@Nullable TextColor nameColor) {
		if (!Objects.equals(this.nameColor, nameColor)) {
			this.nameColor = nameColor;
			changed = true;
		}
	}
	
	public boolean isEmpty() {
		return (nickname == null || nickname.isEmpty()) && (nameColor == null || nameColor == NamedTextColor.WHITE);
	}
	
	public Component getComponent(Component sourceName) {
		if (nickname != null) {
			sourceName = Component.text(nickname, Style.style(nameColor));
		} else if (nameColor != null) {
			sourceName = sourceName.style(Style.style(nameColor));
		}
		return sourceName;
	}

	public void updateDisplayNameFor(Player player) {
		if (isEmpty()) {
			player.displayName(null);
			player.customName(null);
			player.playerListName(null);
		} else {
			Component displayName = Component.text(Strings.isNullOrEmpty(nickname)? player.getName() : nickname);
			if (nameColor != null) {
				displayName.color(nameColor);
			}
			player.displayName(displayName);
			player.customName(displayName);
			player.playerListName(displayName);
		}
	}
	
}
