package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import play.libs.Json;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.UnitAnimationType;

/**
 * Shared board and combat helpers for SC10-SC20.
 *
 * This class centralizes:
 * - tile highlighting rules
 * - occupancy bookkeeping
 * - movement reachability checks
 * - attack/counter-attack resolution
 */
public final class SimpleBoardLogic {

	public static final int TILE_MODE_NORMAL = 0;
	public static final int TILE_MODE_MOVE_HIGHLIGHT = 1;
	public static final int TILE_MODE_ATTACK_HIGHLIGHT = 2;
	private static final int MIN_ANIMATION_WAIT_MS = 120;
	private static final int DEFAULT_ATTACK_WAIT_MS = 250;
	private static final int DEFAULT_IMPACT_WAIT_MS = 220;
	private static final int DEFAULT_DEATH_WAIT_MS = 450;
	private static final int MAX_ANIMATION_WAIT_MS = 900;
	private static final Random RNG = new Random();

	private SimpleBoardLogic() {}

	/**
	 * SC10/SC11/SC12: Stable tile key format used by highlight and occupancy sets.
	 */
	public static String tileKey(int tilex, int tiley) {
		return tilex + "-" + tiley;
	}

	/**
	 * Basic readiness check for event processing that depends on turn and players.
	 */
	public static boolean isTurnSystemReady(GameState gameState) {
		return gameState != null
				&& gameState.gameInitalised
				&& gameState.humanPlayer != null
				&& gameState.aiPlayer != null
				&& gameState.activePlayer != null;
	}

	public static int ownerForActivePlayer(GameState gameState) {
		return gameState.activePlayer == gameState.humanPlayer ? GameState.OWNER_HUMAN : GameState.OWNER_AI;
	}

	public static boolean isHumanTurn(GameState gameState) {
		return gameState.activePlayer == gameState.humanPlayer;
	}

	/**
	 * SC40: true when match interactions are still allowed (initialized and not game over).
	 */
	public static boolean isGameActive(GameState gameState) {
		return isTurnSystemReady(gameState) && !gameState.gameOver;
	}

	/**
	 * SC10-SC15: Register a unit in both id-index and tile-occupancy index.
	 */
	public static void registerUnit(GameState gameState, BetterUnit unit, Tile tile) {
		unit.setPositionByTile(tile);
		gameState.unitsById.put(unit.getId(), unit);
		gameState.unitIdByTile.put(tileKey(tile.getTilex(), tile.getTiley()), unit.getId());
	}

	public static BetterUnit getUnitAt(GameState gameState, int tilex, int tiley) {
		Integer unitId = gameState.unitIdByTile.get(tileKey(tilex, tiley));
		if (unitId == null) {
			return null;
		}
		return gameState.unitsById.get(unitId);
	}

	/**
	 * SC10 visual-path fix:
	 * Choose movement axis order that avoids visually passing through occupied intermediate tiles.
	 * Returns true when Y-first path is preferred; false keeps default X-first.
	 */
	public static boolean shouldMoveYFirstForVisualPath(GameState gameState, BetterUnit unit, Tile destinationTile) {
		if (gameState == null || unit == null || destinationTile == null || unit.getPosition() == null) {
			return false;
		}
		int sx = unit.getPosition().getTilex();
		int sy = unit.getPosition().getTiley();
		int dx = destinationTile.getTilex() - sx;
		int dy = destinationTile.getTiley() - sy;

		// Axis order only matters when both coordinates change.
		if (dx == 0 || dy == 0) {
			return false;
		}

		boolean xFirstBlocked = pathHasBlockingIntermediate(gameState, unit, sx, sy, destinationTile.getTilex(), destinationTile.getTiley(), false);
		boolean yFirstBlocked = pathHasBlockingIntermediate(gameState, unit, sx, sy, destinationTile.getTilex(), destinationTile.getTiley(), true);

		if (xFirstBlocked && !yFirstBlocked) {
			return true;
		}
		if (!xFirstBlocked && yFirstBlocked) {
			return false;
		}
		// Tie-breaker: preserve original default.
		return false;
	}

	/**
	 * SC13/SC14: Back-end position update after move animation has completed.
	 */
	public static void moveUnitStateToTile(GameState gameState, BetterUnit unit, Tile destinationTile) {
		String oldTile = tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley());
		gameState.unitIdByTile.remove(oldTile);
		unit.setPositionByTile(destinationTile);
		gameState.unitIdByTile.put(tileKey(destinationTile.getTilex(), destinationTile.getTiley()), unit.getId());
	}

	public static boolean unitCanTakeAction(BetterUnit unit) {
		return unit != null && unit.getHealth() > 0 && !(unit.isHasMoved() && unit.isHasAttacked());
	}

	/**
	 * SC12: Clear current selection markers without touching board visuals.
	 */
	public static void clearSelection(GameState gameState) {
		gameState.selectedUnitId = null;
		gameState.approachTileByEnemyTile.clear();
	}

	/**
	 * SC12: Clear all tile highlights and restore normal tile mode.
	 */
	public static void clearHighlights(ActorRef out, GameState gameState) {
		Set<String> allHighlighted = new HashSet<String>();
		allHighlighted.addAll(gameState.moveHighlightTiles);
		allHighlighted.addAll(gameState.attackHighlightTiles);
		for (String key : allHighlighted) {
			Tile tile = getTileByKey(gameState, key);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_NORMAL);
			}
		}
		gameState.moveHighlightTiles.clear();
		gameState.attackHighlightTiles.clear();
	}

	public static void clearSelectionAndHighlights(ActorRef out, GameState gameState) {
		clearHighlights(out, gameState);
		clearSelection(gameState);
	}

	/**
	 * SC13/SC14: Clear in-flight action continuation state.
	 */
	public static void clearPendingAction(GameState gameState) {
		gameState.pendingMoveUnitId = null;
		gameState.pendingAttackTargetUnitId = null;
		gameState.actionLocked = false;
	}

	/**
	 * SC15: Reset action flags for units belonging to the current active side.
	 */
	public static void resetActionFlagsForOwner(GameState gameState, int owner) {
		// SC27 UX:
		// refresh per-turn stun-lock marker set for this owner.
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() == owner) {
				gameState.stunnedThisTurnUnitIds.remove(unit.getId());
			}
		}

		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() == owner && unit.getHealth() > 0) {
				// SC27:
				// if stunned for this owner turn, consume the stun and lock actions for
				// exactly this turn; otherwise refresh normal per-turn action rights.
				if (unit.getStunTurnsRemaining() > 0) {
					unit.setHasMoved(true);
					unit.setHasAttacked(true);
					unit.setStunTurnsRemaining(unit.getStunTurnsRemaining() - 1);
					gameState.stunnedThisTurnUnitIds.add(unit.getId());
				} else {
					unit.setHasMoved(false);
					unit.setHasAttacked(false);
				}
			}
		}
	}

	/**
	 * SC23/SC28 + 2024 GameRules:
	 * Units can be summoned onto unoccupied tiles adjacent to any friendly unit.
	 */
	public static Set<String> computeAdjacentUnoccupiedTilesForOwner(GameState gameState, int owner) {
		Set<String> result = new HashSet<String>();
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() != owner || unit.getHealth() <= 0) {
				continue;
			}
			int ux = unit.getPosition().getTilex();
			int uy = unit.getPosition().getTiley();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dy == 0) {
						continue;
					}
					int tx = ux + dx;
					int ty = uy + dy;
					if (!inBoard(gameState, tx, ty)) {
						continue;
					}
					String key = tileKey(tx, ty);
					if (!gameState.unitIdByTile.containsKey(key)) {
						result.add(key);
					}
				}
			}
		}
		return result;
	}

	/**
	 * SC10 + SC11:
	 * Build movement and attack highlight sets for one selected unit.
	 */
	public static HighlightPlan buildHighlightPlan(GameState gameState, BetterUnit selectedUnit) {
		HighlightPlan plan = new HighlightPlan();
		List<BetterUnit> adjacentEnemyProvokes = findAdjacentEnemyProvokeUnits(gameState, selectedUnit);

		// SC10 + SC34 + SC36 + 2024 GameRules:
		// if a unit already attacked, it forfeits moving this turn.
		// if adjacent to enemy Provoke, movement is locked.
		if (!selectedUnit.isHasMoved() && !selectedUnit.isHasAttacked() && adjacentEnemyProvokes.isEmpty()) {
			if (selectedUnit.isFlying()) {
				plan.moveTileKeys.addAll(computeAllUnoccupiedTiles(gameState, selectedUnit));
			} else {
				plan.moveTileKeys.addAll(computeReachableUnoccupiedTiles(gameState, selectedUnit));
			}
		}

		// SC11: show red enemy targets if attack is still available.
		if (!selectedUnit.isHasAttacked()) {
			// SC34:
			// if adjacent to enemy Provoke, only those Provoke units are legal targets.
			if (!adjacentEnemyProvokes.isEmpty()) {
				for (BetterUnit provokeUnit : adjacentEnemyProvokes) {
					if (isInAttackRange(selectedUnit, provokeUnit)) {
						plan.attackTileKeys.add(tileKey(provokeUnit.getPosition().getTilex(), provokeUnit.getPosition().getTiley()));
					}
				}
				return plan;
			}

			for (BetterUnit candidate : gameState.unitsById.values()) {
				if (candidate.getOwner() == selectedUnit.getOwner() || candidate.getHealth() <= 0) {
					continue;
				}
				String enemyKey = tileKey(candidate.getPosition().getTilex(), candidate.getPosition().getTiley());
				if (isInAttackRange(selectedUnit, candidate)) {
					plan.attackTileKeys.add(enemyKey);
					continue;
				}

				// SC14 support: mark reachable non-adjacent enemies in red if we can
				// move to an attack-adjacent tile this turn.
				if (!selectedUnit.isHasMoved()) {
					String approachTile = findApproachTileForEnemy(gameState, selectedUnit, candidate, plan.moveTileKeys);
					if (approachTile != null) {
						plan.attackTileKeys.add(enemyKey);
						plan.approachByEnemyTile.put(enemyKey, approachTile);
					}
				}
			}
		}

		return plan;
	}

	public static void applyHighlightPlan(ActorRef out, GameState gameState, HighlightPlan plan) {
		clearHighlights(out, gameState);
		gameState.moveHighlightTiles.addAll(plan.moveTileKeys);
		gameState.attackHighlightTiles.addAll(plan.attackTileKeys);
		gameState.approachTileByEnemyTile.clear();
		gameState.approachTileByEnemyTile.putAll(plan.approachByEnemyTile);

		for (String moveTile : gameState.moveHighlightTiles) {
			Tile tile = getTileByKey(gameState, moveTile);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_MOVE_HIGHLIGHT);
			}
		}
		for (String attackTile : gameState.attackHighlightTiles) {
			Tile tile = getTileByKey(gameState, attackTile);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_ATTACK_HIGHLIGHT);
			}
		}
	}

	public static boolean isInAttackRange(BetterUnit attacker, BetterUnit defender) {
		int dx = Math.abs(attacker.getPosition().getTilex() - defender.getPosition().getTilex());
		int dy = Math.abs(attacker.getPosition().getTiley() - defender.getPosition().getTiley());
		// 2024 GameRules: default attack supports adjacent diagonals.
		return isTileInAttackRange(dx, dy, attacker.getAttackRange());
	}

	/**
	 * SC16 + SC17 + SC19 + SC20:
	 * Resolve a one-way attack hit (animation + damage + death/avatar sync).
	 *
	 * Note:
	 * - This method does not perform counter-attack.
	 * - Counter-attack is handled in resolveCombatExchange(...).
	 */
	public static void executeAttack(ActorRef out, GameState gameState, BetterUnit attacker, BetterUnit defender) {
		if (gameState.gameOver) {
			return;
		}
		if (!isUnitAlive(attacker) || !isUnitAlive(defender)) {
			return;
		}
		// SC16: keep attack animation visible before processing hit/death.
		int attackAnimationMs = BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);
		waitForAnimation(attackAnimationMs, DEFAULT_ATTACK_WAIT_MS);
		applySingleHitDamage(out, gameState, attacker, defender);
	}

	/**
	 * SC16 + SC17 + SC18 + SC19 + SC20:
	 * Full combat exchange for one click action:
	 * 1) attacker hits defender
	 * 2) if defender survives and can hit without moving, defender counter-attacks
	 */
	public static void resolveCombatExchange(ActorRef out, GameState gameState, BetterUnit attacker, BetterUnit defender) {
		if (gameState.gameOver) {
			return;
		}
		if (!isUnitAlive(attacker) || !isUnitAlive(defender)) {
			return;
		}
		if (!canAttackTargetRespectingProvoke(gameState, attacker, defender)) {
			return;
		}

		// First strike from selected attacker.
		executeAttack(out, gameState, attacker, defender);

		BetterUnit refreshedAttacker = gameState.unitsById.get(attacker.getId());
		BetterUnit refreshedDefender = gameState.unitsById.get(defender.getId());
		if (gameState.gameOver) {
			return;
		}
		if (!isUnitAlive(refreshedAttacker) || !isUnitAlive(refreshedDefender)) {
			return;
		}

		// SC18 + 2024 GameRules:
		// counter-attack only if defender survived and is already in range.
		if (isInAttackRange(refreshedDefender, refreshedAttacker)
				&& canAttackTargetRespectingProvoke(gameState, refreshedDefender, refreshedAttacker)) {
			executeAttack(out, gameState, refreshedDefender, refreshedAttacker);
		}
	}

	public static Tile getTileByKey(GameState gameState, String key) {
		int[] xy = parseKey(key);
		if (xy == null || !inBoard(gameState, xy[0], xy[1])) {
			return null;
		}
		return gameState.board[xy[0]][xy[1]];
	}

	/**
	 * Shared tile-key parser for AI/path helpers using x-y key format.
	 */
	public static int[] parseTileKey(String key) {
		return parseKey(key);
	}

	private static Set<String> computeReachableUnoccupiedTiles(GameState gameState, BetterUnit unit) {
		Set<String> reachable = new HashSet<String>();
		int startX = unit.getPosition().getTilex();
		int startY = unit.getPosition().getTiley();
		int maxStraightSteps = Math.max(0, unit.getMoveRange());

		// 2024 GameRules: default movement is up to two straight tiles (cardinal).
		int[][] cardinal = new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dir : cardinal) {
			for (int step = 1; step <= maxStraightSteps; step++) {
				int nx = startX + (dir[0] * step);
				int ny = startY + (dir[1] * step);
				if (!inBoard(gameState, nx, ny)) {
					break;
				}
				String key = tileKey(nx, ny);
				// Cannot pass through or land on occupied tiles.
				if (gameState.unitIdByTile.containsKey(key) && !Objects.equals(gameState.unitIdByTile.get(key), unit.getId())) {
					break;
				}
				reachable.add(key);
			}
		}

		// 2024 GameRules: default movement also allows one diagonal tile.
		if (maxStraightSteps >= 1) {
			int[][] diagonal = new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
			for (int[] dir : diagonal) {
				int nx = startX + dir[0];
				int ny = startY + dir[1];
				if (!inBoard(gameState, nx, ny)) {
					continue;
				}
				String key = tileKey(nx, ny);
				if (!gameState.unitIdByTile.containsKey(key) || Objects.equals(gameState.unitIdByTile.get(key), unit.getId())) {
					reachable.add(key);
				}
			}
		}

		reachable.remove(tileKey(startX, startY));
		return reachable;
	}

	/**
	 * SC10 visual-path helper:
	 * Tests if an orthogonal L-path contains an occupied intermediate tile.
	 */
	private static boolean pathHasBlockingIntermediate(
			GameState gameState,
			BetterUnit mover,
			int sx,
			int sy,
			int tx,
			int ty,
			boolean yFirst) {
		int cx = sx;
		int cy = sy;
		int stepX = Integer.compare(tx, sx);
		int stepY = Integer.compare(ty, sy);

		if (yFirst) {
			while (cy != ty) {
				cy += stepY;
				if (cx == tx && cy == ty) {
					break;
				}
				if (isOccupiedByOtherUnit(gameState, mover, cx, cy)) {
					return true;
				}
			}
			while (cx != tx) {
				cx += stepX;
				if (cx == tx && cy == ty) {
					break;
				}
				if (isOccupiedByOtherUnit(gameState, mover, cx, cy)) {
					return true;
				}
			}
			return false;
		}

		while (cx != tx) {
			cx += stepX;
			if (cx == tx && cy == ty) {
				break;
			}
			if (isOccupiedByOtherUnit(gameState, mover, cx, cy)) {
				return true;
			}
		}
		while (cy != ty) {
			cy += stepY;
			if (cx == tx && cy == ty) {
				break;
			}
			if (isOccupiedByOtherUnit(gameState, mover, cx, cy)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * SC10 visual-path helper:
	 * True only when tile is occupied by another unit (not the mover itself).
	 */
	private static boolean isOccupiedByOtherUnit(GameState gameState, BetterUnit mover, int x, int y) {
		if (!inBoard(gameState, x, y)) {
			return false;
		}
		Integer unitId = gameState.unitIdByTile.get(tileKey(x, y));
		return unitId != null && !Objects.equals(unitId, mover.getId());
	}

	/**
	 * SC36:
	 * Flying units can move to any unoccupied tile on the board.
	 */
	public static Set<String> computeAllUnoccupiedTiles(GameState gameState, BetterUnit unit) {
		Set<String> freeTiles = new HashSet<String>();
		for (int x = 0; x < gameState.board.length; x++) {
			for (int y = 0; y < gameState.board[x].length; y++) {
				if (gameState.board[x][y] == null) {
					continue;
				}
				String key = tileKey(x, y);
				if (!gameState.unitIdByTile.containsKey(key) || Objects.equals(gameState.unitIdByTile.get(key), unit.getId())) {
					freeTiles.add(key);
				}
			}
		}
		freeTiles.remove(tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley()));
		return freeTiles;
	}

	/**
	 * SC34:
	 * Returns enemy Provoke units adjacent to source (8-neighbour adjacency).
	 */
	public static List<BetterUnit> findAdjacentEnemyProvokeUnits(GameState gameState, BetterUnit source) {
		List<BetterUnit> result = new ArrayList<BetterUnit>();
		if (source == null || source.getHealth() <= 0) {
			return result;
		}
		int sx = source.getPosition().getTilex();
		int sy = source.getPosition().getTiley();
		for (BetterUnit candidate : gameState.unitsById.values()) {
			if (candidate.getHealth() <= 0 || candidate.getOwner() == source.getOwner() || !candidate.isProvoke()) {
				continue;
			}
			int dx = Math.abs(candidate.getPosition().getTilex() - sx);
			int dy = Math.abs(candidate.getPosition().getTiley() - sy);
			if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) {
				result.add(candidate);
			}
		}
		return result;
	}

	/**
	 * SC34:
	 * True when attacker is currently locked by adjacent enemy Provoke units.
	 */
	public static boolean isMovementLockedByProvoke(GameState gameState, BetterUnit attacker) {
		return !findAdjacentEnemyProvokeUnits(gameState, attacker).isEmpty();
	}

	/**
	 * SC34:
	 * Attack legality considering Provoke lock. If locked, only adjacent enemy Provoke units are valid.
	 */
	public static boolean canAttackTargetRespectingProvoke(GameState gameState, BetterUnit attacker, BetterUnit defender) {
		if (attacker == null || defender == null || attacker.getOwner() == defender.getOwner()) {
			return false;
		}
		List<BetterUnit> adjacentProvokes = findAdjacentEnemyProvokeUnits(gameState, attacker);
		if (adjacentProvokes.isEmpty()) {
			return true;
		}
		for (BetterUnit provokeUnit : adjacentProvokes) {
			if (provokeUnit.getId() == defender.getId()) {
				return true;
			}
		}
		return false;
	}

	private static String findApproachTileForEnemy(
			GameState gameState,
			BetterUnit attacker,
			BetterUnit enemy,
			Set<String> reachableMoves) {

		int ex = enemy.getPosition().getTilex();
		int ey = enemy.getPosition().getTiley();
		int ax = attacker.getPosition().getTilex();
		int ay = attacker.getPosition().getTiley();
		int attackRange = Math.max(1, attacker.getAttackRange());

		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String candidate : reachableMoves) {
			int[] xy = parseKey(candidate);
			if (xy == null || !inBoard(gameState, xy[0], xy[1])) {
				continue;
			}
			int dx = Math.abs(xy[0] - ex);
			int dy = Math.abs(xy[1] - ey);
			if (!isTileInAttackRange(dx, dy, attackRange)) {
				continue;
			}
			int distance = Math.abs(xy[0] - ax) + Math.abs(xy[1] - ay);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean isTileInAttackRange(int dx, int dy, int range) {
		return !(dx == 0 && dy == 0) && dx <= range && dy <= range;
	}

	/**
	 * SC17 + SC19 + SC20:
	 * Apply one hit value and update UI/state based on post-hit HP.
	 */
	private static void applySingleHitDamage(ActorRef out, GameState gameState, BetterUnit source, BetterUnit target) {
		if (gameState.gameOver) {
			return;
		}
		int damage = Math.max(0, source.getAttack());
		int previousHealth = target.getHealth();
		int nextHealth = Math.max(0, target.getHealth() - damage);
		target.setHealth(nextHealth);
		BasicCommands.setUnitHealth(out, target, nextHealth);

		// SC33:
		// On-hit trigger when a unit deals damage to an enemy non-avatar unit.
		if (damage > 0
				&& source.getOwner() != target.getOwner()
				&& !target.isAvatar()) {
			triggerOnUnitDealsDamage(out, gameState, source, target, damage);
		}

		// SC32:
		// Avatar damage trigger (Zeal) fires only when avatar survives the hit.
		if (target.isAvatar() && damage > 0 && nextHealth > 0 && nextHealth < previousHealth) {
			triggerOnAvatarDamaged(out, gameState, target.getOwner(), damage);
		}
		// SC40:
		// Avatar reaching 0 HP ends game immediately (no delayed death pipeline).
		if (target.isAvatar() && nextHealth <= 0) {
			syncAvatarHealth(out, gameState, target);
			declareGameOver(out, gameState, source.getOwner());
			return;
		}

		if (nextHealth > 0) {
			// SC16/SC17: keep hit reaction visible before next combat step.
			int hitAnimationMs = BasicCommands.playUnitAnimation(out, target, UnitAnimationType.hit);
			waitForAnimation(hitAnimationMs, DEFAULT_IMPACT_WAIT_MS);
		} else {
			// SC31:
			// death-related triggers resolve before removing the dead unit from board indexes.
			triggerOnUnitDeath(out, gameState, target, source);

			// SC19: death should be seen before removing non-avatar units.
			int deathAnimationMs = BasicCommands.playUnitAnimation(out, target, UnitAnimationType.death);
			waitForAnimation(deathAnimationMs, DEFAULT_DEATH_WAIT_MS);

			// SC19:
			// non-avatar units are removed from board after death animation trigger.
			if (!target.isAvatar()) {
				BasicCommands.deleteUnit(out, target);
				removeUnitFromIndexes(gameState, target);
			}
		}

		// SC20:
		// Avatar damage/death always syncs to owning player's health UI.
		if (target.isAvatar()) {
			syncAvatarHealth(out, gameState, target);
		}
	}

	/**
	 * SC24/SC26:
	 * Apply spell damage directly to a target unit (without attacker animation).
	 */
	public static void applySpellDamage(ActorRef out, GameState gameState, BetterUnit target, int damage) {
		if (gameState.gameOver) {
			return;
		}
		if (!isUnitAlive(target)) {
			return;
		}
		int clampedDamage = Math.max(0, damage);
		int previousHealth = target.getHealth();
		int nextHealth = Math.max(0, target.getHealth() - clampedDamage);
		target.setHealth(nextHealth);
		BasicCommands.setUnitHealth(out, target, nextHealth);

		// SC32:
		// Avatar damage trigger (Zeal) when avatar survives direct spell damage.
		if (target.isAvatar() && clampedDamage > 0 && nextHealth > 0 && nextHealth < previousHealth) {
			triggerOnAvatarDamaged(out, gameState, target.getOwner(), clampedDamage);
		}
		// SC40:
		// Avatar reaching 0 HP ends game immediately (no delayed death pipeline).
		if (target.isAvatar() && nextHealth <= 0) {
			syncAvatarHealth(out, gameState, target);
			int winnerOwner = target.getOwner() == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
			declareGameOver(out, gameState, winnerOwner);
			return;
		}

		if (nextHealth > 0) {
			int hitAnimationMs = BasicCommands.playUnitAnimation(out, target, UnitAnimationType.hit);
			waitForAnimation(hitAnimationMs, DEFAULT_IMPACT_WAIT_MS);
		} else {
			// SC31:
			// death-related triggers resolve before removing unit from board indexes.
			triggerOnUnitDeath(out, gameState, target, null);
			int deathAnimationMs = BasicCommands.playUnitAnimation(out, target, UnitAnimationType.death);
			waitForAnimation(deathAnimationMs, DEFAULT_DEATH_WAIT_MS);
			if (!target.isAvatar()) {
				BasicCommands.deleteUnit(out, target);
				removeUnitFromIndexes(gameState, target);
			}
		}

		if (target.isAvatar()) {
			syncAvatarHealth(out, gameState, target);
		}
	}

	/**
	 * SC25:
	 * Heal a target unit without exceeding its configured max health.
	 */
	public static void applyHeal(ActorRef out, GameState gameState, BetterUnit target, int amount) {
		if (target == null || target.getHealth() <= 0) {
			return;
		}
		int cap = Math.max(1, target.getMaxHealth());
		int healAmount = Math.max(0, amount);
		int nextHealth = Math.min(cap, target.getHealth() + healAmount);
		target.setHealth(nextHealth);
		BasicCommands.setUnitHealth(out, target, nextHealth);

		if (target.isAvatar()) {
			syncAvatarHealth(out, gameState, target);
		}
	}

	/**
	 * SC32:
	 * Zeal trigger - when avatar takes damage, allied Zeal units gain +2 attack permanently.
	 */
	private static void triggerOnAvatarDamaged(ActorRef out, GameState gameState, int damagedAvatarOwner, int damageAmount) {
		if (damageAmount <= 0 || gameState.gameOver) {
			return;
		}
		List<BetterUnit> units = snapshotUnitsById(gameState);
		for (BetterUnit unit : units) {
			if (unit.getHealth() <= 0 || unit.getOwner() != damagedAvatarOwner || !unit.isZealOnAvatarDamaged()) {
				continue;
			}
			unit.setAttack(unit.getAttack() + 2);
			BasicCommands.setUnitAttack(out, unit, unit.getAttack());
		}
	}

	/**
	 * SC33:
	 * UnitDealsDamage trigger. Current deck-specific implementation is Horn artifact on human avatar.
	 */
	private static void triggerOnUnitDealsDamage(
			ActorRef out,
			GameState gameState,
			BetterUnit source,
			BetterUnit target,
			int damageAmount) {
		if (damageAmount <= 0 || gameState.gameOver) {
			return;
		}
		// 2025-26 Deck spec:
		// Horn of the Forsaken (Artifact 3): when owner avatar deals damage to an enemy unit,
		// summon a Wraithling on a random adjacent unoccupied tile.
		if (source.isAvatar()
				&& source.getOwner() == GameState.OWNER_HUMAN
				&& target.getOwner() == GameState.OWNER_AI
				&& !target.isAvatar()
				&& gameState.humanHornCharges > 0) {
			gameState.humanHornCharges--;
			summonRandomAdjacentWraithling(out, gameState, source, GameState.OWNER_HUMAN);
			// SC33 debug visibility:
			// show remaining Horn artifact charges after each on-hit trigger.
			BasicCommands.addPlayer1Notification(out, "Horn charges: " + gameState.humanHornCharges, 2);
		}

		// Generic extension point for future on-hit summon units.
		if (source.isOnHitSummonWraithling()) {
			summonRandomAdjacentWraithling(out, gameState, source, source.getOwner());
		}
	}

	/**
	 * SC31:
	 * Death trigger dispatcher. Runs before deleting the dead unit from indexes.
	 */
	private static void triggerOnUnitDeath(
			ActorRef out,
			GameState gameState,
			BetterUnit deadUnit,
			BetterUnit killer) {
		if (gameState.gameOver || deadUnit == null) {
			return;
		}
		// SC40 precedence:
		// avatar death should terminate the game immediately, so we do not fan out deathwatch chains.
		if (deadUnit.isAvatar()) {
			return;
		}

		List<BetterUnit> watchers = snapshotUnitsById(gameState);
		for (BetterUnit watcher : watchers) {
			if (watcher.getHealth() <= 0 || watcher.getId() == deadUnit.getId()) {
				continue;
			}
			if (watcher.isDeathwatchBadOmen()) {
				watcher.setAttack(watcher.getAttack() + 1);
				BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
			}
			if (watcher.isDeathwatchShadowWatcher()) {
				watcher.setAttack(watcher.getAttack() + 1);
				watcher.setMaxHealth(watcher.getMaxHealth() + 1);
				watcher.setHealth(watcher.getHealth() + 1);
				BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
				BasicCommands.setUnitHealth(out, watcher, watcher.getHealth());
			}
			if (watcher.isDeathwatchBloodmoon()) {
				summonRandomAdjacentWraithling(out, gameState, watcher, watcher.getOwner());
			}
			if (watcher.isDeathwatchShadowdancer()) {
				int enemyOwner = watcher.getOwner() == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
				BetterUnit enemyAvatar = getAvatarUnitForOwner(gameState, enemyOwner);
				if (enemyAvatar != null && enemyAvatar.getHealth() > 0) {
					applySpellDamage(out, gameState, enemyAvatar, 1);
				}
				if (watcher.getHealth() > 0) {
					applyHeal(out, gameState, watcher, 1);
				}
			}
			if (gameState.gameOver) {
				return;
			}
		}
	}

	/**
	 * SC30/SC31/SC33 helper:
	 * Summons one Wraithling on a random adjacent unoccupied tile.
	 */
	public static boolean summonRandomAdjacentWraithling(
			ActorRef out,
			GameState gameState,
			BetterUnit centerUnit,
			int owner) {
		List<Tile> candidates = new ArrayList<Tile>();
		int cx = centerUnit.getPosition().getTilex();
		int cy = centerUnit.getPosition().getTiley();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) {
					continue;
				}
				int tx = cx + dx;
				int ty = cy + dy;
				if (!inBoard(gameState, tx, ty)) {
					continue;
				}
				String key = tileKey(tx, ty);
				if (!gameState.unitIdByTile.containsKey(key)) {
					candidates.add(gameState.board[tx][ty]);
				}
			}
		}
		if (candidates.isEmpty()) {
			return false;
		}
		Tile tile = candidates.get(RNG.nextInt(candidates.size()));
		BetterUnit wraithling = (BetterUnit) utils.BasicObjectBuilders.loadUnit(
				utils.StaticConfFiles.wraithling,
				gameState.nextUnitId,
				BetterUnit.class);
		if (wraithling == null) {
			return false;
		}
		gameState.nextUnitId++;
		wraithling.setOwner(owner);
		wraithling.setAvatar(false);
		wraithling.setAttack(1);
		wraithling.setHealth(1);
		wraithling.setMaxHealth(1);
		wraithling.setMoveRange(2);
		wraithling.setAttackRange(1);
		wraithling.setHasMoved(true);
		wraithling.setHasAttacked(true);
		wraithling.setStunTurnsRemaining(0);
		wraithling.setPositionByTile(tile);

		BasicCommands.drawUnit(out, wraithling, tile);
		BasicCommands.setUnitAttack(out, wraithling, wraithling.getAttack());
		BasicCommands.setUnitHealth(out, wraithling, wraithling.getHealth());
		registerUnit(gameState, wraithling, tile);
		return true;
	}

	/**
	 * SC40:
	 * Enter terminal game-over state once and notify front-end for overlay rendering.
	 */
	public static void declareGameOver(ActorRef out, GameState gameState, int winnerOwner) {
		if (gameState.gameOver) {
			return;
		}
		gameState.gameOver = true;
		gameState.winnerOwner = winnerOwner;
		clearSelectionAndHighlights(out, gameState);
		clearPendingAction(gameState);

		ObjectNode gameOverMessage = Json.newObject();
		gameOverMessage.put("messagetype", "gameover");
		gameOverMessage.put("winnerOwner", winnerOwner);
		gameOverMessage.put("winnerText", winnerOwner == GameState.OWNER_HUMAN ? "You Win" : "You Lose");
		out.tell(gameOverMessage, out);
	}

	/**
	 * Returns avatar unit for owner using tracked ids first, with fallback scan.
	 */
	public static BetterUnit getAvatarUnitForOwner(GameState gameState, int owner) {
		Integer avatarId = owner == GameState.OWNER_HUMAN ? gameState.humanAvatarUnitId : gameState.aiAvatarUnitId;
		if (avatarId != null) {
			BetterUnit avatar = gameState.unitsById.get(avatarId);
			if (avatar != null) {
				return avatar;
			}
		}
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.isAvatar() && unit.getOwner() == owner) {
				return unit;
			}
		}
		return null;
	}

	/**
	 * Utility snapshot for trigger pipelines that may delete units while iterating.
	 */
	private static List<BetterUnit> snapshotUnitsById(GameState gameState) {
		List<BetterUnit> snapshot = new ArrayList<BetterUnit>(gameState.unitsById.values());
		Collections.sort(snapshot, (a, b) -> Integer.compare(a.getId(), b.getId()));
		return snapshot;
	}

	private static void removeUnitFromIndexes(GameState gameState, BetterUnit unit) {
		gameState.unitIdByTile.remove(tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley()));
		gameState.unitsById.remove(unit.getId());
	}

	private static void syncAvatarHealth(ActorRef out, GameState gameState, BetterUnit avatarUnit) {
		int hp = Math.max(0, avatarUnit.getHealth());
		if (avatarUnit.getOwner() == GameState.OWNER_HUMAN) {
			int previousHp = gameState.humanPlayer.getHealth();
			gameState.humanPlayer.setHealth(hp);
			BasicCommands.setPlayer1Health(out, gameState.humanPlayer);
			// Debug visibility requested:
			// announce avatar HP whenever it decreases.
			if (hp < previousHp) {
				BasicCommands.addPlayer1Notification(out, "Your Avatar HP: " + hp, 2);
			}
		} else {
			int previousHp = gameState.aiPlayer.getHealth();
			gameState.aiPlayer.setHealth(hp);
			BasicCommands.setPlayer2Health(out, gameState.aiPlayer);
			// Debug visibility requested:
			// announce AI avatar HP whenever it decreases (helps trace sudden wins).
			if (hp < previousHp) {
				BasicCommands.addPlayer1Notification(out, "AI Avatar HP: " + hp, 2);
			}
		}
	}

	private static boolean isUnitAlive(BetterUnit unit) {
		return unit != null && unit.getHealth() > 0;
	}

	/**
	 * SC16-SC20 visual sequencing helper:
	 * Avoid immediate animation overwrite by spacing combat steps in backend dispatch order.
	 */
	private static void waitForAnimation(int estimatedMs, int fallbackMs) {
		int waitMs = fallbackMs;
		if (estimatedMs > 0) {
			waitMs = Math.max(MIN_ANIMATION_WAIT_MS, Math.min(MAX_ANIMATION_WAIT_MS, estimatedMs));
		}
		try {
			Thread.sleep(waitMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static int[] parseKey(String key) {
		String[] split = key.split("-");
		if (split.length != 2) {
			return null;
		}
		try {
			return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean inBoard(GameState gameState, int x, int y) {
		return x >= 0
				&& y >= 0
				&& x < gameState.board.length
				&& y < gameState.board[0].length
				&& gameState.board[x][y] != null;
	}

	public static class HighlightPlan {
		public Set<String> moveTileKeys = new HashSet<String>();
		public Set<String> attackTileKeys = new HashSet<String>();
		public Map<String, String> approachByEnemyTile = new HashMap<String, String>();
	}
}
