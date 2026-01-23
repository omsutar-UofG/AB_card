package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * This is a utility class that builds a large set of image URLs
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class ImageListForPreLoad {

	/**
	 * This method returns a very big list of relative locations of the different images
	 * that are needed by the game. Pixi.js will cache these on game load.
	 * @return
	 */
	public static Set<String> getImageListForPreLoad() {
		
		Set<String> images = new HashSet<String>();
		
		// UI Elements
		images.add("assets/game/extra/battlemap6_middleground.png");
		images.add("assets/game/extra/AttackCircle.png");
		images.add("assets/game/extra/HealthCircle.png");
		images.add("assets/game/extra/GreyCircle.png");
		images.add("assets/game/extra/ManaCircle.png");
		images.add("assets/game/extra/ui/icon_mana.png");
		images.add("assets/game/extra/ui/icon_mana_inactive.png");
		images.add("assets/game/extra/ui/notification_quest_small.png");
		images.add("assets/game/extra/ui/general_portrait_image_hex_f1-third@2x.png");
		images.add("assets/game/extra/ui/general_portrait_image_hex_f4-third@2x.png");
		images.add("assets/game/extra/ui/tooltip_left@2x.png");
		images.add("assets/game/extra/ui/tooltip_right@2x.png");
		images.add("assets/game/extra/ui/button_end_turn_enemy.png");
		images.add("assets/game/extra/ui/button_primary.png");
		
		// Tiles
		images.addAll(Tile.constructTile(StaticConfFiles.tileConf).getTileTextures());
		
		// Avatars
		images.addAll(BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, -1, Unit.class).getAnimations().getAllFrames());
		images.addAll(BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, -1, Unit.class).getAnimations().getAllFrames());
		
		String cardsDIR = "conf/gameconfs/cards/";
		for (String filename : new File(cardsDIR).list()) {
			images.addAll(getCardImagesForPreload(cardsDIR+filename));
		}
		
		String unitsDIR = "conf/gameconfs/units/";
		for (String filename : new File(unitsDIR).list()) {
			images.addAll(getUnitImagesForPreload(BasicObjectBuilders.loadUnit(unitsDIR+filename, -1, Unit.class)));
		}
		
		String effectsDIR = "conf/gameconfs/effects/";
		for (String filename : new File(effectsDIR).list()) {
			images.addAll(BasicObjectBuilders.loadEffect(effectsDIR+filename).getAnimationTextures());
		}
		
		return images;
	}
	
	public static List<String> getUnitImagesForPreload(Unit unit) {
		List<String> images = unit.getAnimations().getAllFrames();
		return images;
	}
	
	public static List<String> getCardImagesForPreload(String configFile) {
		Card card = BasicObjectBuilders.loadCard(configFile, 0, Card.class);
		List<String> images = new ArrayList<String>(card.getMiniCard().getAnimationFrames().length+card.getMiniCard().getCardTextures().length+card.getBigCard().getCardTextures().length);
		for (String image : card.getMiniCard().getAnimationFrames()) images.add(image);
		for (String image : card.getMiniCard().getCardTextures()) images.add(image);
		for (String image :card.getBigCard().getCardTextures()) images.add(image);
		return images;
	}
	
}
