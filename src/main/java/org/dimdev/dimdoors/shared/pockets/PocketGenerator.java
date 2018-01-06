package org.dimdev.dimdoors.shared.pockets;

import org.dimdev.dimdoors.DimDoors;
import org.dimdev.dimdoors.shared.*;
import org.dimdev.dimdoors.shared.world.ModDimensions;

import java.util.Random;

public class PocketGenerator {

    public static Pocket generatePocketFromTemplate(int dim, PocketTemplate pocketTemplate, VirtualLocation virtualLocation) {
        DimDoors.log.info("Generating pocket from template " + pocketTemplate.getName() + " at virtual location " + virtualLocation);

        PocketRegistry registry = PocketRegistry.getForDim(dim);
        Pocket pocket = registry.newPocket();
        pocketTemplate.place(pocket, 0); // TODO: config option for yBase
        pocket.setVirtualLocation(virtualLocation);
        return pocket;
    }

    public static Pocket generatePrivatePocket(VirtualLocation virtualLocation) {
        PocketTemplate pocketTemplate = SchematicHandler.INSTANCE.getPersonalPocketTemplate();
        return generatePocketFromTemplate(ModDimensions.getPrivateDim(), pocketTemplate, virtualLocation);
    }

    public static Pocket generatePublicPocket(VirtualLocation virtualLocation) {
        PocketTemplate pocketTemplate = SchematicHandler.INSTANCE.getPublicPocketTemplate();
        return generatePocketFromTemplate(ModDimensions.getPublicDim(), pocketTemplate, virtualLocation);
    }

    /**
     * Create a dungeon pocket at a certain depth.
     *
     * @param virtualLocation The virtual location of the pocket
     * @return The newly-generated dungeon pocket
     */
    public Pocket generateDungeonPocket(VirtualLocation virtualLocation) {
        int depth = virtualLocation.getDepth();
        float netherProbability = virtualLocation.getDim() == -1 ? 1 : (float) depth / 50; // TODO: improve nether probability
        Random random = new Random();
        String group = random.nextFloat() < netherProbability ? "nether" : "ruins";
        PocketTemplate pocketTemplate = SchematicHandler.INSTANCE.getRandomTemplate(group, depth, Config.getMaxPocketSize(), false);

        return generatePocketFromTemplate(ModDimensions.getDungeonDim(), pocketTemplate, virtualLocation);
    }
}
