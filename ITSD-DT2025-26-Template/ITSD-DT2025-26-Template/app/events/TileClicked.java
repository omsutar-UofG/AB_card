package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import game.SimpleBoardLogic;
import game.SimpleCardLogic;
import game.SimpleBoardLogic.HighlightPlan;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a tile.
 * The event returns the x (horizontal) and y (vertical) indices of the tile that was
 * clicked. Tile indices start at 1.
 * 
 * { 
 *   messageType = “tileClicked”
 *   tilex = <x index of the tile>
 *   tiley = <y index of the tile>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class TileClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		// SC10-SC15: movement/attack interactions are valid only after full initialize.
		if (!SimpleBoardLogic.isTurnSystemReady(gameState)) {
			return;
		}
		// Only accept tile interactions on the human turn in this phase.
		if (!SimpleBoardLogic.isHumanTurn(gameState)) {
			return;
		}
		// SC13-SC14: avoid concurrent clicks while a move/attack chain is in flight.
		if (gameState.actionLocked) {
			return;
		}

		int tilex = message.get("tilex").asInt();
		int tiley = message.get("tiley").asInt();
		Tile clickedTile = gameState.board[tilex][tiley];
		if (clickedTile == null) {
			return;
		}

		BetterUnit clickedUnit = SimpleBoardLogic.getUnitAt(gameState, tilex, tiley);
		String clickedTileKey = SimpleBoardLogic.tileKey(tilex, tiley);

		// SC23-SC28: if card targeting is active, tile click is consumed by card flow.
		if (SimpleCardLogic.resolveSelectedCardOnTile(out, gameState, clickedTile, clickedUnit)) {
			return;
		}

		// No current selection: selecting own unit should build highlights.
		if (gameState.selectedUnitId == null) {
			handleSelectionStart(out, gameState, clickedUnit);
			return;
		}

		BetterUnit selectedUnit = gameState.unitsById.get(gameState.selectedUnitId);
		if (selectedUnit == null) {
			SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
			return;
		}

		// Re-select another own unit to refresh movement/attack options.
		if (clickedUnit != null
				&& clickedUnit.getOwner() == GameState.OWNER_HUMAN
				&& clickedUnit.getId() != selectedUnit.getId()) {
			handleSelectionStart(out, gameState, clickedUnit);
			return;
		}

		// SC13: click a valid move tile to start movement animation.
		if (gameState.moveHighlightTiles.contains(clickedTileKey)
				&& clickedUnit == null
				&& !selectedUnit.isHasMoved()
				&& !selectedUnit.isHasAttacked()) {
			startMoveAction(out, gameState, selectedUnit, clickedTile, null);
			return;
		}

		// SC11 + SC14: click a valid red target tile to attack.
		if (gameState.attackHighlightTiles.contains(clickedTileKey)
				&& clickedUnit != null
				&& clickedUnit.getOwner() == GameState.OWNER_AI
				&& !selectedUnit.isHasAttacked()) {
			handleAttackSelection(out, gameState, selectedUnit, clickedUnit, clickedTileKey);
			return;
		}

		// SC12: any unrelated tile click clears current highlights and selection.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
	}

	/**
	 * SC10/SC11: Entry point when player clicks on a tile with potential own-unit selection.
	 */
	private void handleSelectionStart(ActorRef out, GameState gameState, BetterUnit clickedUnit) {
		if (clickedUnit == null || clickedUnit.getOwner() != GameState.OWNER_HUMAN) {
			SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
			return;
		}
		if (!SimpleBoardLogic.unitCanTakeAction(clickedUnit)) {
			BasicCommands.addPlayer1Notification(out, "Unit already acted this turn", 2);
			SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
			return;
		}

		gameState.selectedUnitId = clickedUnit.getId();
		HighlightPlan plan = SimpleBoardLogic.buildHighlightPlan(gameState, clickedUnit);
		SimpleBoardLogic.applyHighlightPlan(out, gameState, plan);
	}

	/**
	 * SC13: Shared movement start handler for plain move and move-then-attack chains.
	 */
	private void startMoveAction(
			ActorRef out,
			GameState gameState,
			BetterUnit mover,
			Tile destination,
			Integer optionalAttackTargetUnitId) {
		gameState.actionLocked = true;
		gameState.pendingMoveUnitId = mover.getId();
		gameState.pendingAttackTargetUnitId = optionalAttackTargetUnitId;
		BasicCommands.moveUnitToTile(out, mover, destination);
	}

	/**
	 * SC14 + SC16 + SC18:
	 * - If target is in current range now -> resolve direct combat exchange.
	 * - If target is only reachable after move -> start move and defer combat to UnitStopped.
	 */
	private void handleAttackSelection(
			ActorRef out,
			GameState gameState,
			BetterUnit attacker,
			BetterUnit defender,
			String defenderTileKey) {

		// Direct adjacent attack path.
		if (SimpleBoardLogic.isInAttackRange(attacker, defender)) {
			gameState.actionLocked = true;
			// SC16/SC17/SC18/SC19/SC20: full combat exchange (including counter-attack checks).
			SimpleBoardLogic.resolveCombatExchange(out, gameState, attacker, defender);
			// 2024 GameRules: attacking before moving forfeits movement.
			if (!attacker.isHasMoved()) {
				attacker.setHasMoved(true);
			}
			attacker.setHasAttacked(true);
			SimpleBoardLogic.clearPendingAction(gameState);
			// SC12: clear highlights after completing an action.
			SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
			return;
		}

		// Move-and-attack path.
		if (!attacker.isHasMoved()) {
			String approachTileKey = gameState.approachTileByEnemyTile.get(defenderTileKey);
			Tile approachTile = SimpleBoardLogic.getTileByKey(gameState, approachTileKey);
			if (approachTile != null) {
				startMoveAction(out, gameState, attacker, approachTile, defender.getId());
			}
		}
	}

}
