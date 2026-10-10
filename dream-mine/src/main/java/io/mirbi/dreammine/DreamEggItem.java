package io.mirbi.dreammine;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;

/** An egg-shaped mod item, not a globally modified fake-player spawn egg. */
public final class DreamEggItem extends Item {
    public DreamEggItem(Properties props){super(props);}
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if(!(ctx.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        if(!(ctx.getPlayer() instanceof ServerPlayer player))return InteractionResult.FAIL;
        String label=ctx.getItemInHand().has(DataComponents.CUSTOM_NAME)
            ? ctx.getItemInHand().getHoverName().getString() : "Dream";
        var appearance=DreamAppearance.fromEggLabel(label);
        var data=DreamData.get(level.getServer());
        boolean spawned=DreamMine.birth(level,ctx.getClickedPos().relative(ctx.getClickedFace()),
            data,false,appearance.nick(),appearance.account(),player);
        if(!spawned) {
            player.sendSystemMessage(Component.literal(
                "Dream Mine: cannot spawn here. Choose solid ground with two clear blocks.")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if(!player.isCreative())ctx.getItemInHand().shrink(1);
        player.sendSystemMessage(Component.literal(
            "Dream spawned: "+appearance.nick()+" | skin: "+appearance.account()
            +" (set nearest mob with /dreammine name <nick> or /dreammine skin <account>)")
            .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
                                          List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dreammine.egg.unlimited")
          .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dreammine.egg.lag")
          .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dreammine.egg.rename")
          .withStyle(ChatFormatting.GRAY));
    }
}
