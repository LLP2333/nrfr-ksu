package com.github.nrfr.ksu;

import android.os.IBinder;
import android.os.PersistableBundle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Root-side CLI that overrides carrier config (SIM country ISO / carrier name)
 * through ICarrierConfigLoader, the same binder interface Nrfr uses via Shizuku.
 *
 * Run with: CLASSPATH=nrfr.dex app_process /system/bin com.github.nrfr.ksu.Main <cmd> ...
 */
public final class Main {
    private static final String KEY_ISO = "sim_country_iso_override_string";
    private static final String KEY_NAME_BOOL = "carrier_name_override_bool";
    private static final String KEY_NAME = "carrier_name_string";
    private static final String CALLING_PKG = "com.android.shell";
    private static final int MAX_SLOTS = 4;

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Throwable t) {
            Throwable c = t;
            while (c.getCause() != null && c instanceof java.lang.reflect.InvocationTargetException) {
                c = c.getCause();
            }
            System.err.println("error: " + c);
            System.exit(1);
        }
    }

    private static int run(String[] a) throws Exception {
        if (a.length == 0) return usage();
        switch (a[0]) {
            case "list":
                return list();
            case "set": {
                if (a.length < 3) return usage();
                List<String> rest = new ArrayList<>();
                for (int i = 1; i < a.length; i++) rest.add(a[i]);
                int slot = parseSlot(rest.get(0));
                String iso = rest.get(1).toLowerCase(Locale.ROOT);
                if (!iso.matches("[a-z]{2}")) {
                    System.err.println("error: country code must be 2 letters, got '" + rest.get(1) + "'");
                    return 2;
                }
                String name = rest.size() > 2 ? String.join(" ", rest.subList(2, rest.size())) : null;
                return set(slot, iso, name);
            }
            case "reset":
                if (a.length < 2) return usage();
                if ("all".equals(a[1])) {
                    for (int s = 1; s <= MAX_SLOTS; s++) {
                        int sub = subIdForSlot(s - 1);
                        if (sub > 0) reset(s, sub);
                    }
                    return 0;
                }
                int slot = parseSlot(a[1]);
                int sub = requireSub(slot);
                if (sub < 0) return 3;
                reset(slot, sub);
                return 0;
            case "apply":
                if (a.length < 2) return usage();
                return apply(a[1]);
            case "methods":
                dumpMethods(loader());
                dumpMethods(isub());
                return 0;
            default:
                return usage();
        }
    }

    private static int usage() {
        System.err.println("usage:\n"
                + "  nrfr list\n"
                + "  nrfr set <slot 1..N> <iso> [carrier name...]\n"
                + "  nrfr reset <slot 1..N | all>\n"
                + "  nrfr apply <conf>   (lines: <slot>=<iso>[,<carrier name>])\n"
                + "overrides live in memory only and are gone after a reboot.");
        return 2;
    }

    private static int parseSlot(String s) {
        int slot = Integer.parseInt(s);
        if (slot < 1 || slot > MAX_SLOTS) throw new IllegalArgumentException("slot must be 1.." + MAX_SLOTS);
        return slot;
    }

    private static int requireSub(int slot) throws Exception {
        int sub = subIdForSlot(slot - 1);
        if (sub <= 0) {
            System.err.println("error: no active SIM in slot " + slot);
            return -1;
        }
        return sub;
    }

    // ---- commands -------------------------------------------------------

    private static int list() throws Exception {
        boolean any = false;
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            int sub = subIdForSlot(slot);
            if (sub <= 0) continue;
            any = true;
            PersistableBundle cfg = getConfig(sub);
            String ovIso = cfg == null ? "" : nz(cfg.getString(KEY_ISO));
            String ovName = cfg != null && cfg.getBoolean(KEY_NAME_BOOL, false) ? nz(cfg.getString(KEY_NAME)) : "";
            Object info = subInfo(sub);
            String carrier = info == null ? "" : String.valueOf(call(info, "getCarrierName"));
            String iso = info == null ? "" : nz((String) call(info, "getCountryIso"));
            System.out.println("slot=" + (slot + 1) + "\tsubId=" + sub + "\tcarrier=" + carrier
                    + "\tiso=" + iso + "\toverride_iso=" + ovIso + "\toverride_name=" + ovName);
        }
        if (!any) System.out.println("no active SIM");
        return 0;
    }

    private static int set(int slot, String iso, String name) throws Exception {
        int sub = requireSub(slot);
        if (sub < 0) return 3;
        // overrideConfig merges into existing overrides, so clear first to make "set" idempotent.
        override(sub, null);
        PersistableBundle b = new PersistableBundle();
        b.putString(KEY_ISO, iso);
        if (name != null && !name.isEmpty()) {
            b.putBoolean(KEY_NAME_BOOL, true);
            b.putString(KEY_NAME, name);
        }
        override(sub, b);
        System.out.println("slot " + slot + " (subId " + sub + "): iso=" + iso
                + (name != null ? " name=" + name : ""));
        return 0;
    }

    /** Idempotently brings every active slot in line with the config file. */
    private static int apply(String path) throws Exception {
        String[][] want = new String[MAX_SLOTS][];
        java.io.File f = new java.io.File(path);
        if (f.exists()) {
            for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                int slot;
                try { slot = parseSlot(line.substring(0, eq).trim()); } catch (RuntimeException e) { continue; }
                String val = line.substring(eq + 1).trim();
                int comma = val.indexOf(',');
                String iso = (comma < 0 ? val : val.substring(0, comma)).trim().toLowerCase(Locale.ROOT);
                String name = comma < 0 ? "" : val.substring(comma + 1).trim();
                if (!iso.matches("[a-z]{2}")) {
                    System.err.println("skip slot " + slot + ": bad country code '" + iso + "'");
                    continue;
                }
                want[slot - 1] = new String[]{iso, name};
            }
        }
        int rc = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            int sub = subIdForSlot(i);
            if (sub <= 0) continue;
            PersistableBundle cfg = getConfig(sub);
            String curIso = cfg == null ? "" : nz(cfg.getString(KEY_ISO));
            String curName = cfg != null && cfg.getBoolean(KEY_NAME_BOOL, false) ? nz(cfg.getString(KEY_NAME)) : "";
            Object info = subInfo(sub);
            String effIso = info == null ? "" : nz((String) call(info, "getCountryIso"));
            try {
                if (want[i] != null) {
                    if (want[i][0].equals(curIso) && want[i][1].equals(curName) && want[i][0].equals(effIso)) {
                        System.out.println("slot " + (i + 1) + " (subId " + sub + "): already iso=" + curIso);
                    } else {
                        set(i + 1, want[i][0], want[i][1].isEmpty() ? null : want[i][1]);
                    }
                } else if (!curIso.isEmpty() || !curName.isEmpty()) {
                    reset(i + 1, sub);
                }
            } catch (Exception e) {
                System.err.println("slot " + (i + 1) + ": " + e);
                rc = 1;
            }
        }
        return rc;
    }

    private static void reset(int slot, int sub) throws Exception {
        override(sub, null);
        // Clearing the override does not bring back the SIM's own country ISO (telephony only
        // applies a non-empty override), so briefly override with the ISO derived from the
        // SIM's MCC and clear again.
        String orig = isoFromMcc(sub);
        if (orig.isEmpty()) {
            System.out.println("slot " + slot + " (subId " + sub
                    + "): override cleared; country ISO fully restores after reboot");
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putString(KEY_ISO, orig);
        override(sub, b);
        // Telephony applies the config change asynchronously; wait until it lands before clearing,
        // otherwise it reads the already-cleared config and keeps the old ISO.
        waitForIso(sub, orig, 5000);
        override(sub, null);
        System.out.println("slot " + slot + " (subId " + sub + "): reset, iso=" + orig);
    }

    private static boolean waitForIso(int sub, String iso, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        do {
            Object info = subInfo(sub);
            if (info != null && iso.equals(call(info, "getCountryIso"))) return true;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < end);
        return false;
    }

    private static String isoFromMcc(int sub) {
        try {
            Object info = subInfo(sub);
            if (info == null) return "";
            String mcc = (String) call(info, "getMccString");
            if (mcc == null || mcc.isEmpty()) return "";
            Class<?> t = Class.forName("com.android.internal.telephony.MccTable");
            Object r;
            try {
                r = t.getMethod("countryCodeForMcc", String.class).invoke(null, mcc);
            } catch (NoSuchMethodException e) {
                r = t.getMethod("countryCodeForMcc", int.class).invoke(null, Integer.parseInt(mcc));
            }
            return r == null ? "" : ((String) r).toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    // ---- binder plumbing ------------------------------------------------

    private static IBinder service(String name) throws Exception {
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (b == null) throw new IllegalStateException("service '" + name + "' not found");
        return b;
    }

    private static Object asInterface(String iface, IBinder b) throws Exception {
        return Class.forName(iface + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
    }

    private static Object loader() throws Exception {
        return asInterface("com.android.internal.telephony.ICarrierConfigLoader", service("carrier_config"));
    }

    private static Object isub() throws Exception {
        return asInterface("com.android.internal.telephony.ISub", service("isub"));
    }

    private static int subIdForSlot(int slot) throws Exception {
        Object isub = isub();
        for (Method m : isub.getClass().getMethods()) {
            if (!m.getName().equals("getSubId")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || p[0] != int.class) continue;
            Object r = m.invoke(isub, slot);
            if (r instanceof Integer) return (Integer) r;
            if (r instanceof int[]) return ((int[]) r).length > 0 ? ((int[]) r)[0] : -1;
        }
        throw new NoSuchMethodException("ISub.getSubId(int)");
    }

    private static Object subInfo(int sub) throws Exception {
        return invokeWithSub(isub(), "getActiveSubscriptionInfo", sub);
    }

    private static PersistableBundle getConfig(int sub) throws Exception {
        Object l = loader();
        Object r = invokeWithSub(l, "getConfigForSubIdWithFeature", sub);
        if (r == NOT_FOUND) r = invokeWithSub(l, "getConfigForSubId", sub);
        if (r == NOT_FOUND) throw new NoSuchMethodException("ICarrierConfigLoader.getConfigForSubId*");
        return (PersistableBundle) r;
    }

    /** In-memory override (persistent=false); persistent overrides are reserved for system apps. */
    private static void override(int sub, PersistableBundle b) throws Exception {
        Object l = loader();
        for (Method m : l.getClass().getMethods()) {
            if (!m.getName().equals("overrideConfig")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3) { m.invoke(l, sub, b, false); return; }
            if (p.length == 2) { m.invoke(l, sub, b); return; } // Android <= 10
        }
        throw new NoSuchMethodException("ICarrierConfigLoader.overrideConfig");
    }

    private static final Object NOT_FOUND = new Object();

    /** Calls name(int subId, String pkg, String... nulls) whatever the exact arity is. */
    private static Object invokeWithSub(Object target, String name, int sub) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 0 || p[0] != int.class) continue;
            Object[] args = new Object[p.length];
            args[0] = sub;
            boolean ok = true, pkgSet = false;
            for (int i = 1; i < p.length; i++) {
                if (p[i] != String.class) { ok = false; break; }
                args[i] = pkgSet ? null : CALLING_PKG;
                pkgSet = true;
            }
            if (ok) return m.invoke(target, args);
        }
        return NOT_FOUND;
    }

    private static Object call(Object o, String name) throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static void dumpMethods(Object o) {
        for (Method m : o.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            StringBuilder sb = new StringBuilder(m.getName()).append('(');
            for (Class<?> c : m.getParameterTypes()) sb.append(c.getSimpleName()).append(',');
            System.out.println(sb.append(") -> ").append(m.getReturnType().getSimpleName()));
        }
    }
}
