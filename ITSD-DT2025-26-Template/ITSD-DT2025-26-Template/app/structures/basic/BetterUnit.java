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
}
