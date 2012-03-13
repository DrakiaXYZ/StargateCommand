package net.TheDgtl.StargateCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import net.TheDgtl.Stargate.Blox;
import net.TheDgtl.Stargate.Gate;
import net.TheDgtl.Stargate.Stargate;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class StargateCommand extends JavaPlugin {
	private Logger log;
	private Server server;
	private PluginManager pm;
	
	private Stargate sg = null;
	
	public HashMap<Player, SGCPlayer> players = new HashMap<Player, SGCPlayer>();
	
	public void onEnable() {
		this.server = getServer();
		this.pm = server.getPluginManager();
		this.log = server.getLogger();
		
		sg = (Stargate)pm.getPlugin("Stargate");
		
		if (sg == null) {
			log.severe("[SGC] Error, Stargate not found. Disabling");
			pm.disablePlugin(this);
			return;
		}
		
		pm.registerEvents(new SGCListener(this), this);
	}
	
	public void onDisable() {
		
	}
	
	public void importGate(Player player, Gate gate, Block baseBlock, int modX, int modZ, boolean force) {
		if (gate == null) {
			StargateCommand.sendMessage(player, "The gate specified does not exist", true);
			return;
		}
		
		Blox topLeft = new Blox(baseBlock.getLocation().add(0, gate.getLayout().length, 0));
		
		if (!drawGate(topLeft, gate, modX, modZ, force)) {
			StargateCommand.sendMessage(player, "Gate interfered with existing terrain. Import cancelled. User force to ignore", true);
			return;
		}
		sendMessage(player, "Your gate has been imported successfully", false);
	}
	
	public boolean drawGate(Blox topleft, Gate gate, int modX, int modZ, boolean force) {
		Character[][] layout = gate.getLayout();
		HashMap<Character, Integer> types = gate.getTypes();
		HashMap<Character, Integer> metadata = gate.getMetaData();
		int closedType = gate.getPortalBlockClosed();
		
		// Queue used incase we need to undo
		ArrayList<BlockChange> blockQueue = new ArrayList<BlockChange>();
		
		for (int y = 0; y < layout.length; y++) {
			for (int x = 0; x < layout[y].length; x++) {
				int id = types.get(layout[y][x]);
				Integer mData = metadata.get(layout[y][x]);
				
				if (id == Gate.ANYTHING) {
					continue;
				}
				
				if (id == Gate.ENTRANCE || id == Gate.EXIT) {
					id = closedType;
				}
				
				Blox block = topleft.modRelative(x, y, 0, modX, 1, modZ);
				if (block.getType() != Material.AIR.getId() && !force) {
					return false;
				}
				
				BlockChange bc = new BlockChange();
				bc.block = block;
				bc.newType = id;
				bc.newData = mData;
				blockQueue.add(bc);
			}
		}
		
		for(BlockChange bc : blockQueue) {
			bc.block.setType(bc.newType);
			if (bc.newData != null)
				bc.block.setData(bc.newData);
		}
		return true;
	}
	
	private boolean checkOffset(Location a, Location b, int modX, int modZ, int width, int height) {
		int offX = Math.abs(a.getBlockX() - b.getBlockX());
		int offY = Math.abs(a.getBlockY() - b.getBlockY());
		int offZ = Math.abs(a.getBlockZ() - b.getBlockZ());
		if (modX == 0 && (offX != 0 || offZ > width)) return false;
		if (modZ == 0 && (offZ != 0 || offX > width)) return false;
		if (offY > height) return false;
		return true;
	}
	
	public void exportGate(Player player, Block topleft) {
		SGCPlayer sPlayer = players.get(player);
		// Determine facing
		int tmp = 0;
		int modX = 0;
		int modZ = 0;
		if (topleft.getRelative(BlockFace.EAST).getType() == Material.BEDROCK) {
			modZ = -1;
			tmp++;
		}
		if (topleft.getRelative(BlockFace.WEST).getType() == Material.BEDROCK) {
			modZ = 1;
			tmp++;
		}
		if (topleft.getRelative(BlockFace.NORTH).getType() == Material.BEDROCK) {
			modX = -1;
			tmp++;
		}
		if (topleft.getRelative(BlockFace.SOUTH).getType() == Material.BEDROCK) {
			modX = 1;
			tmp++;
		}
		if (tmp != 1 || topleft.getRelative(BlockFace.DOWN).getType() != Material.BEDROCK) {
			StargateCommand.sendMessage(player, "There was an error determining your frame. Exiting export mode", true);
			players.remove(player);
			return;
		}
		
		// Check offset of control blocks
		Block signBlock = sPlayer.blocks.poll();
		Block buttonBlock = sPlayer.blocks.poll();
		
		// Check offset of exit block
		Block exitBlock = sPlayer.blocks.poll();
		
		// Determine frame width/height
		int frameWidth = 1;
		int frameHeight = 1;
		while (topleft.getRelative(modX * frameWidth, 0, modZ * frameWidth).getType() == Material.BEDROCK) {
			frameWidth++;
		}
		while (topleft.getRelative(0, -frameHeight, 0).getType() == Material.BEDROCK) {
			frameHeight++;
		}
		frameWidth = frameWidth - 2;
		frameHeight = frameHeight - 2;
		Block startBlock = topleft.getRelative(modX, -1, modZ);
		
		if (!checkOffset(signBlock.getLocation(), startBlock.getLocation(), modX, modZ, frameWidth, frameHeight)) {
			sendMessage(player, "Your sign block is outside of your stargate. Exiting", true);
			return;
		}
		if (!checkOffset(buttonBlock.getLocation(), startBlock.getLocation(), modX, modZ, frameWidth, frameHeight)) {
			sendMessage(player, "Your button block is outside of your stargate. Exiting", true);
			return;
		}
		if (!checkOffset(exitBlock.getLocation(), startBlock.getLocation(), modX, modZ, frameWidth, frameHeight)) {
			sendMessage(player, "Your exit block is outside of your stargate. Exiting", true);
			return;
		}

		Gate gate = storeGate(player, startBlock, signBlock, buttonBlock, exitBlock, modX, modZ, frameWidth, frameHeight);
		if (gate != null) {
			Gate.registerGate(gate);
			sendMessage(player, "Your gate layout has been exported and loaded", false);
		}
	}
	
	public Gate storeGate(Player player, Block startBlock, Block signBlock, Block buttonBlock, Block exitBlock, int modX, int modZ, int width, int height) {
		SGCPlayer sPlayer = players.get(player);
		// Store the gate layout
		HashMap<Integer, Character> typeLookup = new HashMap<Integer, Character>();
		
		Character[][] layout = new Character[height][width];
		HashMap<Character, Integer> types = new HashMap<Character, Integer>();
		HashMap<Character, Integer> metadata = new HashMap<Character, Integer>();
		
		// Validate gate before saving
		boolean exitFound = false;
		int controlsFound = 0;
		
		// Init types map
		Character nextChar = 'A';
		types.put('.', Gate.ENTRANCE);
		types.put('*', Gate.EXIT);
		types.put(' ', Gate.ANYTHING);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				Block fBlock = startBlock.getRelative(modX * x, -y, modZ * x);
				int type = fBlock.getTypeId();
				int data = fBlock.getData();
				int lookup = type + (data << 16);
				// Anything
				if (type == Material.AIR.getId()) {
					layout[y][x] = ' ';
					continue;
				}
				// Entrance
				if (type == Material.BEDROCK.getId()) {
					if (fBlock.equals(exitBlock)) {
						exitFound = true;
						layout[y][x] = '*';
						continue;
					}
					layout[y][x] = '.';
					continue;
				}
				if (fBlock.equals(signBlock) || fBlock.equals(buttonBlock)) {
					controlsFound++;
					layout[y][x] = '-';
					Integer cType = types.get('-');
					if (cType != null && cType != type) {
						sendMessage(player, "Both your control blocks must be on the same block type. Exiting", true);
						return null;
					}
					types.put('-', type);
					continue;
				}
				// Store block/metadata for gate
				if (!typeLookup.containsKey(lookup)) {
					typeLookup.put(lookup, nextChar);
					types.put(nextChar, type);
					if (data != 0)
						metadata.put(nextChar, data);
					nextChar++;
				}
				layout[y][x] = typeLookup.get(lookup);
			}
		}
		
		if (!exitFound) {
			sendMessage(player, "Your exit was not in an entrance block. Exiting", true);
			return null;
		}
		if (controlsFound != 2) {
			sendMessage(player, "One of your control blocks was missing. Exiting", true);
			return null;
		}

		Gate gate = new Gate(sPlayer.args[1] + ".gate", layout, types, metadata);
		gate.setPortalBlockOpen(Material.PORTAL.getId());
		gate.setPortalBlockClosed(Material.AIR.getId());
		gate.save(Stargate.getGateFolder());
		return gate;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("[SGC] This command can only be used ingame");
			return true;
		}
		Player player = (Player)sender;
		// SGC import/export
		if (command.getName().equalsIgnoreCase("sgc") && args.length > 0) {
			// Import
			if (args[0].equalsIgnoreCase("import")) {
				if (!player.hasPermission("stargate.command.import")) {
					StargateCommand.sendMessage(player, "Permission Denied", true);
					return true;
				}
				if (args.length < 2) {
					StargateCommand.sendMessage(player, "You did not specify a gate to import", true);
					return true;
				}
				if (Gate.getGateByName(args[1] + ".gate") == null) {
					StargateCommand.sendMessage(player, "The gate you specified does not exist", true);
					return true;
				}
				
				StargateCommand.sendMessage(player, "Please select two blocks to define gate location/direction", false);
				StargateCommand.sendMessage(player, "Do this by right-clicking two blocks next to each other", false);
				players.put(player, new SGCPlayer(player, Action.IMPORT, args));
			// Export
			} else if (args[0].equalsIgnoreCase("export")) {
				if (!player.hasPermission("stargate.command.export")) {
					StargateCommand.sendMessage(player, "Permission Denied", true);
					return true;
				}
				if (args.length < 2) {
					sendMessage(player, "You must provide a gate name", true);
					return true;
				}
				boolean force = false;
				if (args.length > 2 && args[2].equalsIgnoreCase("force")) force = true;
				if (Gate.getGateByName(args[1] + ".gate") != null && !force) {
					sendMessage(player, "A gate by that name exists. Use force to overwrite", true);
					return true;
				}
				StargateCommand.sendMessage(player, "Please select where you would like the sign placed", false);
				StargateCommand.sendMessage(player, "Do this by right-clicking the block", false);
				players.put(player, new SGCPlayer(player, Action.EXPORT, args));
			// Cancel
			} else if (args[0].equalsIgnoreCase("cancel")) {
				players.remove(player);
				StargateCommand.sendMessage(player, "Command cancelled", false);
			}
			return true;
		}
		return false;
	}
	
	public static void sendMessage(CommandSender sender, String message, boolean error) {
		if (error) {
			message = ChatColor.RED + "[SGC] " + ChatColor.WHITE + message;
		} else {
			message = ChatColor.BLUE + "[SGC] " + ChatColor.WHITE + message;
		}
		sender.sendMessage(message);
	}
	
	private class BlockChange {
		public Blox block;
		public Integer newType;
		public Integer newData;
	}
	
	public enum Action {
		IMPORT,
		EXPORT
	}
}