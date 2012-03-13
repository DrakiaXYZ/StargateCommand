package net.TheDgtl.StargateCommand;

import java.util.LinkedList;

import net.TheDgtl.StargateCommand.StargateCommand.Action;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SGCPlayer {
	public Player player;
	public LinkedList<Block> blocks = new LinkedList<Block>();
	public Action action;
	public String[] args;
	
	public SGCPlayer(Player player, Action action) {
		this.player = player;
		this.action = action;
	}
	
	public SGCPlayer(Player player, Action action, String[] args) {
		this(player, action);
		this.args = args;
	}
}
