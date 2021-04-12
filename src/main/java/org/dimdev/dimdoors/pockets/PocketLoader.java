package org.dimdev.dimdoors.pockets;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.nbt.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.dimdoors.api.util.NbtUtil;
import org.dimdev.dimdoors.api.util.Path;
import org.dimdev.dimdoors.api.util.SimpleTree;
import org.dimdev.dimdoors.pockets.generator.PocketGenerator;
import org.dimdev.dimdoors.pockets.virtual.VirtualPocket;
import org.dimdev.dimdoors.api.util.WeightedList;
import org.dimdev.dimdoors.util.schematic.Schematic;

public class PocketLoader implements SimpleSynchronousResourceReloadListener {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();
	private static final PocketLoader INSTANCE = new PocketLoader();
	private SimpleTree<String, PocketGenerator> pocketGenerators = new SimpleTree<>(String.class);
	private SimpleTree<String, VirtualPocket> pocketGroups = new SimpleTree<>(String.class);
	private SimpleTree<String, VirtualPocket> virtualPockets = new SimpleTree<>(String.class);
	private SimpleTree<String, PocketTemplate> templates = new SimpleTree<>(String.class);
	private SimpleTree<String, NbtElement> dataTree = new SimpleTree<>(String.class);

	private PocketLoader() {
	}

	@Override
	public void reload(ResourceManager manager) {
		pocketGenerators.clear();
		pocketGroups.clear();
		virtualPockets.clear();
		templates.clear();
		dataTree.clear();

		dataTree = loadResourcePathFromJsonToTree(manager, "pockets/json", t -> t).join();

		CompletableFuture<SimpleTree<String, PocketGenerator>> futurePocketGeneratorMap = loadResourcePathFromJsonToTree(manager, "pockets/generators", this::loadPocketGenerator);
		CompletableFuture<SimpleTree<String, VirtualPocket>> futurePocketGroups = loadResourcePathFromJsonToTree(manager, "pockets/groups", this::loadVirtualPocket);
		CompletableFuture<SimpleTree<String, VirtualPocket>> futureVirtualPockets = loadResourcePathFromJsonToTree(manager, "pockets/virtual", this::loadVirtualPocket);
		CompletableFuture<SimpleTree<String, PocketTemplate>> futureTemplates = loadResourcePathFromCompressedNbtToTree(manager, "pockets/schematic", ".schem", this::loadPocketTemplate);


		pocketGenerators = futurePocketGeneratorMap.join();
		pocketGroups = futurePocketGroups.join();
		virtualPockets = futureVirtualPockets.join();
		templates = futureTemplates.join();

		pocketGroups.values().forEach(VirtualPocket::init);
		virtualPockets.values().forEach(VirtualPocket::init);
	}

	private <T> CompletableFuture<SimpleTree<String, T>> loadResourcePathFromJsonToTree(ResourceManager manager, String startingPath, Function<NbtElement, T> reader) {
		int sub = startingPath.endsWith("/") ? 0 : 1;

		Collection<Identifier> ids = manager.findResources(startingPath, str -> str.endsWith(".json"));
		return CompletableFuture.supplyAsync(() -> {
			SimpleTree<String, T> tree = new SimpleTree<>(String.class);
			tree.putAll(ids.parallelStream().unordered().collect(Collectors.toConcurrentMap(
					id -> Path.stringPath(id.getNamespace() + ":" + id.getPath().substring(0, id.getPath().lastIndexOf(".")).substring(startingPath.length() + sub)),
					id -> {
						try {
							JsonElement json = GSON.fromJson(new InputStreamReader(manager.getResource(id).getInputStream()), JsonElement.class);
							return reader.apply(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json));
						} catch (IOException e) {
							throw new RuntimeException("Error loading resource: " + id);
						}
					})));
			return tree;
		});
	}

	private <T> CompletableFuture<SimpleTree<String, T>> loadResourcePathFromCompressedNbtToTree(ResourceManager manager, String startingPath, String extension, BiFunction<NbtCompound, String, T> reader) {
		int sub = startingPath.endsWith("/") ? 0 : 1;
		Function<Identifier, Path<String>> normalizer = id -> Path.stringPath(id.getNamespace() + ":" + id.getPath().substring(0, id.getPath().lastIndexOf(".")).substring(startingPath.length() + sub));
		Collection<Identifier> ids = manager.findResources(startingPath, str -> str.endsWith(extension));
		return CompletableFuture.supplyAsync(() -> {
			SimpleTree<String, T> tree = new SimpleTree<>(String.class);
			tree.putAll(ids.parallelStream().unordered().collect(Collectors.toConcurrentMap(
					normalizer,
					id -> {
						try {
							return reader.apply(NbtIo.readCompressed(manager.getResource(id).getInputStream()), normalizer.apply(id).reduce(String::concat).get());
						} catch (IOException e) {
							throw new RuntimeException("Error loading resource: " + id);
						}
					})));
			return tree;
		});
	}

//    public void load() {
//        long startTime = System.currentTimeMillis();
//
//		try {
//			Path path = Paths.get(SchematicV2Handler.class.getResource("/data/dimdoors/pockets/generators").toURI());
//			loadJson(path, new String[0], this::loadPocketGenerator);
//			LOGGER.info("Loaded pockets in {} seconds", System.currentTimeMillis() - startTime);
//		} catch (URISyntaxException e) {
//			LOGGER.error(e);
//		}
//
//		startTime = System.currentTimeMillis();
//		try {
//			Path path = Paths.get(SchematicV2Handler.class.getResource("/data/dimdoors/pockets/groups").toURI());
//			loadJson(path, new String[0], this::loadPocketGroup);
//			LOGGER.info("Loaded pocket groups in {} seconds", System.currentTimeMillis() - startTime);
//		} catch (URISyntaxException e) {
//			LOGGER.error(e);
//		}
//    }

	public NbtElement getDataTag(String id) {
		return this.dataTree.get(Path.stringPath(id));
	}

	public NbtCompound getDataCompoundTag(String id) {
		return NbtUtil.asCompoundTag(getDataTag(id), "Could not convert Tag \"" + id + "\" to CompoundTag!");
	}

	private VirtualPocket loadVirtualPocket(NbtElement tag) {
		return VirtualPocket.deserialize(tag);
	}

	private PocketGenerator loadPocketGenerator(NbtElement tag) {
		return PocketGenerator.deserialize(NbtUtil.asCompoundTag(tag, "Could not load PocketGenerator since its json does not represent a CompoundTag!"));
	}

	private PocketTemplate loadPocketTemplate(NbtCompound tag, String id) {
		try {
			return new PocketTemplate(Schematic.fromTag(tag), new Identifier(id));
		} catch (Exception e) {
			throw new RuntimeException("Error loading " + tag.toString(), e);
		}
	}

	public WeightedList<PocketGenerator, PocketGenerationContext> getPocketsMatchingTags(List<String> required, List<String> blackList, boolean exact) {
		return new WeightedList<>(pocketGenerators.values().stream().filter(pocketGenerator -> pocketGenerator.checkTags(required, blackList, exact)).collect(Collectors.toList()));
	}

	public VirtualPocket getGroup(Identifier group) {
		return pocketGroups.get(Path.stringPath(group));
	}

	public static PocketLoader getInstance() {
		return INSTANCE;
	}

	public SimpleTree<String, PocketTemplate> getTemplates() {
		return this.templates;
	}

	public SimpleTree<String, VirtualPocket> getPocketGroups() {
		return this.pocketGroups;
	}

	public SimpleTree<String, VirtualPocket> getVirtualPockets() {
		return this.virtualPockets;
	}

	public PocketGenerator getGenerator(Identifier id) {
		return pocketGenerators.get(Path.stringPath(id));
	}

	@Override
	public Identifier getFabricId() {
		return new Identifier("dimdoors", "schematics_v2");
	}
}
