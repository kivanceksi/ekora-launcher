package tr.ekora.istemci.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Sunucudan gelen veriyi güvenle okumak için küçük yardımcılar (eksik/yanlış alan çökertmez). */
public final class Json {
	private Json() { }

	public static String yazi(JsonObject o, String k, String v) {
		try {
			JsonElement e = o.get(k);
			return e == null || e.isJsonNull() ? v : e.getAsString();
		} catch (RuntimeException ex) {
			return v;
		}
	}

	public static long sayi(JsonObject o, String k, long v) {
		try {
			JsonElement e = o.get(k);
			return e == null || e.isJsonNull() ? v : e.getAsLong();
		} catch (RuntimeException ex) {
			return v;
		}
	}

	public static double ondalik(JsonObject o, String k, double v) {
		try {
			JsonElement e = o.get(k);
			return e == null || e.isJsonNull() ? v : e.getAsDouble();
		} catch (RuntimeException ex) {
			return v;
		}
	}

	public static boolean mantik(JsonObject o, String k, boolean v) {
		try {
			JsonElement e = o.get(k);
			return e == null || e.isJsonNull() ? v : e.getAsBoolean();
		} catch (RuntimeException ex) {
			return v;
		}
	}

	public static List<JsonObject> liste(JsonObject o, String k) {
		List<JsonObject> l = new ArrayList<>();
		try {
			JsonElement e = o.get(k);
			if (e instanceof JsonArray a) for (JsonElement x : a) if (x.isJsonObject()) l.add(x.getAsJsonObject());
		} catch (RuntimeException ignored) { }
		return l;
	}

	public static List<String> yazilar(JsonObject o, String k) {
		List<String> l = new ArrayList<>();
		try {
			JsonElement e = o.get(k);
			if (e instanceof JsonArray a) for (JsonElement x : a) if (x.isJsonPrimitive()) l.add(x.getAsString());
		} catch (RuntimeException ignored) { }
		return l;
	}
}
