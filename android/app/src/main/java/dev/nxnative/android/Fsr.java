package dev.nxnative.android;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * FSR 1 upscaling at the present (DM_VK_FSR, DM_VK_FSR_SHARP: dm/gfx/vk_android/vka_present.c), set from the in-game panel. The choice is kept for the next starts (env)
 * and sent to the running game as lines of its live control file (DM_CTL, android/TESTING.md "Live control"), which the host reads within 100 ms and applies at the next
 * frame; with DM_CTL= in env.txt (no reader) it takes effect at the next start
 */
final class Fsr {
    private Fsr() {}

    /** RCAS sharpness in stops: 0 is the strongest, each stop halves it (AMD's default 0.25) */
    static final float[] SHARPS = {0f, 0.25f, 0.5f, 1f};

    static boolean on(Context c) { return Prefs.of(c).getBoolean("fsr", false); }
    static float sharp(Context c) { return Prefs.of(c).getFloat("fsr_sharp", 0.25f); }

    /** The host's switches for a run (env.txt lines come after these and win) */
    static void env(Context c, List<String> env) {
        env.add("DM_VK_FSR=" + (on(c) ? 1 : 0));
        env.add("DM_VK_FSR_SHARP=" + sharp(c));
        env.add("DM_VK_BAR_KEEP=1");
        env.add("DM_VK_PUSH_DELTA=1");
        env.add("DM_VK_DYN_FILTER=1");
        env.add("DM_VK_VB_FILTER=1");
        env.add("DM_VK_DRV_MEMO=1");
        env.add("DM_VK_TEX_MEMO=1");
        env.add("DM_VK_PLACE_PART_MB=64");
        env.add("DM_VK_PLACE_GRANULE_MB=16");
        env.add("DM_VK_PLACE_LAZY=1");
        env.add("DM_VK_PSO_ASYNC=2");
        env.add("DM_VK_PREWARM=4");
        env.add("DM_NVN_GPU_TS=segment");
        env.add("DM_VK_GPU_FLOOR=100");
        env.add("DM_VK_GPU_FLOOR_KGSL=1");
        env.add("DM_VK_GPU_FLOOR_ADPF=1");
        env.add("DM_VK_ENC_BATCH=16384");
        env.add("DM_VK_UPLOAD_MT=4");
        env.add("DM_VK_SPARSE_DENSE_MB=64");
        env.add("DM_VK_SPARSE_RES_MB=16");
        env.add("DM_VK_ZSHARE=1");
        env.add("DM_VK_ZSHARE_MB=1");
        env.add("DM_VK_IMGMEM=1");
        env.add("DM_VK_UPLOAD_BATCH_MB=32");
        env.add("DM_VK_UPLOAD_INFLIGHT_MB=128");
        env.add("DM_NUP_CACHE_MB=16");
        env.add("DM_LONGFRAME=0");
        env.add("DM_LONGFRAME_THREADS=0");
        env.add("DM_VK_STATS=0");
        if (Prefs.of(c).getBoolean("fp16", true)) {
            env.add("TU_DEBUG=fp16");
            env.add("FD_MESA_DEBUG=fp16");
            env.add("DM_VK_FP16=1");
        }
        env.add("DM_VK_DOUBLE_BUFFER=" + (Prefs.of(c).getBoolean("double_buf", true) ? 1 : 0));
    }

    private static void push(Context c) {
        File ext = c.getExternalFilesDir(null);   // GameActivity's DM_CTL: <app files>/ctl.txt
        if (ext == null) return;
        try (FileWriter w = new FileWriter(new File(ext, "ctl.txt"), true)) {
            w.write("env DM_VK_FSR=" + (on(c) ? 1 : 0) + "\nenv DM_VK_FSR_SHARP=" + sharp(c) + "\n");
        } catch (IOException e) { Native.note("[RED] FSR: the setting could not be sent to the game (" + e.getMessage() + "); it applies at the next start"); }
    }

    /** The in-game panel's rows: on / off and the sharpness, each applied at once */
    static void rows(Context c, LinearLayout box) {
        TextView head = new TextView(c);
        head.setText("Upscaling");
        head.setPadding(0, 24, 0, 0);
        box.addView(head);
        CheckBox on = new CheckBox(c);
        on.setText("FSR 1 (sharper when the picture is scaled up to the screen)");
        on.setChecked(on(c));
        on.setOnCheckedChangeListener((b, v) -> { Prefs.of(c).edit().putBoolean("fsr", v).commit(); push(c); });
        box.addView(on);
        TextView l = new TextView(c); l.setText("Sharpness"); box.addView(l);
        RadioGroup g = new RadioGroup(c);
        g.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Max", "Default", "Soft", "Softer"};
        for (int i = 0; i < SHARPS.length; i++) {
            RadioButton b = new RadioButton(c);
            b.setText(labels[i]);
            b.setId(3000 + i);
            g.addView(b);
            if (SHARPS[i] == sharp(c)) b.setChecked(true);
        }
        g.setOnCheckedChangeListener((gr, id) -> { Prefs.of(c).edit().putFloat("fsr_sharp", SHARPS[id - 3000]).commit(); push(c); });
        box.addView(g);
        CheckBox fp16 = new CheckBox(c);
        fp16.setText("FP16 Fast Math (2x shader ALU speed & lower RAM register use)");
        fp16.setChecked(Prefs.of(c).getBoolean("fp16", true));
        fp16.setOnCheckedChangeListener((b, v) -> Prefs.of(c).edit().putBoolean("fp16", v).commit());
        box.addView(fp16);
        CheckBox dbuf = new CheckBox(c);
        dbuf.setText("Double-Buffering Swapchain (saves VRAM & reduces input lag)");
        dbuf.setChecked(Prefs.of(c).getBoolean("double_buf", true));
        dbuf.setOnCheckedChangeListener((b, v) -> Prefs.of(c).edit().putBoolean("double_buf", v).commit());
        box.addView(dbuf);
    }
}
