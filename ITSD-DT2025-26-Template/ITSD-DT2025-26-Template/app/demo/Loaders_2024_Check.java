package demo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;
import utils.OrderedCardLoader;
import utils.StaticConfFiles;

public class Loaders_2024_Check {

		/**
		 * This is a test of the loading and rendering for the 2024 unit
		 * set. It exists as a convenient way for Richard to check that he
		 * has not broken anything when he updated the template to add the
		 * new 2024 decks.
		 */
		public static void test(ActorRef out) {
			
			
			
			// drawTile
			Tile tile = BasicObjectBuilders.loadTile(3, 2);
			BasicCommands.drawTile(out, tile, 0);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();} // these cause processing to wait for a number of milliseconds.
			
			//--------------------------------------------------------
			// Test Cards and their Units
			// -------------------------------------------------------
			
			BasicCommands.addPlayer1Notification(out, "Test Cards / Units", 2);
			
			List<Card> cards = new ArrayList<Card>();
			cards.addAll(OrderedCardLoader.getPlayer1Cards(1));
			cards.addAll(OrderedCardLoader.getPlayer2Cards(1));
			
			// Run test for all cards
			for (Card card : cards) {
				
				BasicCommands.addPlayer1Notification(out, card.getCardname(), 2);
				
				// drawCard [1]
				BasicCommands.drawCard(out, card, 1, 0);
				try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

				// drawCard [1] Highlight
				BasicCommands.drawCard(out, card, 1, 1);
				try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
				
				int unitID = 0;
				if (card.isCreature()) {
					
					Unit unit = BasicObjectBuilders.loadUnit(card.getUnitConfig(), unitID, Unit.class);
					unit.setPositionByTile(tile); 
					BasicCommands.drawUnit(out, unit, tile);
					try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

					// playUnitAnimation [Move] and wait for a period until the animation does one cycle
					try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.move));} catch (InterruptedException e) {e.printStackTrace();}

					// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
					try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.attack));} catch (InterruptedException e) {e.printStackTrace();}
					
					// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
					try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.hit));} catch (InterruptedException e) {e.printStackTrace();}

					// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
					try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.channel));} catch (InterruptedException e) {e.printStackTrace();}

					// playUnitAnimation [Death] and wait for a period until the animation does one cycle
					try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death));} catch (InterruptedException e) {e.printStackTrace();}

					// deleteUnit
					BasicCommands.deleteUnit(out, unit);
					try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
					
					unitID++;
				}
				
				BasicCommands.deleteCard(out, 1);
				try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			}
			
			
			//--------------------------------------------------------
			// Test Avatars and Tokens
			// -------------------------------------------------------
			
			BasicCommands.addPlayer1Notification(out, "Test Avatars and Tokens", 1);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			
			// As avatars and tokens do not have cards, we need to directly load the unit
			// StaticConfFiles tells us where the associated conf file is
			
			List<String> avatarsAndTokens = new ArrayList<String>();
			avatarsAndTokens.add(StaticConfFiles.humanAvatar);
			avatarsAndTokens.add(StaticConfFiles.aiAvatar);
			avatarsAndTokens.add(StaticConfFiles.wraithling);
			
			for (String unitConf : avatarsAndTokens) {
				Unit unit = BasicObjectBuilders.loadUnit(unitConf, 1, Unit.class);
				unit.setPositionByTile(tile); 
				BasicCommands.drawUnit(out, unit, tile);
				try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

				// playUnitAnimation [Move] and wait for a period until the animation does one cycle
				try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.move));} catch (InterruptedException e) {e.printStackTrace();}

				// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
				try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.attack));} catch (InterruptedException e) {e.printStackTrace();}
				
				// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
				try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.hit));} catch (InterruptedException e) {e.printStackTrace();}

				// playUnitAnimation [Attack] and wait for a period until the animation does one cycle
				try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.channel));} catch (InterruptedException e) {e.printStackTrace();}

				// playUnitAnimation [Death] and wait for a period until the animation does one cycle
				try {Thread.sleep(BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death));} catch (InterruptedException e) {e.printStackTrace();}

				// deleteUnit
				BasicCommands.deleteUnit(out, unit);
				try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			}
			
			//--------------------------------------------------------
			// Test Effects
			// -------------------------------------------------------
			
			BasicCommands.addPlayer1Notification(out, "Test Effects", 1);
			try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
			
			// Now check all of the loaded effects
			String effectsDIR = "conf/gameconfs/effects/";
			for (String filename : new File(effectsDIR).list()) {
				EffectAnimation effect = BasicObjectBuilders.loadEffect(effectsDIR+filename);
				try {Thread.sleep(BasicCommands.playEffectAnimation(out, effect, tile));} catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	
}
