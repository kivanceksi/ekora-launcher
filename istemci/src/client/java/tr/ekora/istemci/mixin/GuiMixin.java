package tr.ekora.istemci.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import tr.ekora.istemci.client.menu.AkilliMenu;

/** Sunucu "bu bir eklenti menüsü" dediyse vanilla sandık ekranı yerine Akıllı Menü açılır. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void ekora$akilliMenu(Screen screen, CallbackInfo ci) {
		Screen yeni = AkilliMenu.donustur(screen);
		if (yeni != screen) {
			ci.cancel();
			((Gui) (Object) this).setScreen(yeni);
		}
	}
}
