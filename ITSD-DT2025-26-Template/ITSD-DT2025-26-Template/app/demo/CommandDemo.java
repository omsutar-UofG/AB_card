package demo;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;
import utils.OrderedCardLoader;
import utils.StaticConfFiles;

/**
 * This class contains an illustration of calling all of the commands provided
 * in the ITSD Card Game Template
 * @author Dr. Richard McCreadie
 *
 */
public class CommandDemo {

	/**
	 * This is a demo of the various commands that can be executed
	 * 
	 * WARNING: This is a very long-running method, as it has a lot
	 * thread sleeps in it. The back-end will not respond to commands
	 * while this method is processing, e.g. if you refresh the game
	 * page or try to kill the server you will get a long delay before
	 * anything happens.
	 */
	public static void executeDemo(ActorRef out) {

		BasicCommands.addPlayer1Notification(out, "Command Demo", 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();} // these cause processing to wait for a number of milliseconds.

		// addPlayer1Notification
		BasicCommands.addPlayer1Notification(out, "addPlayer1Notification", 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		
		//--------------------------------------------------------
		// Basic Draw Commands
		// -------------------------------------------------------
		
		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[3,2]", 2);
		Tile tile = BasicObjectBuilders.loadTile(3, 2);
		BasicCommands.drawTile(out, tile, 0);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}
		
		// drawUnit
		BasicCommands.addPlayer1Notification(out, "drawUnit", 2);
		Unit unit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 0, Unit.class);
		unit.setPositionByTile(tile); 
		BasicCommands.drawUnit(out, unit, tile);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// setUnitAttack
		BasicCommands.addPlayer1Notification(out, "setUnitAttack", 2);
		BasicCommands.setUnitAttack(out, unit, 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// setUnitHealth
		BasicCommands.addPlayer1Notification(out, "setUnitHealth", 2);
		BasicCommands.setUnitHealth(out, unit, 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		
		//--------------------------------------------------------
		// Playing Unit Animations
		// -------------------------------------------------------
		
		// playUnitAnimation [Move]
		BasicCommands.addPlayer1Notification(out, "playUnitAnimation [Move]", 2);
		BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.move);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// playUnitAnimation [Attack]
		BasicCommands.addPlayer1Notification(out, "playUnitAnimation [Attack]", 2);
		BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.attack);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// playUnitAnimation [Death]
		BasicCommands.addPlayer1Notification(out, "playUnitAnimation [Death]", 3);
		BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death);
		try {Thread.sleep(3000);} catch (InterruptedException e) {e.printStackTrace();}

		
		//--------------------------------------------------------
		// Deleting a Unit
		//-------------------------------------------------------
		
		// deleteUnit
		BasicCommands.addPlayer1Notification(out, "deleteUnit", 2);
		BasicCommands.deleteUnit(out, unit);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}


		//--------------------------------------------------------
		// Movement
		//-------------------------------------------------------
		
		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[3,2] Highlight", 2);
		BasicCommands.drawTile(out, tile, 1);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[3,2] Red Highlight", 2);
		BasicCommands.drawTile(out, tile, 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[3,2]", 2);
		BasicCommands.drawTile(out, tile, 0);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[4,2]", 2);
		Tile tile3 = BasicObjectBuilders.loadTile(4, 2);
		BasicCommands.drawTile(out, tile3, 0);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[5,2]", 2);
		Tile tile4 = BasicObjectBuilders.loadTile(5, 2);
		BasicCommands.drawTile(out, tile4, 0);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// drawTile
		BasicCommands.addPlayer1Notification(out, "drawTile[5,3]", 2);
		Tile tile5 = BasicObjectBuilders.loadTile(5, 3);
		BasicCommands.drawTile(out, tile5, 0);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}
		
		BasicCommands.addPlayer1Notification(out, "drawUnit", 1);
		Unit unit2 = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, 1, Unit.class);
		unit2.setPositionByTile(tile); 
		BasicCommands.drawUnit(out, unit2, tile);
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
		
		BasicCommands.addPlayer1Notification(out, "moveUnitToTile", 3);
		BasicCommands.moveUnitToTile(out, unit2, tile5);
		try {Thread.sleep(4000);} catch (InterruptedException e) {e.printStackTrace();}
		
		BasicCommands.addPlayer1Notification(out, "moveUnitToTile (y-axis first)", 3);
		BasicCommands.moveUnitToTile(out, unit2, tile, true);
		try {Thread.sleep(4000);} catch (InterruptedException e) {e.printStackTrace();}
		
		BasicCommands.addPlayer1Notification(out, "deleteUnit", 2);
		BasicCommands.deleteUnit(out, unit2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}
		
		
		
		//--------------------------------------------------------
		// Player Display Data
		//-------------------------------------------------------
		
		// Player Cards
		BasicCommands.addPlayer1Notification(out, "Player Test", 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// setPlayer1Health
		BasicCommands.addPlayer1Notification(out, "setPlayer1Health", 2);
		Player humanPlayer = new Player(20, 0);
		BasicCommands.setPlayer1Health(out, humanPlayer);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// setPlayer1Health
		BasicCommands.addPlayer1Notification(out, "setPlayer2Health", 2);
		Player aiPlayer = new Player(20, 0);
		BasicCommands.setPlayer2Health(out, aiPlayer);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

		// Mana
		for (int m = 0; m<10; m++) {
			BasicCommands.addPlayer1Notification(out, "setPlayer1Mana ("+m+")", 1);
			humanPlayer.setMana(m);
			BasicCommands.setPlayer1Mana(out, humanPlayer);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
		}

		// Mana
		for (int m = 0; m<10; m++) {
			BasicCommands.addPlayer1Notification(out, "setPlayer2Mana ("+m+")", 1);
			aiPlayer.setMana(m);
			BasicCommands.setPlayer2Mana(out, aiPlayer);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
		}
		
		
		//--------------------------------------------------------
		// Rendering Cards
		//-------------------------------------------------------
		
		// Unhighlighted
		
		int handPosition = 1;
		for (Card card : OrderedCardLoader.getPlayer1Cards(1)) {
			BasicCommands.drawCard(out, card, handPosition, 0);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			
			handPosition++;
			
			if (handPosition>6) break;
		}
		
		// highlighted
		
		handPosition = 1;
		for (Card card : OrderedCardLoader.getPlayer1Cards(1)) {
			BasicCommands.drawCard(out, card, handPosition, 1);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			
			handPosition++;
			
			if (handPosition>6) break;
		}
		
		
		// delete card
		handPosition = 1;
		for (int i=1; i<7; i++) {
			BasicCommands.deleteCard(out, i);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
		}

		//--------------------------------------------------------
		// Test New Stuff from the 2024 Template
		//-------------------------------------------------------
		
		BasicCommands.addPlayer1Notification(out, "2024 Loader Check", 2);
		try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}
		Loaders_2024_Check.test(out); // moved 2024 tests in here
		


	}

}
