package tr.ekora.istemci.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/** Oyuncunun Ekora İstemci ayarları (config/ekora-istemci.json). Yalnızca bu bilgisayarda. */
public final class Ayarlar {
	private Ayarlar() { }

	/** Sunucudaki sandık menülerini Ekora görünümüyle çiz. */
	public static boolean sandikGorunumu = true;
	/** Sağ üst köşe bildirimleri. */
	public static boolean bildirimler = true;
	/** Ekora menü sesleri (tık, çark, kasa). */
	public static boolean sesler = true;
	/** Menü açılış animasyonu. */
	public static boolean animasyon = true;
	/** Cam dolgu eşyalarını (boş süs camları) sandık menülerinde gizle. */
	public static boolean camGizle = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path dosya() {
		return FabricLoader.getInstance().getConfigDir().resolve("ekora-istemci.json");
	}

	public static void yukle() {
		try {
			Path p = dosya();
			if (!Files.exists(p)) return;
			JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
			sandikGorunumu = Json.mantik(o, "sandikGorunumu", sandikGorunumu);
			bildirimler = Json.mantik(o, "bildirimler", bildirimler);
			sesler = Json.mantik(o, "sesler", sesler);
			animasyon = Json.mantik(o, "animasyon", animasyon);
			camGizle = Json.mantik(o, "camGizle", camGizle);
		} catch (Exception ignored) { }
	}

	public static void kaydet() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("sandikGorunumu", sandikGorunumu);
			o.addProperty("bildirimler", bildirimler);
			o.addProperty("sesler", sesler);
			o.addProperty("animasyon", animasyon);
			o.addProperty("camGizle", camGizle);
			Files.writeString(dosya(), GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception ignored) { }
	}
}
