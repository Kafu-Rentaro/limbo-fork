# Limbo Fork Modernization Roadmap

この文書は、Limbo fork を「最新 QEMU + Kotlin + Material 3 Expressive + foldable 対応 + 3D アクセラレーション対応」へ移行するための実装ロードマップです。

## 現状

- Android 側は Java/View/AppCompat/XML layout 中心。
- Gradle は Android Gradle Plugin 4.1.1、compileSdk/targetSdk は 29。
- Native build は `ndk-build`/`Android.mk`/手書き Makefile 中心。
- QEMU は 2.9.1 と 5.1.0 のパッチだけを想定している。
- QEMU configure では `--disable-opengl` が指定されており、3D 表示経路はまだ有効化されていない。
- `jni/qemu`, `jni/glib`, `jni/pixman`, `jni/SDL2`, `jni/libffi` のソースツリーはリポジトリに含まれていない。

## 実装済み

- Gradle wrapper を復元し、Gradle 9.1.0 でビルド可能にした。
- Android Gradle Plugin 8.13.2 / Kotlin 2.3.0 へ更新した。
- compileSdk/targetSdk 36、minSdk 23 に更新した。
- 各アーキテクチャの entry Activity、`ArchDefinitions`、`MachineProperty`、`MachineAction` を Kotlin 化した。
- Material 3 Expressive theme を導入した。
- Jetpack WindowManager と hinge angle sensor による foldable posture 検出を SDL 画面に接続した。
- SDL 表示領域と touch-control 領域を posture/orientation に応じて自動配分する `AdaptiveVmLayout` を追加した。
- F1-F12、矢印、修飾キー、ナビゲーションキーを含む `Full Keyboard (F1-F12)` key mapper preset を追加した。
- virtio GPU / virgl 選択肢と `-display sdl,gl=on` の QEMU 引数生成を追加した。
- `USE_ARMV9=true` と `USE_VIRGL=true` の native build flag を追加した。
- QEMU 11.0.0 用の GPG signature 検証つき取得 script と version config stub を追加した。
- full keyboard preset を操作領域いっぱいに描画し、上下分割/tabletop/左右分割/book posture の比率と連動するようにした。
- Windows 98/Me、2000/XP、7、10、11 + 3D 向けの x86 guest profile 適用 UI を追加した。
- Android debug build/lint を GitHub Actions で実行する CI を追加した。
- Windows 98/Me 向けに `VGA,vgamem_mb=64` の 3D-ready profile を追加し、QEMU 起動時に `-device VGA,vgamem_mb=64` として渡せるようにした。
- QEMU 11 で削除済みの `-soundhw` / `-no-acpi` / `-no-hpet` を避け、`-audio driver=sdl,model=...` と `-machine acpi=off,hpet=off` を生成するようにした。
- QEMU 11.0.0 を Android NDK/LLVM toolchain で configure する `tools/configure-qemu-11-android.sh` を追加した。
- QEMU network runtime options を `-net` から `-netdev ... -device ...` へ移行し、`virtio` NIC を arch/machine に応じた device 名へ解決するようにした。

## 目標バージョン

- QEMU: 11.0.0
  - 2026-04-22 時点の最新 stable release。
  - https://www.qemu.org/2026/04/22/qemu-11-0-0/
  - https://www.qemu.org/download/
- Android UI:
  - Kotlin first。
  - Compose + Material 3 / Material 3 Expressive API へ段階移行。
  - Compose Material 3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Foldables:
  - Jetpack WindowManager の `WindowLayoutInfo` / `FoldingFeature` を基準にする。
  - 公式 API は正確なヒンジ角度を直接公開しないため、角度そのものに依存する挙動は OEM/センサー別の追加実装として扱う。
  - https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware

## フェーズ 0: ビルド基盤の固定

1. Gradle wrapper を復元し、CI/ローカルで同じコマンドを使えるようにする。
2. Android Gradle Plugin、Gradle、JDK、Kotlin plugin の互換表を決める。
3. `jcenter()` を削除し、`google()` と `mavenCentral()` に移行する。
4. compileSdk/targetSdk を最新に上げる。
5. 各 application module と library module を Kotlin 対応にする。
6. Native build の再現手順を script 化する。

完了条件:

- `./gradlew assembleDebug` が通る。
- native source がない状態でも Android 側だけの検査が通る。
- native source がある状態では ABI ごとに QEMU build を実行できる。

## フェーズ 1: Java から Kotlin への段階移行

1. entry Activity から Kotlin 化する。
2. `Config`, `Machine`, `MachineProperty` のような状態モデルを Kotlin data/enum/sealed class に寄せる。
3. `MachineController` と `MachineExecutor` の境界を Kotlin interface に寄せる。
4. JNI に直接触る `VMExecutor` は最後に移行する。
5. SDL Java glue は QEMU/SDL 更新と絡むため、先に無理に変換しない。

完了条件:

- Java と Kotlin が混在しても動作する。
- public API の package/class name は JNI と Manifest 互換を壊さない。
- Activity migration ごとに起動確認できる。

## フェーズ 2: QEMU 11.0.0 への移行

QEMU 5.1.0 から 11.0.0 への直接差し替えは大きな破壊的変更を含むため、旧パッチをそのまま当てる方針は取らない。

実装タスク:

1. QEMU 11.0.0 の tarball を取得し、署名検証できるようにする。
2. Limbo 固有 patch をカテゴリ別に分解する。
   - Android filesystem compatibility
   - Android logging
   - SDL display integration
   - QEMU as shared library
   - JNI control symbols
   - refresh/fullscreen/mouse hooks
3. QEMU 11 の Meson/configure 体系に合わせて Android cross file を作る。
4. 旧 `--audio-drv-list`, `--enable-sdl`, `--disable-opengl` などの configure option を QEMU 11 の option 名に更新する。
5. runtime の `-soundhw`, `-no-acpi`, `-no-hpet` 依存を現行 QEMU の `-audio` / `-machine` 形式へ移行する。
6. `qemu_init`, `qemu_main_loop`, `qemu_cleanup`, shutdown/reset symbol の互換性を確認する。
7. QMP ベースの制御を増やし、dlsym で QEMU 内部変数を書き換える箇所を減らす。

完了条件:

- `x86_64-softmmu` を `arm64-v8a` host 向けに build できる。
- SDL 表示で BIOS 画面まで到達できる。
- shutdown/reset/pause/resume の回帰がない。

## フェーズ 3: ARMv8 / ARMv9 host 対応

Android ABI はどちらも `arm64-v8a` になるため、配布 ABI は同じままにする。ARMv9 専用最適化は runtime feature detection と optional code path で扱う。

実装タスク:

1. `arm64-v8a` baseline build を ARMv8 互換で維持する。
2. ARMv9/SVE/SME などの最適化は Android NDK と端末対応状況を確認し、別 build flavor または runtime dispatch にする。
3. QEMU TCG/KVM で使える host feature をログに出す。

完了条件:

- ARMv8 端末で起動可能。
- ARMv9 端末で追加 feature が検出されても ARMv8 端末を壊さない。

## フェーズ 4: 3D アクセラレーション

現代 OS 向けの第一候補は `virtio-gpu` + virgl/renderer 系。Android host では EGL/GLES と QEMU/renderer の接続が主な難所になる。

実装タスク:

1. QEMU build で OpenGL/GLES と virtio-gpu GL display path を有効化する。
2. Android host 側に EGL context と renderer lifecycle を持たせる。
3. UI に GPU mode を追加する。
   - None
   - virtio-gpu
   - virtio-gpu + 3D
   - legacy VGA
4. guest OS ごとに推奨 GPU/NIC/storage preset を用意する。
5. Windows 10/11、Linux guest から先に検証する。

注意:

- Windows 98/2000 は virtio-gpu 用の実用的な公式 guest driver が期待できない。
- Windows 98 の 3D は、QEMU 11 の virtio-gpu だけでは実現できない可能性が高い。
- Windows 98 で 3D を狙う場合は、BOXV9x/SoftGPU/qemu-3dfx などの guest driver/wrapper と組み合わせる前提で、legacy VGA 64MB profile から検証する。

完了条件:

- Linux guest で OpenGL renderer が動く。
- Windows 10/11 で対応 driver を使った GPU path を検証する。
- Windows 98/2000 は「2D安定」「3D研究中」を明確に分けて扱う。

## フェーズ 5: Material 3 Expressive / Compose UI

実装タスク:

1. XML/View 画面を一括で消さず、Compose screen を新規画面から導入する。
2. Machine list / VM editor / run screen controls を Compose 化する。
3. Material 3 theme を導入する。
4. Material 3 Expressive API は alpha/stable 状況を確認して opt-in を局所化する。
5. SDL surface は Compose の外側または `AndroidView` で保持し、描画 lifecycle を壊さない。

完了条件:

- 既存 VM 作成/編集/起動操作が Compose UI で実行できる。
- Material 3 dynamic color と Expressive components を利用できる。
- 旧 XML 画面は段階的に削除できる。

## フェーズ 6: Foldable / orientation / layout ratio

実装タスク:

1. Jetpack WindowManager を導入する。
2. `FoldingFeature.State.HALF_OPENED` + horizontal fold を tabletop posture として扱う。
3. vertical fold を book posture として扱う。
4. SDL surface と touch keyboard/key mapper の領域配分を posture ごとに切り替える。
5. 物理的な hinge angle が必要な場合は、対応端末別センサー adapter を optional 実装にする。

推奨 layout:

- flat landscape: display 75%、keyboard/controls 25%。
- flat portrait: display 60%、keyboard/controls 40%。
- tabletop: 上側 display、下側 full keyboard/controls。
- book: 左右どちらかを display、反対側を keyboard/controls。

完了条件:

- 回転、画面サイズ変更、fold state 変更で SDL surface と keyboard の比率が自動更新される。
- hinge/occlusion bounds に UI が重ならない。

## フェーズ 7: フルタッチキーボード

実装タスク:

1. 既存 key mapper と別に、標準 full keyboard preset を追加する。
2. F1-F12、Esc、PrintScreen、Pause、Insert/Delete、Home/End、PageUp/PageDown、矢印、Ctrl/Alt/Shift/Win/Menu を含める。
3. compact / full / function-row / game-pad preset を切り替えられるようにする。
4. foldable posture に応じて key size と行数を自動調整する。

完了条件:

- Windows setup、BIOS、DOS、古い Windows、最新 Windows で必要キーが送れる。
- キーラベルが折りたたみ/回転後もはみ出さない。

## フェーズ 8: Guest OS 対応方針

Windows 98/2000 から Windows 11 まで単一 preset で最適化するのは避ける。OS 世代ごとの profile を持つ。

- Windows 98:
  - i440fx/PIIX、IDE、SB16/AC97、Cirrus/std VGA から開始。
  - 3D-ready profile は `-device VGA,vgamem_mb=64` + SDL を使い、guest 側 driver/wrapper の導入を前提にする。
- Windows 2000/XP:
  - IDE/e1000/AC97/VMware SVGA などを検証。
- Windows 7:
  - e1000/virtio-storage 併用を検証。
- Windows 10/11:
  - q35、UEFI、virtio storage/network、virtio-gpu 系を検証。

完了条件:

- OS profile ごとの既知の動作構成を UI から選べる。
- 3D support matrix を README に明記する。

## 最初の実装順

1. Build/Gradle/Kotlin 基盤。
2. QEMU 11.0.0 を `arm64-v8a` host + `x86_64-softmmu` guest で起動。
3. QEMU 11 で SDL 2D 表示を復旧。
4. full keyboard preset。
5. foldable ratio controller。
6. Compose + Material 3 screen migration。
7. virtio-gpu/3D。
8. Windows 98 legacy 3D research。
