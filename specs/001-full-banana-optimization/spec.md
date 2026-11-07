# Feature Specification: Comprehensive BananaNative Optimization Suite (18-Point Overhaul)

## 1. Overview
Complete performance, memory (RAM/VRAM), thermal, and presentation optimization suite for **BananaNative** (`v0.1.5`), targeting **Donkey Kong Bananza** (`040074A01BF12000`) and **Mario Kart World** (`0400C3F00006E000`) across both source code and the pre-built release APK (`banana-native-v0.1.5.apk`).

## 2. The 18 Optimization Requirements

### Category A: Vulkan & CPU Rendering Engine (10 Core Optimizations)
1. **OPT-01: Fine-Grained Barrier Filtering (`vka_exec.c`)**: Coalesce redundant back-to-back full pipeline barriers (`vka_barrier`) across consecutive transfers/clears and narrow barrier stages from `ALL_COMMANDS_BIT` to specific transfer/compute/color stages.
2. **OPT-02: Draw Call & Descriptor Recording Fast-Path (`vka_draw.c`, `vka_bind.c`)**: Enable strict dirty-bit filtering for vertex buffers (`g_vb_filter`), dynamic rendering state (`g_dyn_filter`), and descriptor set bindings so unchanged bindings across consecutive draw calls are skipped.
3. **OPT-03: Placed Vulkan Memory Pool Recycling (`vka_mem.c`)**: Cache and recycle recently released `VkDeviceMemory` segments instead of immediately calling `vkFreeMemory` + `mremap` during level transitions, avoiding 0.7 ms/MiB stalls.
4. **OPT-04: Asynchronous Pipeline Compilation & Prewarm Tuning (`vka_pso.c`)**: Increase background PSO worker concurrency and prevent main replay thread stalls during non-critical shader compilation.
5. **OPT-05: Dynamic Resolution & Per-Segment GPU Timestamps (`hle_nvn.c`, `GameSettings.java`)**: Default `DM_NVN_GPU_TS=segment` and `DM_WINDOW_SIZE=1280x720` (with 1080p option) so `MaintainGpuPercentage` scales internal scene resolution accurately.
6. **OPT-06: NDSR Upscaler Bypass / Fast-Path & FSR 1 Integration (`vka_present.c`, `Fsr.java`)**: Optimize presentation scaling and allow lightweight hardware linear blit or tuned FSR 1 (`DM_VK_FSR_SHARP=0.25`).
7. **OPT-07: Thermal Throttling & GPU Clock Floor Control (`vka_perf.c`)**: Tune KGSL power constraint and ADPF floor (`DM_VK_FLOOR=100`) to prevent Adreno GPU frequency collapse from 1200 MHz down to 282 MHz.
8. **OPT-08: LSFG Motion Flow Optimization (`vka_framegen.c`, `Lsfg.java`)**: Default `DM_LSFG_FLOW=50` (50% optical flow scale) and 30->60 FPS pacing to cut frame generation GPU/VRAM overhead by 60%.
9. **OPT-09: Direct Guest Memory Handle Reads (`vka_draw.c`)**: Bypass slow `process_vm_readv` syscalls on deferred texture handle reads (`OP_BIND_TEX`) whenever the address resides within known readable guest heap/pool mappings.
10. **OPT-10: Turnip / Adreno Driver Memoization & Fast Lookup (`vka_driver.c`)**: Pre-memoize format properties (`vkGetPhysicalDeviceFormatProperties`) instead of querying the Vulkan driver on every `present_frame` and texture upload.

### Category B: RAM & Memory Footprint Reduction (5 Core Optimizations)
11. **OPT-11: Render Target Footprint Reduction (`loader.c`, `Prefs.java`)**: Lower default window target from `2880x1620` (~488 MB VRAM) to `1280x720` (~180 MB VRAM), saving >300 MB of uncommitted/resident GPU memory.
12. **OPT-12: LSFG Intermediate Image Pool Trimming (`vka_framegen.c`)**: Allocate LSFG intermediate textures lazily and release them immediately when LSFG is toggled off.
13. **OPT-13: Linux Page Cache Eviction for RomFS Reads (`hle_fs.c`, `plat_android.c`)**: Issue `posix_fadvise(fd, 0, 0, POSIX_FADV_DONTNEED)` after large sequential RomFS/pack reads and `madvise(..., MADV_DONTNEED)` on freed guest heap blocks so Android's LMK does not count cached file pages against App RSS.
14. **OPT-14: Guest Application Pool & Reservation Right-Sizing (`game.tsv`, `hle_svc.c`)**: Trim `app_pool` and `mem_reserve` overhead to reduce virtual page table bloat on 8 GB / 12 GB Android devices.
15. **OPT-15: Immediate Pool Garbage Collection on Scene Unload (`vka_mem.c`)**: Reclaim unreferenced anonymous pages with `MADV_DONTNEED` immediately upon `mtl_pool_del`.

### Category C: Display Presentation & Screen Stretch (3 Core Optimizations)
16. **OPT-16: Full-Screen Stretch Mode (`vka_present.c`, `SidePanel.java`, `Prefs.java`)**: Add `DM_STRETCH=1` support in C (`vka_present.c`) and an interactive "Stretch to fill screen" toggle in the in-game `SidePanel` and settings, overriding 16:9 letterboxing to fill the entire display (`dw = ew`, `dh = eh`, `dx = 0`, `dy = 0`).
17. **OPT-17: Letterbox Black-Bar Clear Elimination (`vka_present.c`)**: Skip `vkCmdClearColorImage` and its associated barrier whenever `dw == ew && dh == eh` (stretch mode or native panel match).
18. **OPT-18: Viewport & Scissor Redundant Command Elimination (`vka_draw.c`, `vka_present.c`)**: Track active `VkViewport` and `VkRect2D` scissor per command buffer and only emit `vkCmdSetViewport` / `vkCmdSetScissor` when dimensions actually change.
