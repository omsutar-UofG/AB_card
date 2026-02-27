package structures.basic;

import java.util.Set;

public class BetterUnit extends Unit {

	// Optional keyword container for future story cards (e.g. provoke/rush/flying).
	Set<String> keywords;

	// Owner side for turn and target validation.
	int owner;
	// Runtime combat values used by SC14/SC15.
	int attack = 2;
	int health = 20;
	// SC25: cap healing to this value (for non-avatar units this is their summon/base health).
	int maxHealth = 20;
	// Runtime action restrictions for SC15.
	boolean hasMoved = false;
	boolean hasAttacked = false;
	// SC27: number of upcoming owner turns this unit is stunned for.
	int stunTurnsRemaining = 0;
	// Basic interaction ranges for SC10/SC11/SC13/SC14.
	int moveRange = 2;
	int attackRange = 1;
	// Avatar flag for player-health synchronization rules.
	boolean avatar = false;

	// SC34/SC35/SC36 keyword flags.
	boolean provoke = false;
	boolean rush = false;
	boolean flying = false;

	// SC30/SC31/SC32/SC33 ability flags (deck-specific runtime mapping).
	boolean openingGambitGloomChaser = false;
	boolean openingGambitNightsorrowAssassin = false;
	boolean openingGambitSilverguardSquire = false;
	boolean deathwatchBadOmen = false;
	boolean deathwatchShadowWatcher = false;
	boolean deathwatchBloodmoon = false;
	boolean deathwatchShadowdancer = false;
	boolean zealOnAvatarDamaged = false;
	boolean onHitSummonWraithling = false;

	public BetterUnit() {}
	
	public BetterUnit(Set<String> keywords) {
		super();
		this.keywords = keywords;
	}

	public Set<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(Set<String> keywords) {
		this.keywords = keywords;
	};

	public int getOwner() {
		return owner;
	}

	public void setOwner(int owner) {
		this.owner = owner;
	}

	public int getAttack() {
		return attack;
	}

	public void setAttack(int attack) {
		this.attack = attack;
	}

	public int getHealth() {
		return health;
	}

	public void setHealth(int health) {
		this.health = health;
	}

	public int getMaxHealth() {
		return maxHealth;
	}

	public void setMaxHealth(int maxHealth) {
		this.maxHealth = maxHealth;
	}

	public boolean isHasMoved() {
		return hasMoved;
	}

	public void setHasMoved(boolean hasMoved) {
		this.hasMoved = hasMoved;
	}

	public boolean isHasAttacked() {
		return hasAttacked;
	}

	public void setHasAttacked(boolean hasAttacked) {
		this.hasAttacked = hasAttacked;
	}

	public int getStunTurnsRemaining() {
		return stunTurnsRemaining;
	}

	public void setStunTurnsRemaining(int stunTurnsRemaining) {
		this.stunTurnsRemaining = stunTurnsRemaining;
	}

	public int getMoveRange() {
		return moveRange;
	}

	public void setMoveRange(int moveRange) {
		this.moveRange = moveRange;
	}

	public int getAttackRange() {
		return attackRange;
	}

	public void setAttackRange(int attackRange) {
		this.attackRange = attackRange;
	}

	public boolean isAvatar() {
		return avatar;
	}

	public void setAvatar(boolean avatar) {
		this.avatar = avatar;
	}

	/**
	 * SC34 keyword flag: Provoke.
	 */
	public boolean isProvoke() {
		return provoke;
	}

	public void setProvoke(boolean provoke) {
		this.provoke = provoke;
	}

	/**
	 * SC35 keyword flag: Rush.
	 */
	public boolean isRush() {
		return rush;
	}

	public void setRush(boolean rush) {
		this.rush = rush;
	}

	/**
	 * SC36 keyword flag: Flying.
	 */
	public boolean isFlying() {
		return flying;
	}

	public void setFlying(boolean flying) {
		this.flying = flying;
	}

	/**
	 * SC30 opening-gambit marker for Gloom Chaser.
	 */
	public boolean isOpeningGambitGloomChaser() {
		return openingGambitGloomChaser;
	}

	public void setOpeningGambitGloomChaser(boolean openingGambitGloomChaser) {
		this.openingGambitGloomChaser = openingGambitGloomChaser;
	}

	/**
	 * SC30 opening-gambit marker for Nightsorrow Assassin.
	 */
	public boolean isOpeningGambitNightsorrowAssassin() {
		return openingGambitNightsorrowAssassin;
	}

	public void setOpeningGambitNightsorrowAssassin(boolean openingGambitNightsorrowAssassin) {
		this.openingGambitNightsorrowAssassin = openingGambitNightsorrowAssassin;
	}

	/**
	 * SC30 opening-gambit marker for Silverguard Squire.
	 */
	public boolean isOpeningGambitSilverguardSquire() {
		return openingGambitSilverguardSquire;
	}

	public void setOpeningGambitSilverguardSquire(boolean openingGambitSilverguardSquire) {
		this.openingGambitSilverguardSquire = openingGambitSilverguardSquire;
	}

	/**
	 * SC31 deathwatch marker for Bad Omen.
	 */
	public boolean isDeathwatchBadOmen() {
		return deathwatchBadOmen;
	}

	public void setDeathwatchBadOmen(boolean deathwatchBadOmen) {
		this.deathwatchBadOmen = deathwatchBadOmen;
	}

	/**
	 * SC31 deathwatch marker for Shadow Watcher.
	 */
	public boolean isDeathwatchShadowWatcher() {
		return deathwatchShadowWatcher;
	}

	public void setDeathwatchShadowWatcher(boolean deathwatchShadowWatcher) {
		this.deathwatchShadowWatcher = deathwatchShadowWatcher;
	}

	/**
	 * SC31 deathwatch marker for Bloodmoon Priestess.
	 */
	public boolean isDeathwatchBloodmoon() {
		return deathwatchBloodmoon;
	}

	public void setDeathwatchBloodmoon(boolean deathwatchBloodmoon) {
		this.deathwatchBloodmoon = deathwatchBloodmoon;
	}

	/**
	 * SC31 deathwatch marker for Shadowdancer.
	 */
	public boolean isDeathwatchShadowdancer() {
		return deathwatchShadowdancer;
	}

	public void setDeathwatchShadowdancer(boolean deathwatchShadowdancer) {
		this.deathwatchShadowdancer = deathwatchShadowdancer;
	}

	/**
	 * SC32 damage-trigger marker for Zeal-like effects.
	 */
	public boolean isZealOnAvatarDamaged() {
		return zealOnAvatarDamaged;
	}

	public void setZealOnAvatarDamaged(boolean zealOnAvatarDamaged) {
		this.zealOnAvatarDamaged = zealOnAvatarDamaged;
	}

	/**
	 * SC33 on-hit marker for summon-on-damage effects.
	 */
	public boolean isOnHitSummonWraithling() {
		return onHitSummonWraithling;
	}

	public void setOnHitSummonWraithling(boolean onHitSummonWraithling) {
		this.onHitSummonWraithling = onHitSummonWraithling;
	}
}
