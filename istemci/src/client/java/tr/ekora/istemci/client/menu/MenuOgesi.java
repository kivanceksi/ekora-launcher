package tr.ekora.istemci.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import tr.ekora.istemci.client.SandikGorunumu;

/**
 * Bir menü yuvasındaki eşyanın "anlamı": dolgu mu, içerik kartı mı, araç çubuğu düğmesi mi, büyük eylem düğmesi mi;
 * açıklamasından fiyat, kişi, süre, adet gibi alanlar ve "Sol tık: …" ipuçları ayrılır.
 * Bütün eklentilerde aynı kurallar: Türkçe ve İngilizce anahtar kelimeler.
 */
public final class MenuOgesi {
	public enum Tur { DOLGU, ICERIK, ARAC, EYLEM }

	public enum Yon { YOK, ONCEKI, SONRAKI, GERI, KAPAT }

	/** "Anahtar: değer" alanı (fiyat, kişi, süre, adet). */
	public record Alan(String anahtar, String deger) { }

	/** Tıklama ipucu: "Sol tık" → "miktar seç ve al". */
	public record Ipucu(String tus, String eylem) { }

	public final int slot;
	public final ItemStack stack;
	public final Component ad;
	public final String adDuz;
	public final List<Component> bilgi = new ArrayList<>();
	public final List<Alan> fiyatlar = new ArrayList<>();
	public Alan kisi, sure, adet;
	public final List<Alan> digerAlanlar = new ArrayList<>();
	public final List<Ipucu> ipuclari = new ArrayList<>();
	public final String aramaMetni;
	public Tur tur;
	public Yon yon = Yon.YOK;
	public final int renk;

	private static final Locale TR = Locale.forLanguageTag("tr");
	private static final Pattern ALAN = Pattern.compile("^\\s*([^:]{2,28}?)\\s*:\\s*(.+)$");
	private static final Pattern AYRAC = Pattern.compile("^[\\s─━—\\-_=•.·]*$");
	private static final Pattern IPUCU = Pattern.compile("^\\s*[▶►»•]?\\s*((?:shift\\s*\\+\\s*)?(?:sol|sağ|orta|left|right|middle)(?:\\s*tık|\\s*click)?|tıkla|click)\\s*[:\\-]?\\s*(.*)$");

	private MenuOgesi(int slot, ItemStack st, Tur tur, int renk) {
		this.slot = slot;
		this.stack = st;
		this.ad = st.isEmpty() ? Component.empty() : st.getHoverName();
		this.adDuz = ad.getString().replaceAll("[-]", "").trim();
		this.tur = tur;
		this.renk = renk;
		StringBuilder ara = new StringBuilder(adDuz.toLowerCase(TR));
		if (!st.isEmpty() && tur != Tur.DOLGU) {
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore != null) for (Component satir : lore.lines()) ayir(satir, ara);
		}
		this.aramaMetni = ara.toString();
	}

	private void ayir(Component satir, StringBuilder ara) {
		String d = satir.getString().replaceAll("[-]", "").trim();
		if (d.isEmpty() || AYRAC.matcher(d).matches() || d.matches("^#\\d+$")) return;
		ara.append(' ').append(d.toLowerCase(TR));
		String k = d.toLowerCase(TR);
		Matcher ip = IPUCU.matcher(k);
		if (ip.matches()) {
			int i = d.indexOf(':');
			String tus = i > 0 ? d.substring(0, i).replace("▶", "").trim() : "Tıkla";
			String eylem = i > 0 ? d.substring(i + 1).trim() : d.replace("▶", "").replaceFirst("(?i)tıkla\\s*[:\\-]?", "").trim();
			ipuclari.add(new Ipucu(tus, eylem.isEmpty() ? d : eylem));
			return;
		}
		Matcher m = ALAN.matcher(d);
		if (m.matches()) {
			String a = m.group(1).trim(), deger = m.group(2).trim();
			String ak = a.toLowerCase(TR);
			Alan alan = new Alan(a, deger);
			if (ak.matches(".*(fiyat|tutar|ücret|ödeme|bedel|maliyet|satın al|alış|sat$|satış|teklif|eline geçecek|ödül|kazanç|price|cost|buy|sell).*")) { fiyatlar.add(alan); return; }
			if (kisi == null && ak.matches(".*(satıcı|sahib|sahip|oyuncu|veren|alıcı|seller|owner).*")) { kisi = alan; return; }
			if (sure == null && ak.matches(".*(kalan|süre|bitiş|zaman|expires|time).*")) { sure = alan; return; }
			if (adet == null && ak.matches(".*(adet|miktar|stok|teslim|ilerleme|amount|stock|progress).*")) { adet = alan; return; }
			digerAlanlar.add(alan);
		}
		bilgi.add(satir); // tanınmayan "anahtar: değer" satırları da açıklamada (renkleriyle) gösterilir
	}

	public boolean bos() { return tur == Tur.DOLGU; }

	/** Aynı görünen eşya mı (yan yana kopyalanmış onay düğmeleri tek düğme olsun). */
	public boolean ayni(MenuOgesi o) {
		return o != null && o.tur == tur && o.adDuz.equals(adDuz) && o.aramaMetni.equals(aramaMetni)
				&& BuiltInRegistries.ITEM.getKey(o.stack.getItem()).equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public static MenuOgesi analiz(int slot, ItemStack st, int satirSayisi) {
		if (st == null || st.isEmpty() || SandikGorunumu.susCami(st)) return new MenuOgesi(slot, st == null ? ItemStack.EMPTY : st, Tur.DOLGU, 0);
		String yol = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		int adRenk = SandikGorunumu.adRengi(st);
		boolean cam = yol.endsWith("glass_pane");
		// renkli beton/kil ve kalın büyük harfli ad ("OYUNU BAŞLAT", "ONAYLA") → büyük eylem düğmesi
		boolean blokDugme = (yol.endsWith("_concrete") || yol.endsWith("_terracotta") || yol.endsWith("_wool"))
				&& st.getHoverName().getString().equals(st.getHoverName().getString().toUpperCase(TR)) && st.getHoverName().getString().length() > 2;
		MenuOgesi o = new MenuOgesi(slot, st, Tur.ICERIK, cam || blokDugme ? SandikGorunumu.camRengi(st) : adRenk == -1 ? 0xFFE5E7EB : adRenk);
		String k = o.adDuz.toLowerCase(TR);
		if (k.contains("önceki") || k.startsWith("«") || k.contains("previous") || k.equals("◀")) o.yon = Yon.ONCEKI;
		else if (k.contains("sonraki") || k.endsWith("»") || k.contains("next page") || k.equals("▶")) o.yon = Yon.SONRAKI;
		else if (k.contains("kapat") || k.equals("close") || yol.equals("barrier")) o.yon = Yon.KAPAT;
		else if (k.startsWith("geri") || k.contains("« geri") || k.contains("ana menü") || k.contains("menüye dön") || k.equals("back") || k.startsWith("« ")) o.yon = Yon.GERI;
		int satir = slot / 9;
		if (cam || blokDugme) o.tur = Tur.EYLEM;
		else if (o.yon != Yon.YOK || yol.equals("arrow") || yol.equals("spectral_arrow")) o.tur = Tur.ARAC;
		else if (satirSayisi >= 3 && satir == satirSayisi - 1) o.tur = Tur.ARAC;
		return o;
	}
}
