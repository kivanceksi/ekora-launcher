package tr.ekora.istemci.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import tr.ekora.istemci.client.SandikGorunumu;

/** Ekora görünümü: süs camlarını çizme/ipucu gösterme, başlığı Ekora üslubunda yaz. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Shadow protected Slot hoveredSlot;
	@Shadow protected int imageWidth;
	@Shadow protected int inventoryLabelX;
	@Shadow protected int inventoryLabelY;
	@Shadow @Final protected Component playerInventoryTitle;

	private AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	private boolean ekora$bizim() {
		return SandikGorunumu.aktif() && ((Object) this) instanceof ContainerScreen;
	}

	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
	private void ekora$yuva(GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
		if (ekora$bizim() && SandikGorunumu.susCami(slot.getItem())) ci.cancel();
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void ekora$ipucu(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
		if (ekora$bizim() && this.hoveredSlot != null && SandikGorunumu.susCami(this.hoveredSlot.getItem())) ci.cancel();
	}

	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void ekora$basliklar(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
		if (!ekora$bizim()) return;
		SandikGorunumu.basliklar(g, this.font, this.title, this.playerInventoryTitle, this.imageWidth, this.inventoryLabelX, this.inventoryLabelY);
		ci.cancel();
	}
}
