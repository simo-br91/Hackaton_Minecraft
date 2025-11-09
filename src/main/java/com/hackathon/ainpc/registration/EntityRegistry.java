package com.hackathon.ainpc.registration;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AiNpcMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AiNpcMod.MOD_ID);

    public static final RegistryObject<EntityType<ProfessorGEntity>> PROFESSOR_G =
            ENTITY_TYPES.register("professor_g", () -> EntityType.Builder
                    .of(ProfessorGEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(AiNpcMod.MOD_ID + ":professor_g"));

    public static final RegistryObject<Item> PROFESSOR_G_SPAWN_EGG =
            ITEMS.register("professor_g_spawn_egg",
                    () -> new ForgeSpawnEggItem(PROFESSOR_G, 0x00FF00, 0x008000,
                            new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        ITEMS.register(eventBus);
        eventBus.addListener(EntityRegistry::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PROFESSOR_G.get(), ProfessorGEntity.createAttributes().build());
    } // ✅ FIXED: Added closing brace for method
} // ✅ FIXED: Added closing brace for class
