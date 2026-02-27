package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import game.SimpleBoardLogic;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;

/**
 * Indicates that a unit instance has stopped moving. 
 * The event reports the unique id of the unit.
 * 
 * { 
 *   messageType = “unitStopped”
 *   id = <unit id>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class UnitStopped implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		int unitid = message.get("id").asInt();
		int tilex = message.get("tilex").asInt();
		int tiley = message.get("tiley").asInt();

		if (!SimpleBoardLogic.isTurnSystemReady(gameState)) {
			return;
		}

		BetterUnit mover = gameState.unitsById.get(unitid);
		if (mover == null) {
			SimpleBoardLogic.clearPendingAction(gameState);
			return;
		}

		Tile destination = gameState.board[tilex][tiley];
		if (destination == null) {
			SimpleBoardLogic.clearPendingAction(gameState);
			return;
		}

		// SC13: update back-end unit position once move animation has finished.
		SimpleBoardLogic.moveUnitStateToTile(gameState, mover, destination);
		mover.setHasMoved(true);

		// SC14: complete deferred attack for move-and-attack flow.
		if (gameState.pendingAttackTargetUnitId != null) {
			BetterUnit defender = gameState.unitsById.get(gameState.pendingAttackTargetUnitId);
			if (defender != null && !mover.isHasAttacked()) {
				SimpleBoardLogic.executeAttack(out, gameState, mover, defender);
				mover.setHasAttacked(true);
			}
		}

		SimpleBoardLogic.clearPendingAction(gameState);
		// SC12: clear highlights after action completion.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
	}

}
