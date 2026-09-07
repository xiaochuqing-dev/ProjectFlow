# V4.0-B 环境素材记录

来源：用户提供的参考图 `reference.jpg`。使用内置 image_gen 生成 3 张独立环境背景，经 Agent 目视检查后转换为 WebP。没有生成整页截图作为界面；所有文字、导航、卡片与交互均由 React 和 CSS 实现。没有调用付费 API fallback、安装生产依赖或引入外部图床。

| 交付素材 | 生成尺寸 | 页面用途 | WebP 大小 |
| --- | --- | --- | --- |
| `frontend/public/workspace/sidebar-mountains.webp` | 1024 × 1536 | 侧栏底部山脉、极光与河流 | 9,042 bytes |
| `frontend/public/workspace/hero-valley.webp` | 1828 × 860 | 当前状态与项目库的主环境层 | 50,370 bytes |
| `frontend/public/workspace/evidence-planet.webp` | 1254 × 1254 | 工程证据面板底部星球 | 6,928 bytes |

单独的 `mark.svg` 是抽象双叶品牌符号，用于浏览器图标。页面徽标由同一色彩和抽象形状的 CSS 组成。素材总计约 65 KiB，全部来自同一参考视觉，随项目本地提供。

## 最终素材提示词

以下是美术素材生成规格，不是 ProjectFlow 业务模型 Prompt 或真实项目内容。

### Sidebar mountain

Use case: stylized-concept; Asset type: website sidebar lower background, displayed about 200 px wide by 320 px tall at the bottom-left; Input images: the provided ProjectFlow dashboard screenshot is a reference image for visual style, atmosphere, palette, and the small landscape vignette only; do not reproduce its UI. Primary request: Create one original, clean portrait background asset, approximately 2:3. A midnight navy to deep black-blue mountain environment with layered distant mountain silhouettes and a cyan-blue aurora sky. A very small luminous cyan river winds near the bottom. A subtle indigo glow comes from the far horizon. The upper half must become very dark and fade naturally to near-black so it can dissolve behind sidebar navigation. Style/medium: refined cinematic digital environment art, restrained premium product UI ambience; no painterly brush texture; clean and atmospheric. Composition/framing: vertical 2:3 frame; mountains concentrated in the lower half; river near bottom; ample dark negative space in upper half; no framing border. Lighting/mood: quiet midnight, cool cyan rim light, subtle indigo haze, high contrast while remaining subdued. Color palette: midnight navy, black-blue, muted indigo, restrained cyan-blue. Constraints: original image only; no text, no letters, no numbers, no logo, no UI elements, no cards, no icons, no people, no buildings, no watermark; do not show a whole dashboard or device; no bright white sky.

### Central hero

Use case: stylized-concept; Asset type: website central hero background, intended for a very wide landscape region approximately 3:1; Input images: the provided ProjectFlow dashboard screenshot is a reference image for visual style, palette, mood, and hero-landscape composition only; do not reproduce its UI. Primary request: Create one original, clean, panoramic central hero environment. A midnight-blue mountainous valley with one tall jagged rocky spire at about 65% across from the left. A narrow luminous cyan S-shaped river or path winds through the mountain valley from the near foreground toward the spire. Along the distant horizon use softly lit, muted peach and blue dusk light. Keep the left half very dark and calm with generous negative space for overlaid text. Style/medium: refined cinematic digital environment art matching a premium dark product UI, crisp but atmospheric, controlled realism. Composition/framing: ultra-wide panoramic landscape approximately 3:1; a dark text-safe left half; tall rocky spire near right 65%; the cyan river makes a clear S curve through the middle/right valley; no cropped peak; no border. Lighting/mood: quiet indigo-blue dusk, restrained cyan glow along the river, soft muted peach at the horizon, deep shadows. Color palette: near-black navy, deep midnight blue, indigo, restrained cyan, subtle muted peach. Constraints: original image only; no text, no letters, no numbers, no logo, no UI elements, no cards, no icons, no people, no buildings, no watermark; do not show a whole dashboard or device; avoid overly bright sky, starfield, fog that obscures mountains, or vivid neon.

### Evidence footer planet

Use case: stylized-concept; Asset type: website evidence-panel footer background, intended to display in a compact bottom-right panel around 280 by 200 px; Input images: the provided ProjectFlow dashboard screenshot is a reference image for visual style, palette, mood, and its subtle lower-right planetary vignette only; do not reproduce its UI. Primary request: Create one original, clean square cosmic background. Near-black navy outer space with a large dark planet only partially entering the frame from the bottom-right corner. Give the planet a very subtle indigo and cyan atmospheric rim, with restrained blue haze around the lower-right edge. Keep the upper-left two-thirds nearly black and uncluttered. Style/medium: premium cinematic digital environment art for a dark product UI, minimal, subtle, controlled, realistic atmosphere. Composition/framing: square canvas; dark planet cropped by the lower-right edge; upper-left almost empty black navy negative space; no border. Lighting/mood: quiet, mysterious, restrained indigo/cyan rim lighting; very low contrast in the background; no dramatic flare. Color palette: near-black navy, deep indigo, faint cyan-blue, minimal cool haze. Constraints: original image only; no text, no letters, no numbers, no logo, no UI elements, no cards, no icons, no people, no spacecraft, no stars obvious enough to read as a starfield, no watermark; do not show a whole dashboard or device; avoid bright neon, galaxies, planets centered in frame, lightning, or bright white light.
