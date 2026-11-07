# Technical Implementation Plan: 18-Point BananaNative Optimization

## 1. Architecture & Dual-Target Strategy
Since we have both the full source repository (`scratch/BananaNative`) and the shipped release APK (`banana-native-v0.1.5.apk`), our implementation operates on two synchronized tracks:

### Track 1: Native Source Code Refactoring (`dm/` & `android/`)
1. **`dm/gfx/vk_android/vka_present.c`**:
   - Add `DM_STRETCH` live environment variable check + atomic state `g_stretch` (OPT-16).
   - Skip `vkCmdClearColorImage` when `dw == ew && dh == eh` (OPT-17).
   - Cache `VkFormatProperties` per `im->f.vk` instead of calling `vkGetPhysicalDeviceFormatProperties` every frame (OPT-10).
2. **`dm/gfx/vk_android/vka_exec.c` & `vka_draw.c`**:
   - Coalesce redundant consecutive `vka_barrier(cb)` calls via a `g_cb_bar_clean` dirty flag reset on any draw/dispatch/transfer (OPT-01).
   - Fast-path `OP_BIND_TEX` deferred reads when within guest memory range to avoid `process_vm_readv` syscall overhead (OPT-09).
   - Dirty-track `VkViewport` and `VkRect2D` scissor in `vka_draw.c` (OPT-18) along with `g_dyn_filter` and `g_vb_filter` (OPT-02).
3. **`dm/gfx/vk_android/vka_mem.c` & `dm/host/plat/plat_android.c`**:
   - Apply `madvise(addr, len, MADV_DONTNEED)` on unplaced/returned anonymous pool ranges (OPT-13, OPT-15).
   - Add small segment victim cache before `vkFreeMemory` to prevent level-transition stutter (OPT-03).
4. **`dm/games/040074A01BF12000/game.tsv`**:
   - Right-size `app_pool` and `mem_reserve` for mobile RAM profiles (OPT-14).
5. **`android/app/src/main/java/dev/nxnative/android/` (`SidePanel.java`, `Prefs.java`, `GameSettings.java`, `Lsfg.java`)**:
   - Add live "Stretch to fill screen" checkbox in `SidePanel` writing `env DM_STRETCH=0/1` to `ctl.txt` and persisting in `Prefs` (OPT-16).
   - Default resolution to `1280x720` (`Phone 720p`) when unset (OPT-05, OPT-11).
   - Default `gpu_ts` to `segment` (OPT-05) and `LSFG` flow scale to `50%` (OPT-08, OPT-12).

### Track 2: Binary & Smali Patching of `banana-native-v0.1.5.apk` via Craft Anything Engines
1. **Decompile APK (`apktool`)**:
   - Unpack `banana-native-v0.1.5.apk` using Craft Anything's `apktool.jar`.
2. **Analyze & Patch Native ELF Binaries (`libnxhost_040074A01BF12000.so`, `libnxhost_0400C3F00006E000.so`, `libnxhost_0400A3A01EE4C000.so`)**:
   - Use `Craft Anything` (`Ghidra` / ELF symbol & machine-code inspection) to locate `present_frame` scaling math (`fdiv`/`fcsel` for `ew/cw` vs `eh/ch`) and `vka_barrier` redundancy inside `libnxhost_*.so`.
   - Patch the aspect-ratio scaling instruction sequence or hook `nxhost_picture_place` so that when `g_place_x` / stretch flag is active, `dw = ew` and `dh = eh` natively in the ARM64 binary!
   - Patch embedded `game.tsv` / default constants in `libnxhost_*.so` and `assets/pack/tools.zip` to reduce RAM reservation and enforce optimal GPU floor & segment timing.
3. **Patch Dex / Smali (`classes.dex`)**:
   - Modify `Prefs.smali`, `SidePanel.smali`, `GameActivity.smali`, and `Lsfg.smali` to:
     - Inject all optimal environment variables (`DM_WINDOW_SIZE=1280x720`, `DM_NVN_GPU_TS=segment`, `DM_VK_FLOOR=100`, `DM_LSFG_FLOW=50`, `DM_VK_PASS_KEEP=1`, `DM_VK_DYN_FILTER=1`, `DM_VK_VB_FILTER=1`) by default.
     - Add the "Stretch to fill screen" UI toggle in `SidePanel.smali` and wire it to `Native.picturePlace` / `ctl.txt` stretch mode.
     - Trigger periodic JVM/native memory trim (`System.gc()` / `trimMemory`) on scene transitions.
4. **Rebuild, Align & Sign APK**:
   - Repackage with `apktool b`, align `lib/arm64-v8a/*.so` to 16 KB page alignment (`zipalign`), and sign with `apksigner` / `keytool` into `C:\Users\MOHAMED SADIQ\Downloads\Compressed\banana-native-v0.1.5-ultra-optimized.apk`.
