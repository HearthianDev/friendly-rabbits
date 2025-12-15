package net.hearthian.friendlyrabbits.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.rabbit.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(Rabbit.class)
public class RabbitMixin extends Animal {
    @Unique
    private static final Predicate<Entity> AVOID_PLAYERS = entity -> !entity.isDiscrete() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    @Unique
    private static final Codec<EntityReference<@NotNull LivingEntity>> TRUSTED_ID_CODEC = EntityReference.codec();
    @Unique
    private static final Codec<List<EntityReference<@NotNull LivingEntity>>> TRUSTED_ID_LIST_CODEC = TRUSTED_ID_CODEC.listOf();
    @Unique
    private static final EntityDataAccessor<@NotNull Optional<EntityReference<@NotNull LivingEntity>>> DATA_TRUSTED_ID_0 = SynchedEntityData.defineId(RabbitMixin.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    @Unique
    private static final EntityDataAccessor<@NotNull Optional<EntityReference<@NotNull LivingEntity>>> DATA_TRUSTED_ID_1 = SynchedEntityData.defineId(RabbitMixin.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

    protected RabbitMixin(EntityType<? extends @NotNull Animal> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyArg(method = "registerGoals", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V", ordinal = 5), index = 1)
    protected Goal registerGoals(Goal goal) {
        return new AvoidEntityGoal<>(this, Player.class, 8.0f, 2.2, 2.2, livingEntity -> AVOID_PLAYERS.test(livingEntity) && !this.trusts(livingEntity));
    }

    @Inject(at = @At("TAIL"), method = "defineSynchedData")
    public void defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(DATA_TRUSTED_ID_0, Optional.empty());
        builder.define(DATA_TRUSTED_ID_1, Optional.empty());
    }

    @Shadow
    @Override
    public boolean isFood(@NotNull ItemStack itemStack) {
        return false;
    }

    @Shadow
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel serverLevel, @NotNull AgeableMob ageableMob) {
        return null;
    }

    @Inject(at = @At("RETURN"), method = "getBreedOffspring*", cancellable = true)
    protected void getBreedOffspringMixin2(ServerLevel serverLevel, AgeableMob ageableMob, CallbackInfoReturnable<Rabbit> cir) {
        RabbitMixin rabbit = (RabbitMixin)(LivingEntity) cir.getReturnValue();

        if (rabbit != null) {
            ServerPlayer serverPlayer = this.getLoveCause();
            ServerPlayer serverPlayer2 = ((RabbitMixin)ageableMob).getLoveCause();
            if (serverPlayer != null) {
                rabbit.addTrustedEntity(serverPlayer);
            }
            if (serverPlayer2 != null && serverPlayer != serverPlayer2) {
                rabbit.addTrustedEntity(serverPlayer2);
            }
        }

        cir.setReturnValue((Rabbit)(LivingEntity)rabbit);
    }

    protected void onOffspringSpawnedFromEgg(@NotNull Player player, @NotNull Mob mob) {
        ((RabbitMixin)mob).addTrustedEntity(player);
    }

    @Unique
    void addTrustedEntity(LivingEntity livingEntity) {
        this.addTrustedEntity(EntityReference.of(livingEntity));
    }

    @Unique
    private void addTrustedEntity(EntityReference<@NotNull LivingEntity> entityReference) {
        if ((this.entityData.get(DATA_TRUSTED_ID_0)).isPresent()) {
            this.entityData.set(DATA_TRUSTED_ID_1, Optional.of(entityReference));
        } else {
            this.entityData.set(DATA_TRUSTED_ID_0, Optional.of(entityReference));
        }
    }

    @Unique
    boolean trusts(LivingEntity livingEntity) {
        return this.getTrustedEntities().anyMatch(entityReference -> entityReference.matches(livingEntity));
    }

    @Unique
    Stream<EntityReference<@NotNull LivingEntity>> getTrustedEntities() {
        return Stream.concat(((Optional)this.entityData.get(DATA_TRUSTED_ID_0)).stream(), ((Optional)this.entityData.get(DATA_TRUSTED_ID_1)).stream());
    }

    @Inject(at = @At("HEAD"), method = "addAdditionalSaveData")
    protected void addAdditionalSaveData(ValueOutput valueOutput, CallbackInfo ci) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.store("Trusted", TRUSTED_ID_LIST_CODEC, this.getTrustedEntities().toList());
    }

    @Inject(at = @At("HEAD"), method = "readAdditionalSaveData")
    protected void readAdditionalSaveData(ValueInput valueInput, CallbackInfo ci) {
        super.readAdditionalSaveData(valueInput);
        this.clearTrusted();
        valueInput.read("Trusted", TRUSTED_ID_LIST_CODEC).orElse(List.of()).forEach(this::addTrustedEntity);
    }

    @Unique
    private void clearTrusted() {
        this.entityData.set(DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.set(DATA_TRUSTED_ID_1, Optional.empty());
    }
}