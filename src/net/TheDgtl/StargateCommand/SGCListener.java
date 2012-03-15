package net.TheDgtl.StargateCommand;

import net.TheDgtl.Stargate.Gate;
import net.TheDgtl.Stargate.Portal;
import net.TheDgtl.Stargate.Stargate;
import net.TheDgtl.Stargate.event.StargateActivateEvent;
import net.TheDgtl.StargateCommand.StargateCommand.Action;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerInteractEvent;

public class SGCListener implements Listener {
	StargateCommand sgc;
	public SGCListener(StargateCommand plugin) {
		sgc = plugin;
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		// We want right-click block only
		if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
		
		Player player = event.getPlayer();
		Block block = event.getClickedBlock();
		SGCPlayer sPlayer = sgc.players.get(player);
		if (sPlayer == null || block == null) return;
		if (sPlayer.action == Action.IMPORT) {
			event.setCancelled(true);
			event.setUseInteractedBlock(Result.DENY);
			event.setUseItemInHand(Result.DENY);
			// First block
			if (sPlayer.blocks.size() == 0) {
				sPlayer.blocks.add(block);
				StargateCommand.sendMessage(player, "Please select a second block by right-clicking it", false);
				return;
			} else {
				Block fb = sPlayer.blocks.poll();
				int modX = fb.getX() - block.getX();
				int modZ = fb.getZ() - block.getZ();
				int modY = fb.getY() - block.getY();
				if (modY != 0 || modX > 1 || modX < -1 || modZ > 1 || modZ < -1 || (modX != 0 && modZ != 0)) {
					StargateCommand.sendMessage(player, "The blocks you selected were not next to eachother. Exiting", true);
					sgc.players.remove(player);
					return;
				}
				Gate gate = Gate.getGateByName(sPlayer.args[1] + ".gate");
				boolean force = false;
				if (sPlayer.args.length > 2 && sPlayer.args[2].equalsIgnoreCase("force")) force = true;
				sgc.importGate(player, gate, fb, modX, modZ, force);
				sgc.players.remove(player);
			}
		} else if (sPlayer.action == Action.EXPORT) {
			event.setCancelled(true);
			event.setUseInteractedBlock(Result.DENY);
			event.setUseItemInHand(Result.DENY);
			if (sPlayer.blocks.size() == 0) {
				sPlayer.blocks.add(block);
				StargateCommand.sendMessage(player, "Please select the button location", false);
			} else if (sPlayer.blocks.size() == 1) {
				sPlayer.blocks.add(block);
				StargateCommand.sendMessage(player, "Please select the exit location", false);
			} else if (sPlayer.blocks.size() == 2) {
				sPlayer.blocks.add(block);
				StargateCommand.sendMessage(player, "Please select the top-left block of the bedrock frame", false);
			} else if (sPlayer.blocks.size() == 3) {
				// First we find the dimensions of the frame
				if (block.getType() != Material.BEDROCK) {
					StargateCommand.sendMessage(player, "You did not select bedrock, exiting export mode", true);
					sgc.players.remove(player);
					return;
				}
				sgc.exportGate(player, block);
				sgc.players.remove(player);
			}
		}
	}
	
	@EventHandler
	public void onStargateActivate(StargateActivateEvent event) {
		Portal portal = event.getPortal();
		Player player = event.getPlayer();
		SGCPlayer sPlayer = sgc.players.get(player);
		if (sPlayer == null) return;
		if (sPlayer.action != Action.DIAL) return;
		sgc.players.remove(player);
		Portal destPortal = Portal.getByName(sPlayer.args[0], portal.getNetwork());
		if (destPortal == null) {
			StargateCommand.sendMessage(player, "The specified destination does not exist for this gate. Exiting", true);
		} else {
			if (!Stargate.canAccessNetwork(player, portal.getNetwork())) {
				StargateCommand.sendMessage(player, "You do not have access to that network.", true);
				return;
			}
			event.setCancelled(true);
			portal.activate(player);
			portal.setDestination(destPortal);
			Stargate.openPortal(player, portal);
			portal.drawSign();
		}
	}
}
