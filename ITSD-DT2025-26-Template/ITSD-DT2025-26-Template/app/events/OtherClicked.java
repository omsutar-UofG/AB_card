package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import game.SimpleBoardLogic;
import game.SimpleCardLogic;
import structures.GameState;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * somewhere that is not on a card tile or the end-turn button.
 * 
 * { 
 *   messageType = “otherClicked”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class OtherClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		// SC12: Clicking any non-action area clears active highlights/selection.
		if (!SimpleBoardLogic.isGameActive(gameState)) {
			return;
		}
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);
		SimpleCardLogic.clearCardSelectionAndHandHighlight(out, gameState);
	}

}


