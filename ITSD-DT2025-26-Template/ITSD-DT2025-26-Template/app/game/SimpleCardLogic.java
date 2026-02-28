package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Shared card-play logic for SC21-SC29.
 *
 * Rule priority used in this class:
 * 1) 2024-GameRules
 * 2) 2025-26 deck card specification
 * 3) story-card generic wording
 */
public final class SimpleCardLogic {

	public static final String TARGET_SUMMON_TILE = "SUMMON_TILE";
	public static final String TARGET_ALLY_UNIT = "ALLY_UNIT";
	public static final String TARGET_ENEMY_UNIT = "ENEMY_UNIT";
	public static final String TARGET_ENEMY_NON_AVATAR = "ENEMY_NON_AVATAR";
	public static final String TARGET_SELF_AVATAR = "SELF_AVATAR";
	public static final String TARGET_NONE = "NONE";

	private static final int CARD_MODE_NORMAL = 0;
	private static final int CARD_MODE_SELECTED = 1;
	private static final int DEFAULT_NOTIFICATION_SECONDS = 2;
	private static final int DEFAULT_EFFECT_WAIT_MS = 250;
	private static final int MAX_SPELL_SUMMON_COUNT = 3;

	/**
	 * SC37 extension:
	 * Immutable AI card-play decision for one card action.
	 */
	private static final class AiCardDecision {
		final int handPosition;
		final Card card;
		final Tile targetTile;
		final BetterUnit targetUnit;
		final int score;

		AiCardDecision(int handPosition, Card card, Tile targetTile, BetterUnit targetUnit, int score) {
			this.handPosition = handPosition;
			this.card = card;
			this.targetTile = targetTile;
			this.targetUnit = targetUnit;
			this.score = score;
		}
	}

	/**
	 * Utility class: no instances.
	 */
	private SimpleCardLogic() {}

	/**
	 * SC21: true when a hand card is currently selected for targeting/playing.
	 */
	public static boolean hasSelectedCard(GameState gameState) {
		return gameState.selectedCardHandPosition != null;
	}

	/**
	 * SC21/SC12:
	 * Clears selected-card state and redraws hand to non-highlight mode.
	 */
	public static void clearCardSelectionAndHandHighlight(ActorRef out, GameState gameState) {
		if (gameState.selectedCardHandPosition == null && gameState.selectedCardTargetMode == null) {
			return;
		}
		gameState.selectedCardHandPosition = null;
		gameState.selectedCardTargetMode = null;
		redrawHumanHand(out, gameState, null);
	}

	/**
	 * SC21 + SC22:
	 * Handles card click, highlights selected card, validates mana, and highlights valid targets.
	 */
	public static void handleCardClicked(ActorRef out, GameState gameState, int handPosition) {
		Card card = getCardByHandPosition(gameState.humanPlayer, handPosition);
		if (card == null) {
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		// Toggle off when user clicks the currently selected card again.
		if (gameState.selectedCardHandPosition != null && gameState.selectedCardHandPosition == handPosition) {
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		// SC22: deny selection/play flow when mana is insufficient.
		if (gameState.humanPlayer.getMana() < card.getManacost()) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		String targetMode = resolveTargetMode(card);
		if (TARGET_NONE.equals(targetMode)) {
			BasicCommands.addPlayer1Notification(out, "Card not in Sprint 4 scope", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		gameState.selectedCardHandPosition = handPosition;
		gameState.selectedCardTargetMode = targetMode;
		redrawHumanHand(out, gameState, handPosition);

		SimpleBoardLogic.HighlightPlan plan = buildTargetHighlightPlan(gameState, card, targetMode);
		SimpleBoardLogic.applyHighlightPlan(out, gameState, plan);

		if (plan.moveTileKeys.isEmpty() && plan.attackTileKeys.isEmpty()) {
			BasicCommands.addPlayer1Notification(out, "No valid targets", DEFAULT_NOTIFICATION_SECONDS);
			clearCardSelectionAndHandHighlight(out, gameState);
		}
	}

	/**
	 * SC23-SC29:
	 * Resolves tile click when a card is selected. Returns true if click was consumed by card flow.
	 */
	public static boolean resolveSelectedCardOnTile(
			ActorRef out,
			GameState gameState,
			Tile clickedTile,
			BetterUnit clickedUnit) {
		if (!hasSelectedCard(gameState)) {
			return false;
		}

		Card selectedCard = getCardByHandPosition(gameState.humanPlayer, gameState.selectedCardHandPosition);
		if (selectedCard == null) {
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		if (gameState.humanPlayer.getMana() < selectedCard.getManacost()) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		if (!isValidTargetSelection(gameState, clickedTile, clickedUnit)) {
			// SC12 behavior for card flow: clear on unrelated tile click.
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		// 2024 GameRules card-order:
		// 1) spend mana, 2) apply card effect, 3) remove card from hand.
		int manaBefore = gameState.humanPlayer.getMana();
		int manaAfter = manaBefore - selectedCard.getManacost();
		gameState.humanPlayer.setMana(manaAfter);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

		boolean effectApplied = executeSelectedCardEffect(out, gameState, selectedCard, clickedTile, clickedUnit);
		if (!effectApplied) {
			// Defensive rollback: if effect cannot resolve, restore spent mana.
			gameState.humanPlayer.setMana(manaBefore);
			BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
			BasicCommands.addPlayer1Notification(out, "Action denied", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		discardCardAndReorderHand(out, gameState.humanPlayer, gameState.selectedCardHandPosition, true);
		SimpleBoardLogic.clearHighlights(out, gameState);
		gameState.selectedCardHandPosition = null;
		gameState.selectedCardTargetMode = null;
		return true;
	}

	/**
	 * SC37 extension:
	 * AI attempts to play at most one legal card during its turn.
	 * Returns true only when a card was successfully played.
	 */
	public static boolean aiTryPlayOneCard(ActorRef out, GameState gameState) {
		if (gameState == null || gameState.aiPlayer == null || gameState.gameOver) {
			return false;
		}

		AiCardDecision decision = chooseBestAiCardDecision(gameState);
		if (decision == null) {
			return false;
		}

		int manaBefore = gameState.aiPlayer.getMana();
		int manaAfter = manaBefore - decision.card.getManacost();
		if (manaAfter < 0) {
			return false;
		}
		gameState.aiPlayer.setMana(manaAfter);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);

		boolean effectApplied = executeCardEffectForOwner(
				out,
				gameState,
				decision.card,
				decision.targetTile,
				decision.targetUnit,
				GameState.OWNER_AI);
		if (!effectApplied) {
			gameState.aiPlayer.setMana(manaBefore);
			BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);
			return false;
		}

		discardCardAndReorderHand(out, gameState.aiPlayer, decision.handPosition, false);
		return true;
	}

	/**
	 * SC37 extension:
	 * Picks the highest-scoring legal card decision from the AI hand under current mana.
	 */
	private static AiCardDecision chooseBestAiCardDecision(GameState gameState) {
		int aiMana = gameState.aiPlayer.getMana();
		AiCardDecision best = null;
		List<Card> hand = gameState.aiPlayer.getHand();

		for (int i = 0; i < hand.size(); i++) {
			Card card = hand.get(i);
			if (card == null || card.getManacost() > aiMana) {
				continue;
			}
			AiCardDecision candidate = buildAiDecisionForCard(gameState, card, i + 1);
			if (candidate == null) {
				continue;
			}
			if (best == null
					|| candidate.score > best.score
					|| (candidate.score == best.score && candidate.handPosition < best.handPosition)) {
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * SC37 extension:
	 * Builds a concrete legal target selection and heuristic score for one AI card.
	 */
	private static AiCardDecision buildAiDecisionForCard(GameState gameState, Card card, int handPosition) {
		if (card == null) {
			return null;
		}
		String mode = resolveTargetMode(card);
		if (TARGET_NONE.equals(mode)) {
			return null;
		}

		String name = normalizeCardName(card);
		int owner = GameState.OWNER_AI;

		if (card.isCreature()) {
			Tile tile = chooseBestSummonTileForOwner(gameState, owner);
			if (tile == null) {
				return null;
			}
			int unitAttack = card.getBigCard() != null ? card.getBigCard().getAttack() : 0;
			int unitHealth = card.getBigCard() != null ? card.getBigCard().getHealth() : 0;
			int score = 140 + card.getManacost() * 10 + unitAttack * 4 + unitHealth;
			return new AiCardDecision(handPosition, card, tile, null, score);
		}

		if ("truestrike".equals(name)) {
			BetterUnit target = chooseBestEnemyTargetForDamage(gameState, owner, 2);
			if (target == null) {
				return null;
			}
			Tile tile = gameState.board[target.getPosition().getTilex()][target.getPosition().getTiley()];
			int score = computeDamageSpellScore(target, 2);
			return new AiCardDecision(handPosition, card, tile, target, score);
		}
		if ("sundrop elixir".equals(name)) {
			BetterUnit target = chooseBestAllyTargetForHeal(gameState, owner);
			if (target == null) {
				return null;
			}
			Tile tile = gameState.board[target.getPosition().getTilex()][target.getPosition().getTiley()];
			int missing = Math.max(0, target.getMaxHealth() - target.getHealth());
			int score = 120 + missing * 10 + (target.isAvatar() ? 20 : 0);
			return new AiCardDecision(handPosition, card, tile, target, score);
		}
		if ("dark terminus".equals(name)) {
			BetterUnit target = chooseBestEnemyTargetForDestroy(gameState, owner);
			if (target == null) {
				return null;
			}
			Tile tile = gameState.board[target.getPosition().getTilex()][target.getPosition().getTiley()];
			int score = 260 + target.getAttack() * 12 + target.getHealth() * 2;
			return new AiCardDecision(handPosition, card, tile, target, score);
		}
		if ("beamshock".equals(name)) {
			BetterUnit target = chooseBestEnemyTargetForStun(gameState, owner);
			if (target == null) {
				return null;
			}
			Tile tile = gameState.board[target.getPosition().getTilex()][target.getPosition().getTiley()];
			int score = 210 + target.getAttack() * 10 + (target.isHasAttacked() ? 0 : 12);
			return new AiCardDecision(handPosition, card, tile, target, score);
		}
		if ("wraithling swarm".equals(name)) {
			Tile tile = chooseBestSummonTileForOwner(gameState, owner);
			if (tile == null) {
				return null;
			}
			return new AiCardDecision(handPosition, card, tile, null, 170);
		}
		if ("horn of the forsaken".equals(name)) {
			// AI artifact tracking is intentionally out of scope for current sprint.
			return null;
		}
		return null;
	}

	/**
	 * SC37 extension:
	 * Picks an unoccupied legal summon tile adjacent to own units that is closest to enemy avatar.
	 */
	private static Tile chooseBestSummonTileForOwner(GameState gameState, int owner) {
		Set<String> legalTiles = SimpleBoardLogic.computeAdjacentUnoccupiedTilesForOwner(gameState, owner);
		if (legalTiles.isEmpty()) {
			return null;
		}
		int enemyOwner = owner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		BetterUnit enemyAvatar = SimpleBoardLogic.getAvatarUnitForOwner(gameState, enemyOwner);

		Tile bestTile = null;
		int bestDistance = Integer.MAX_VALUE;
		int bestY = Integer.MAX_VALUE;
		int bestX = Integer.MAX_VALUE;

		List<String> sorted = sortedTileKeys(legalTiles);
		for (String key : sorted) {
			Tile candidate = SimpleBoardLogic.getTileByKey(gameState, key);
			if (candidate == null) {
				continue;
			}
			int cx = candidate.getTilex();
			int cy = candidate.getTiley();
			int distance = 0;
			if (enemyAvatar != null) {
				distance = Math.abs(cx - enemyAvatar.getPosition().getTilex())
						+ Math.abs(cy - enemyAvatar.getPosition().getTiley());
			}
			if (bestTile == null
					|| distance < bestDistance
					|| (distance == bestDistance && (cy < bestY || (cy == bestY && cx < bestX)))) {
				bestTile = candidate;
				bestDistance = distance;
				bestY = cy;
				bestX = cx;
			}
		}
		return bestTile;
	}

	/**
	 * SC37 extension:
	 * Selects an enemy target for fixed-damage spell use, prioritizing lethal and high-value threats.
	 */
	private static BetterUnit chooseBestEnemyTargetForDamage(GameState gameState, int casterOwner, int damage) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		BetterUnit best = null;
		int bestScore = Integer.MIN_VALUE;
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() != enemyOwner || unit.getHealth() <= 0) {
				continue;
			}
			int score = computeDamageSpellScore(unit, damage);
			if (best == null || score > bestScore || (score == bestScore && unit.getId() < best.getId())) {
				best = unit;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * SC37 extension:
	 * Score function for fixed-damage target selection.
	 */
	private static int computeDamageSpellScore(BetterUnit target, int damage) {
		if (target == null) {
			return Integer.MIN_VALUE;
		}
		if (target.isAvatar()) {
			if (target.getHealth() <= damage) {
				return 10000;
			}
			return 260 + (20 - target.getHealth()) * 6;
		}
		int killBonus = target.getHealth() <= damage ? 450 : 0;
		return 200 + killBonus + target.getAttack() * 9 + (target.getMaxHealth() - target.getHealth());
	}

	/**
	 * SC37 extension:
	 * Selects highest-value enemy non-avatar target for destroy spell.
	 */
	private static BetterUnit chooseBestEnemyTargetForDestroy(GameState gameState, int casterOwner) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		BetterUnit best = null;
		int bestScore = Integer.MIN_VALUE;
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() != enemyOwner || unit.getHealth() <= 0 || unit.isAvatar()) {
				continue;
			}
			int score = unit.getAttack() * 10 + unit.getHealth() * 2 + (unit.isProvoke() ? 15 : 0);
			if (best == null || score > bestScore || (score == bestScore && unit.getId() < best.getId())) {
				best = unit;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * SC37 extension:
	 * Selects highest-impact enemy non-avatar target for stun spell.
	 */
	private static BetterUnit chooseBestEnemyTargetForStun(GameState gameState, int casterOwner) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		BetterUnit best = null;
		int bestScore = Integer.MIN_VALUE;
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() != enemyOwner || unit.getHealth() <= 0 || unit.isAvatar()) {
				continue;
			}
			if (unit.getStunTurnsRemaining() > 0) {
				continue;
			}
			int score = unit.getAttack() * 10 + unit.getHealth() + (unit.isProvoke() ? 20 : 0);
			if (best == null || score > bestScore || (score == bestScore && unit.getId() < best.getId())) {
				best = unit;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * SC37 extension:
	 * Selects allied unit with the greatest missing HP for heal spell.
	 */
	private static BetterUnit chooseBestAllyTargetForHeal(GameState gameState, int owner) {
		BetterUnit best = null;
		int bestScore = Integer.MIN_VALUE;
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() != owner || unit.getHealth() <= 0) {
				continue;
			}
			int missing = unit.getMaxHealth() - unit.getHealth();
			if (missing <= 0) {
				continue;
			}
			int score = missing * 10 + (unit.isAvatar() ? 20 : 0);
			if (best == null || score > bestScore || (score == bestScore && unit.getId() < best.getId())) {
				best = unit;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * SC21 + SC29: redraw full human hand and optionally mark one card selected.
	 */
	private static void redrawHumanHand(ActorRef out, GameState gameState, Integer selectedPosition) {
		for (int i = 1; i <= 6; i++) {
			BasicCommands.deleteCard(out, i);
		}

		List<Card> hand = gameState.humanPlayer.getHand();
		for (int i = 0; i < hand.size() && i < 6; i++) {
			int position = i + 1;
			int mode = (selectedPosition != null && selectedPosition == position) ? CARD_MODE_SELECTED : CARD_MODE_NORMAL;
			BasicCommands.drawCard(out, hand.get(i), position, mode);
		}
	}

	/**
	 * SC22/SC23/SC24/SC25/SC26/SC27/SC28:
	 * Builds tile highlight plan for selected card based on card type and target rules.
	 */
	private static SimpleBoardLogic.HighlightPlan buildTargetHighlightPlan(GameState gameState, Card card, String targetMode) {
		SimpleBoardLogic.HighlightPlan plan = new SimpleBoardLogic.HighlightPlan();
		int owner = GameState.OWNER_HUMAN;

		if (TARGET_SUMMON_TILE.equals(targetMode)) {
			plan.moveTileKeys.addAll(SimpleBoardLogic.computeAdjacentUnoccupiedTilesForOwner(gameState, owner));
			return plan;
		}
		if (TARGET_SELF_AVATAR.equals(targetMode)) {
			BetterUnit avatar = SimpleBoardLogic.getAvatarUnitForOwner(gameState, owner);
			if (avatar != null) {
				plan.moveTileKeys.add(SimpleBoardLogic.tileKey(
						avatar.getPosition().getTilex(),
						avatar.getPosition().getTiley()));
			}
			return plan;
		}

		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getHealth() <= 0) {
				continue;
			}
			String key = SimpleBoardLogic.tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley());

			if (TARGET_ENEMY_UNIT.equals(targetMode) && unit.getOwner() != owner) {
				plan.attackTileKeys.add(key);
			}
			if (TARGET_ALLY_UNIT.equals(targetMode) && unit.getOwner() == owner) {
				plan.moveTileKeys.add(key);
			}
			if (TARGET_ENEMY_NON_AVATAR.equals(targetMode) && unit.getOwner() != owner && !unit.isAvatar()) {
				plan.attackTileKeys.add(key);
			}
		}
		return plan;
	}

	/**
	 * SC23-SC28: validates whether clicked tile/unit matches active card target constraints.
	 */
	private static boolean isValidTargetSelection(GameState gameState, Tile clickedTile, BetterUnit clickedUnit) {
		String key = SimpleBoardLogic.tileKey(clickedTile.getTilex(), clickedTile.getTiley());
		String mode = gameState.selectedCardTargetMode;

		if (TARGET_SUMMON_TILE.equals(mode)) {
			return gameState.moveHighlightTiles.contains(key) && clickedUnit == null;
		}
		if (TARGET_ALLY_UNIT.equals(mode)) {
			return gameState.moveHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_HUMAN;
		}
		if (TARGET_ENEMY_UNIT.equals(mode)) {
			return gameState.attackHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_AI;
		}
		if (TARGET_ENEMY_NON_AVATAR.equals(mode)) {
			return gameState.attackHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_AI
					&& !clickedUnit.isAvatar();
		}
		if (TARGET_SELF_AVATAR.equals(mode)) {
			return gameState.moveHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_HUMAN
					&& clickedUnit.isAvatar();
		}
		return false;
	}

	/**
	 * SC23-SC28:
	 * Executes effect body only. Cost deduction and hand discard are handled by caller.
	 */
	private static boolean executeSelectedCardEffect(
			ActorRef out,
			GameState gameState,
			Card selectedCard,
			Tile clickedTile,
			BetterUnit clickedUnit) {
		return executeCardEffectForOwner(
				out,
				gameState,
				selectedCard,
				clickedTile,
				clickedUnit,
				GameState.OWNER_HUMAN);
	}

	/**
	 * SC23-SC29 + SC37 extension:
	 * Shared effect dispatcher for both human and AI card execution.
	 */
	private static boolean executeCardEffectForOwner(
			ActorRef out,
			GameState gameState,
			Card selectedCard,
			Tile clickedTile,
			BetterUnit clickedUnit,
			int casterOwner) {
		if (selectedCard.isCreature()) {
			return summonCreatureFromCard(out, gameState, selectedCard, clickedTile, casterOwner);
		}

		String name = normalizeCardName(selectedCard);
		if ("truestrike".equals(name)) {
			return castTruestrikeForOwner(out, gameState, clickedTile, clickedUnit, casterOwner);
		}
		if ("sundrop elixir".equals(name)) {
			return castSundropElixirForOwner(out, gameState, clickedTile, clickedUnit, casterOwner);
		}
		if ("dark terminus".equals(name)) {
			return castDarkTerminusForOwner(out, gameState, clickedTile, clickedUnit, casterOwner);
		}
		if ("beamshock".equals(name)) {
			return castBeamshockForOwner(out, gameState, clickedTile, clickedUnit, casterOwner);
		}
		if ("horn of the forsaken".equals(name)) {
			return castHornOfTheForsakenForOwner(out, gameState, clickedTile, clickedUnit, casterOwner);
		}
		if ("wraithling swarm".equals(name)) {
			return castWraithlingSwarmForOwner(out, gameState, clickedTile, casterOwner);
		}
		return false;
	}

	/**
	 * SC23:
	 * Summon creature card onto selected legal tile.
	 */
	private static boolean summonCreatureFromCard(
			ActorRef out,
			GameState gameState,
			Card card,
			Tile targetTile,
			int owner) {
		if (card.getUnitConfig() == null) {
			return false;
		}
		String occupiedKey = SimpleBoardLogic.tileKey(targetTile.getTilex(), targetTile.getTiley());
		if (gameState.unitIdByTile.containsKey(occupiedKey)) {
			return false;
		}

		BetterUnit summoned = (BetterUnit) BasicObjectBuilders.loadUnit(card.getUnitConfig(), gameState.nextUnitId, BetterUnit.class);
		if (summoned == null) {
			return false;
		}
		gameState.nextUnitId++;

		int summonAttack = Math.max(0, card.getBigCard().getAttack());
		int summonHealth = Math.max(1, card.getBigCard().getHealth());

		summoned.setOwner(owner);
		summoned.setAvatar(false);
		summoned.setAttack(summonAttack);
		summoned.setHealth(summonHealth);
		summoned.setMaxHealth(summonHealth);
		summoned.setMoveRange(2);
		summoned.setAttackRange(1);
		applyUnitKeywordAndAbilityFlagsFromCard(summoned, normalizeCardName(card));
		// SC35:
		// default summon sickness blocks move/attack this turn, except Rush units.
		if (summoned.isRush()) {
			summoned.setHasMoved(false);
			summoned.setHasAttacked(false);
		} else {
			summoned.setHasMoved(true);
			summoned.setHasAttacked(true);
		}
		summoned.setStunTurnsRemaining(0);
		summoned.setPositionByTile(targetTile);

		playEffectAndWait(out, StaticConfFiles.f1_summon, targetTile);
		BasicCommands.drawUnit(out, summoned, targetTile);
		BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());
		BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
		SimpleBoardLogic.registerUnit(gameState, summoned, targetTile);
		// SC30:
		// opening-gambit abilities resolve immediately after unit creation.
		triggerOnSummonAbilities(out, gameState, summoned);
		return true;
	}

	/**
	 * SC24:
	 * Truestrike -> deal fixed damage to an enemy unit target.
	 */
	private static boolean castTruestrike(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		return castTruestrikeForOwner(out, gameState, targetTile, target, GameState.OWNER_HUMAN);
	}

	/**
	 * SC25:
	 * Sundrop Elixir -> heal allied target by 5 without exceeding max health.
	 */
	private static boolean castSundropElixir(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		return castSundropElixirForOwner(out, gameState, targetTile, target, GameState.OWNER_HUMAN);
	}

	/**
	 * SC26 + 2025-26 Deck spec:
	 * Dark Terminus -> destroy enemy non-avatar unit, then summon Wraithling on that tile.
	 */
	private static boolean castDarkTerminus(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		return castDarkTerminusForOwner(out, gameState, targetTile, target, GameState.OWNER_HUMAN);
	}

	/**
	 * SC27 + 2025-26 Deck spec:
	 * Beamshock -> stun enemy non-avatar unit for its next turn only.
	 */
	private static boolean castBeamshock(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		return castBeamshockForOwner(out, gameState, targetTile, target, GameState.OWNER_HUMAN);
	}

	/**
	 * SC33 + 2025-26 Deck spec:
	 * Horn of the Forsaken equips artifact charges (3) on the human avatar.
	 */
	private static boolean castHornOfTheForsaken(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		return castHornOfTheForsakenForOwner(out, gameState, targetTile, target, GameState.OWNER_HUMAN);
	}

	/**
	 * SC28 + 2025-26 Deck spec:
	 * Wraithling Swarm -> summon 3 Wraithlings in sequence (up to available legal tiles).
	 */
	private static boolean castWraithlingSwarm(ActorRef out, GameState gameState, Tile selectedTile) {
		return castWraithlingSwarmForOwner(out, gameState, selectedTile, GameState.OWNER_HUMAN);
	}

	/**
	 * Shared spell helper for SC33:
	 * Equip Horn artifact charges on the caster avatar.
	 */
	private static boolean castHornOfTheForsakenForOwner(
			ActorRef out,
			GameState gameState,
			Tile targetTile,
			BetterUnit target,
			int casterOwner) {
		if (target == null || !target.isAvatar() || target.getOwner() != casterOwner) {
			return false;
		}
		// Current runtime only tracks artifact charges for human deck implementation.
		if (casterOwner != GameState.OWNER_HUMAN) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_buff, targetTile);
		gameState.humanHornCharges = 3;
		BasicCommands.addPlayer1Notification(out, "Horn equipped (3)", 2);
		return true;
	}

	/**
	 * Shared spell helper for SC24:
	 * Deal 2 damage to an enemy unit from the specified caster side.
	 */
	private static boolean castTruestrikeForOwner(
			ActorRef out,
			GameState gameState,
			Tile targetTile,
			BetterUnit target,
			int casterOwner) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		if (target == null || target.getOwner() != enemyOwner) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_inmolation, targetTile);
		SimpleBoardLogic.applySpellDamage(out, gameState, target, 2);
		return true;
	}

	/**
	 * Shared spell helper for SC25:
	 * Heal allied unit by 5 without exceeding max health.
	 */
	private static boolean castSundropElixirForOwner(
			ActorRef out,
			GameState gameState,
			Tile targetTile,
			BetterUnit target,
			int casterOwner) {
		if (target == null || target.getOwner() != casterOwner) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_buff, targetTile);
		SimpleBoardLogic.applyHeal(out, gameState, target, 5);
		return true;
	}

	/**
	 * Shared spell helper for SC26:
	 * Destroy enemy non-avatar unit, then summon a Wraithling on its tile.
	 */
	private static boolean castDarkTerminusForOwner(
			ActorRef out,
			GameState gameState,
			Tile targetTile,
			BetterUnit target,
			int casterOwner) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		if (target == null || target.getOwner() != enemyOwner || target.isAvatar()) {
			return false;
		}

		int targetTileX = target.getPosition().getTilex();
		int targetTileY = target.getPosition().getTiley();
		Tile replacementTile = gameState.board[targetTileX][targetTileY];

		playEffectAndWait(out, StaticConfFiles.f1_martyrdom, targetTile);
		SimpleBoardLogic.applySpellDamage(out, gameState, target, target.getHealth());

		if (replacementTile != null) {
			summonWraithling(out, gameState, replacementTile, casterOwner);
		}
		return true;
	}

	/**
	 * Shared spell helper for SC27:
	 * Stun enemy non-avatar for its next turn.
	 */
	private static boolean castBeamshockForOwner(
			ActorRef out,
			GameState gameState,
			Tile targetTile,
			BetterUnit target,
			int casterOwner) {
		int enemyOwner = casterOwner == GameState.OWNER_HUMAN ? GameState.OWNER_AI : GameState.OWNER_HUMAN;
		if (target == null || target.getOwner() != enemyOwner || target.isAvatar()) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_inmolation, targetTile);
		target.setStunTurnsRemaining(1);
		// SC27 UX:
		// notify both "stun applied" and "you got stunned" perspectives for the human player.
		if (casterOwner == GameState.OWNER_HUMAN) {
			BasicCommands.addPlayer1Notification(out, "You applied Stun", 2);
		} else if (target.getOwner() == GameState.OWNER_HUMAN) {
			BasicCommands.addPlayer1Notification(out, "Your unit was stunned", 2);
		}
		return true;
	}

	/**
	 * Shared spell helper for SC28:
	 * Summon 3 Wraithlings in sequence (up to available legal tiles) for caster side.
	 */
	private static boolean castWraithlingSwarmForOwner(
			ActorRef out,
			GameState gameState,
			Tile selectedTile,
			int casterOwner) {
		boolean spawnedAny = summonWraithling(out, gameState, selectedTile, casterOwner);
		int spawned = spawnedAny ? 1 : 0;

		while (spawned < MAX_SPELL_SUMMON_COUNT) {
			List<String> sortedCandidates = sortedTileKeys(SimpleBoardLogic.computeAdjacentUnoccupiedTilesForOwner(gameState, casterOwner));
			if (sortedCandidates.isEmpty()) {
				break;
			}
			Tile nextTile = SimpleBoardLogic.getTileByKey(gameState, sortedCandidates.get(0));
			if (nextTile == null) {
				break;
			}
			if (!summonWraithling(out, gameState, nextTile, casterOwner)) {
				break;
			}
			spawned++;
		}
		return spawnedAny;
	}

	/**
	 * SC28 helper:
	 * Summons one Wraithling token with 1/1 stats.
	 */
	private static boolean summonWraithling(ActorRef out, GameState gameState, Tile tile, int owner) {
		String key = SimpleBoardLogic.tileKey(tile.getTilex(), tile.getTiley());
		if (gameState.unitIdByTile.containsKey(key)) {
			return false;
		}

		BetterUnit wraithling = (BetterUnit) BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, gameState.nextUnitId, BetterUnit.class);
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

		playEffectAndWait(out, StaticConfFiles.f1_summon, tile);
		BasicCommands.drawUnit(out, wraithling, tile);
		BasicCommands.setUnitAttack(out, wraithling, wraithling.getAttack());
		BasicCommands.setUnitHealth(out, wraithling, wraithling.getHealth());
		SimpleBoardLogic.registerUnit(gameState, wraithling, tile);
		return true;
	}

	/**
	 * SC29:
	 * Remove used card and redraw hand positions so indices stay contiguous.
	 */
	private static void discardCardAndReorderHand(ActorRef out, Player player, int handPosition, boolean redrawHumanUi) {
		int index = handPosition - 1;
		if (index < 0 || index >= player.getHand().size()) {
			return;
		}
		player.getHand().remove(index);

		if (!redrawHumanUi) {
			return;
		}

		for (int i = 1; i <= 6; i++) {
			BasicCommands.deleteCard(out, i);
		}
		for (int i = 0; i < player.getHand().size() && i < 6; i++) {
			BasicCommands.drawCard(out, player.getHand().get(i), i + 1, CARD_MODE_NORMAL);
		}
	}

	/**
	 * SC30-SC36:
	 * Maps card identity to runtime keyword/ability flags on summoned units.
	 */
	private static void applyUnitKeywordAndAbilityFlagsFromCard(BetterUnit unit, String normalizedCardName) {
		unit.setProvoke(false);
		unit.setRush(false);
		unit.setFlying(false);
		unit.setOpeningGambitGloomChaser(false);
		unit.setOpeningGambitNightsorrowAssassin(false);
		unit.setOpeningGambitSilverguardSquire(false);
		unit.setDeathwatchBadOmen(false);
		unit.setDeathwatchShadowWatcher(false);
		unit.setDeathwatchBloodmoon(false);
		unit.setDeathwatchShadowdancer(false);
		unit.setZealOnAvatarDamaged(false);
		unit.setOnHitSummonWraithling(false);

		if ("rock pulveriser".equals(normalizedCardName)
				|| "swamp entangler".equals(normalizedCardName)
				|| "ironcliff guardian".equals(normalizedCardName)) {
			unit.setProvoke(true);
		}
		if ("silverguard knight".equals(normalizedCardName)) {
			unit.setProvoke(true);
			unit.setZealOnAvatarDamaged(true);
		}
		if ("saberspine tiger".equals(normalizedCardName)) {
			unit.setRush(true);
		}
		if ("young flamewing".equals(normalizedCardName)) {
			unit.setFlying(true);
		}
		if ("gloom chaser".equals(normalizedCardName)) {
			unit.setOpeningGambitGloomChaser(true);
		}
		if ("nightsorrow assassin".equals(normalizedCardName)) {
			unit.setOpeningGambitNightsorrowAssassin(true);
		}
		if ("silverguard squire".equals(normalizedCardName)) {
			unit.setOpeningGambitSilverguardSquire(true);
		}
		if ("bad omen".equals(normalizedCardName)) {
			unit.setDeathwatchBadOmen(true);
		}
		if ("shadow watcher".equals(normalizedCardName)) {
			unit.setDeathwatchShadowWatcher(true);
		}
		if ("bloodmoon priestess".equals(normalizedCardName)) {
			unit.setDeathwatchBloodmoon(true);
		}
		if ("shadowdancer".equals(normalizedCardName)) {
			unit.setDeathwatchShadowdancer(true);
		}
	}

	/**
	 * SC30:
	 * Execute opening-gambit effects immediately after summon.
	 */
	private static void triggerOnSummonAbilities(ActorRef out, GameState gameState, BetterUnit summoned) {
		if (summoned == null || gameState.gameOver) {
			return;
		}
		if (summoned.isOpeningGambitGloomChaser()) {
			triggerGloomChaserOpeningGambit(out, gameState, summoned);
		}
		if (summoned.isOpeningGambitNightsorrowAssassin()) {
			triggerNightsorrowOpeningGambit(out, gameState, summoned);
		}
		if (summoned.isOpeningGambitSilverguardSquire()) {
			triggerSilverguardSquireOpeningGambit(out, gameState, summoned);
		}
	}

	/**
	 * SC30 (Gloom Chaser):
	 * Summon Wraithling directly behind this unit. Human behind = left, AI behind = right.
	 */
	private static void triggerGloomChaserOpeningGambit(ActorRef out, GameState gameState, BetterUnit summoned) {
		int x = summoned.getPosition().getTilex();
		int y = summoned.getPosition().getTiley();
		int behindX = summoned.getOwner() == GameState.OWNER_HUMAN ? x - 1 : x + 1;
		Tile behindTile = SimpleBoardLogic.getTileByKey(gameState, SimpleBoardLogic.tileKey(behindX, y));
		if (behindTile == null) {
			return;
		}
		String key = SimpleBoardLogic.tileKey(behindTile.getTilex(), behindTile.getTiley());
		if (!gameState.unitIdByTile.containsKey(key)) {
			summonWraithling(out, gameState, behindTile, summoned.getOwner());
		}
	}

	/**
	 * SC30 (Nightsorrow Assassin):
	 * Destroy one adjacent enemy whose current health is below max health.
	 */
	private static void triggerNightsorrowOpeningGambit(ActorRef out, GameState gameState, BetterUnit summoned) {
		List<BetterUnit> candidates = new ArrayList<BetterUnit>();
		int sx = summoned.getPosition().getTilex();
		int sy = summoned.getPosition().getTiley();
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() == summoned.getOwner() || unit.getHealth() <= 0) {
				continue;
			}
			int dx = Math.abs(unit.getPosition().getTilex() - sx);
			int dy = Math.abs(unit.getPosition().getTiley() - sy);
			// 2025-26 Deck spec:
			// Nightsorrow Assassin can only destroy an adjacent DAMAGED non-avatar enemy unit.
			if (dx <= 1
					&& dy <= 1
					&& !(dx == 0 && dy == 0)
					&& !unit.isAvatar()
					&& unit.getHealth() < unit.getMaxHealth()) {
				candidates.add(unit);
			}
		}
		if (candidates.isEmpty()) {
			return;
		}
		Collections.sort(candidates, new Comparator<BetterUnit>() {
			@Override
			public int compare(BetterUnit a, BetterUnit b) {
				return Integer.compare(a.getId(), b.getId());
			}
		});
		BetterUnit target = candidates.get(0);
		Tile targetTile = gameState.board[target.getPosition().getTilex()][target.getPosition().getTiley()];
		playEffectAndWait(out, StaticConfFiles.f1_martyrdom, targetTile);
		SimpleBoardLogic.applySpellDamage(out, gameState, target, target.getHealth());
	}

	/**
	 * SC30 (Silverguard Squire):
	 * Buff allied units directly left/right of own avatar by +1/+1 permanently.
	 */
	private static void triggerSilverguardSquireOpeningGambit(ActorRef out, GameState gameState, BetterUnit summoned) {
		BetterUnit avatar = SimpleBoardLogic.getAvatarUnitForOwner(gameState, summoned.getOwner());
		if (avatar == null) {
			return;
		}
		int ax = avatar.getPosition().getTilex();
		int ay = avatar.getPosition().getTiley();
		int[] candidateXs = new int[] {ax - 1, ax + 1};
		for (int tx : candidateXs) {
			Tile tile = SimpleBoardLogic.getTileByKey(gameState, SimpleBoardLogic.tileKey(tx, ay));
			if (tile == null) {
				continue;
			}
			BetterUnit target = SimpleBoardLogic.getUnitAt(gameState, tx, ay);
			if (target == null || target.getOwner() != summoned.getOwner() || target.isAvatar() || target.getHealth() <= 0) {
				continue;
			}
			target.setAttack(target.getAttack() + 1);
			target.setMaxHealth(target.getMaxHealth() + 1);
			target.setHealth(target.getHealth() + 1);
			BasicCommands.setUnitAttack(out, target, target.getAttack());
			BasicCommands.setUnitHealth(out, target, target.getHealth());
		}
	}

	/**
	 * SC21/SC29 helper:
	 * Safe 1-based hand lookup used by both click flow and AI flow.
	 */
	private static Card getCardByHandPosition(Player player, Integer handPosition) {
		if (player == null || handPosition == null) {
			return null;
		}
		int index = handPosition - 1;
		if (index < 0 || index >= player.getHand().size()) {
			return null;
		}
		return player.getHand().get(index);
	}

	/**
	 * SC22-SC29 helper:
	 * Maps card identity to target-selection mode used by validation and highlighting.
	 */
	private static String resolveTargetMode(Card card) {
		if (card.isCreature()) {
			return TARGET_SUMMON_TILE;
		}
		String name = normalizeCardName(card);
		if ("truestrike".equals(name)) {
			return TARGET_ENEMY_UNIT;
		}
		if ("sundrop elixir".equals(name)) {
			return TARGET_ALLY_UNIT;
		}
		if ("dark terminus".equals(name)) {
			return TARGET_ENEMY_NON_AVATAR;
		}
		if ("beamshock".equals(name)) {
			return TARGET_ENEMY_NON_AVATAR;
		}
		if ("horn of the forsaken".equals(name)) {
			return TARGET_SELF_AVATAR;
		}
		if ("wraithling swarm".equals(name)) {
			return TARGET_SUMMON_TILE;
		}
		return TARGET_NONE;
	}

	/**
	 * Shared helper:
	 * Normalizes card names for stable string matching across loader variants.
	 */
	private static String normalizeCardName(Card card) {
		if (card == null || card.getCardname() == null) {
			return "";
		}
		return card.getCardname().trim().toLowerCase();
	}

	/**
	 * Shared FX helper:
	 * Plays one effect animation and blocks roughly until it is visible.
	 */
	private static void playEffectAndWait(ActorRef out, String effectConfPath, Tile tile) {
		EffectAnimation effect = BasicObjectBuilders.loadEffect(effectConfPath);
		if (effect == null || tile == null) {
			return;
		}
		int estimatedMs = BasicCommands.playEffectAnimation(out, effect, tile);
		int waitMs = estimatedMs > 0 ? estimatedMs : DEFAULT_EFFECT_WAIT_MS;
		try {
			Thread.sleep(waitMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Shared helper:
	 * Sort tile keys deterministically by row then column for reproducible AI choices.
	 */
	private static List<String> sortedTileKeys(Iterable<String> tileKeys) {
		List<String> list = new ArrayList<String>();
		for (String key : tileKeys) {
			list.add(key);
		}
		Collections.sort(list, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				int[] pa = parseTileKey(a);
				int[] pb = parseTileKey(b);
				if (pa[1] != pb[1]) {
					return Integer.compare(pa[1], pb[1]);
				}
				return Integer.compare(pa[0], pb[0]);
			}
		});
		return list;
	}

	/**
	 * Shared helper:
	 * Parses tile key format "x-y"; returns {0,0} on malformed input.
	 */
	private static int[] parseTileKey(String key) {
		String[] split = key.split("-");
		if (split.length != 2) {
			return new int[] {0, 0};
		}
		try {
			return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
		} catch (NumberFormatException e) {
			return new int[] {0, 0};
		}
	}
}
