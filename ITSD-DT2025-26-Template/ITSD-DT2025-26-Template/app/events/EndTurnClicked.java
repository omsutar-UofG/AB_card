package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import game.SimpleBoardLogic;
import structures.GameState;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * the end-turn button.
 * 
 * { 
 *   messageType = “endTurnClicked”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class EndTurnClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		// Defensive guard: ignore End Turn until initialization has fully created
		// both players and the active-turn pointer.
		if (!isTurnSystemReady(gameState)) {
			BasicCommands.addPlayer1Notification(out, "Game is initializing...", 2);
			return;
		}

		// The End Turn button is a player-side action. Do not allow manual turn
		// skipping while the opponent turn is active.
		if (gameState.activePlayer != gameState.humanPlayer) {
			BasicCommands.addPlayer1Notification(out, "Opponent Turn", 2);
			return;
		}
		// SC13/SC14: prevent ending turn while action pipeline is still running.
		if (gameState.actionLocked) {
			BasicCommands.addPlayer1Notification(out, "Action in progress", 2);
			return;
		}
		// SC12: clear active highlights/selection when ending the turn.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);
		
		// 1) SC07: Mana Drain at End Turn.
		// The ending player is human in this event path.
		gameState.humanPlayer.setMana(0);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

		// 2) SC05 + 2024-GameRules alignment:
		// draw happens at END of current player's turn (not at next-turn start).
		drawCard(out, gameState, gameState.humanPlayer);

		// 3) SC08: pass control to opponent.
		gameState.activePlayer = gameState.aiPlayer;
		BasicCommands.addPlayer1Notification(out, "Opponent Turn", 2);

		// 4) SC05: next active player gains mana = turnNumber + 1 (cap 9).
		int newMana = gameState.turnNumber + 1;
		if (newMana > 9) {
			newMana = 9;
		}
		gameState.aiPlayer.setMana(newMana);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);

		// SC15: reset per-turn action restrictions for units on the new active side.
		SimpleBoardLogic.resetActionFlagsForOwner(gameState, SimpleBoardLogic.ownerForActivePlayer(gameState));
		
	}

	/**
	 * True only after initialize has finished wiring all turn-related references.
	 */
	private boolean isTurnSystemReady(GameState gameState) {
		return gameState != null
				&& gameState.gameInitalised
				&& gameState.humanPlayer != null
				&& gameState.aiPlayer != null
				&& gameState.activePlayer != null;
	}

	/**
	 * Draw one card for the specified player.
	 * SC05 (rule-aligned): called when that player ENDs their turn.
	 * SC06: if hand size is already 6, burn the drawn card.
	 */
	private void drawCard(ActorRef out, GameState gameState, structures.basic.Player player) {
		// Check if deck is empty
		if (player.getDeck().isEmpty()) {
			// BasicCommands.addPlayer1Notification(out, "Deck is empty!", 2);
			return;
		}
		
		// Get the top card
		structures.basic.Card card = player.getDeck().get(0);
		
		// SC06: Overdraw Rule
		// Acceptance Criteria: If hand size >= 6, drawn card is removed from deck without entering hand.
		if (player.getHand().size() >= 6) {
			// Burn the card (Remove from deck but don't add to hand)
			player.removeCardFromDeck(card);
			if (player == gameState.humanPlayer) {
				BasicCommands.addPlayer1Notification(out, "Hand full! Card burned.", 2);
			}
			// Optional: Visualization of burning card could go here
		} else {
			// Normal Draw
			player.removeCardFromDeck(card);
			player.addCardToHand(card);
			// Draw card on UI (only for Human player usually)
			if (player == gameState.humanPlayer) {
				BasicCommands.drawCard(out, card, player.getHand().indexOf(card) + 1, 0);
			}
		}
	}

}
