package structures.basic;

/**
 * A basic representation of of the Player. A player
 * has health and mana.
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Player {

	int health;
	int mana;
	// SC05: Add deck and hand to Player
	java.util.List<Card> deck;
	java.util.List<Card> hand;
	
	public Player() {
		super();
		this.health = 20;
		this.mana = 0;
		// Initialize lists
		this.deck = new java.util.ArrayList<>();
		this.hand = new java.util.ArrayList<>();
	}
	public Player(int health, int mana) {
		super();
		this.health = health;
		this.mana = mana;
		// Initialize lists
		this.deck = new java.util.ArrayList<>();
		this.hand = new java.util.ArrayList<>();
	}
	public int getHealth() {
		return health;
	}
	public void setHealth(int health) {
		this.health = health;
	}
	public int getMana() {
		return mana;
	}
	public void setMana(int mana) {
		this.mana = mana;
	}
	
	// Methods for Deck and Hand management
	public java.util.List<Card> getDeck() {
		return deck;
	}
	public void setDeck(java.util.List<Card> deck) {
		this.deck = deck;
	}
	public java.util.List<Card> getHand() {
		return hand;
	}
	public void setHand(java.util.List<Card> hand) {
		this.hand = hand;
	}
	public void addCardToHand(Card card) {
		this.hand.add(card);
	}
	public void addCardToDeck(Card card) {
		this.deck.add(card);
	}
	public void removeCardFromDeck(Card card) {
		this.deck.remove(card);
	}
	public void removeCardFromHand(Card card) {
		this.hand.remove(card);
	}
	
	
	
}
