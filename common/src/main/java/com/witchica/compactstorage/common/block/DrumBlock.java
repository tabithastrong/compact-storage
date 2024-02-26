package com.witchica.compactstorage.common.block;

import com.mojang.serialization.MapCodec;
import com.witchica.compactstorage.common.block.entity.DrumBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DrumBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing");

    public DrumBlock(Properties settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx).setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(FACING, state.getValue(FACING).getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }


    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options) {
        super.appendHoverText(stack, world, tooltip, options);

        tooltip.add(Component.translatable("text.compact_storage.drum.tooltip_1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("text.compact_storage.drum.tooltip_2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrumBlockEntity(pos, state);
    }

    public void extractItem(Level world, BlockPos pos, Player player) {
        DrumBlockEntity drumBlockEntity = (DrumBlockEntity) world.getBlockEntity(pos);
        SimpleContainer inventory = drumBlockEntity.inventory;

        ItemStack extracted = inventory.removeItemNoUpdate(0);

        if(!extracted.isEmpty()) {
            world.addFreshEntity(new ItemEntity(world, player.getBlockX(), player.getBlockY(), player.getBlockZ(), extracted));
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    public void insertItem(Level world, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        DrumBlockEntity drum = (DrumBlockEntity) world.getBlockEntity(pos);
        SimpleContainer itemHandler = drum.inventory;

        boolean completed = false;

        if(itemInHand.isEmpty() && drum.hasAnyItems()) {
            Container playerInventory = player.getInventory();

            for(int i = 0; i < playerInventory.getContainerSize(); i++) {
                ItemStack itemStack = playerInventory.getItem(i);
                if(itemHandler.canPlaceItem(0, itemStack)) {
                    ItemStack returned = itemHandler.addItem(itemStack);

                    if(itemStack.getCount() != returned.getCount()) {
                        playerInventory.setItem(i, returned);
                        completed = true;
                        break;
                    }
                }
            }
        } else {
            ItemStack itemStack = player.getItemInHand(hand);

            if(itemHandler.canPlaceItem(0, itemStack)) {
                ItemStack returned = itemHandler.addItem(itemStack);

                if(itemStack.getCount() != returned.getCount()) {
                    player.setItemInHand(hand, returned);
                    completed = true;
                }
            }
        }

        if(completed) {
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(!world.isClientSide) {
            if(player.isShiftKeyDown()) {
               extractItem(world, pos, player);
            } else {
                insertItem(world, pos, player, hand);
            }
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        if(!world.isClientSide) {
            extractItem(world, pos, player);
        }

        super.attack(state, world, pos, player);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if(blockEntity instanceof DrumBlockEntity drumBlock) {
            int totalItemCount = drumBlock.getTotalItemCount();
            int stackSize = drumBlock.getStoredType().getMaxStackSize();
            int output = Mth.floor(((totalItemCount / (float) stackSize) / 64f) * 15f);
            return output;
        }

        return 0;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if(blockEntity instanceof DrumBlockEntity drumBlock) {
                Containers.dropContents(world, pos, drumBlock.inventory);
                world.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }
}
