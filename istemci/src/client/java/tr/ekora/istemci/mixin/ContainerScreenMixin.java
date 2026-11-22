package tr.ekora.istemci.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

import tr.ekora.istemci.client.SandikGorunumu;

/** Sandık menüsü (9×N): vanilla doku yerine Ekora paneli. Tıklama/yuva davranışı değişmez. */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {
	private ContainerScreenMixin(ChestMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
	}

	@Redirect(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void ekora$doku(GuiGraphicsExtractor g, RenderPipeline p, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th) {
		if (!SandikGorunumu.aktif()) g.blit(p, id, x, y, u, v, w, h, tw, th);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void ekora$panel(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		if (SandikGorunumu.aktif()) SandikGorunumu.arkaPlan(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.menu, this.menu.getRowCount());
	}
}
