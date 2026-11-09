<template>
  <div class="landing" ref="rootRef">
    <!-- 顶部品牌栏 -->
    <header class="landing-top">
      <div class="brand">
        <svg class="brand-logo" viewBox="0 0 36 36" aria-hidden="true">
          <defs>
            <linearGradient id="wc-logo-g" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stop-color="#4f8cff" />
              <stop offset="1" stop-color="#1d4ed8" />
            </linearGradient>
          </defs>
          <path d="M5 17.5 L30.5 4.5 L22.5 30 L16.8 19.6 Z" fill="url(#wc-logo-g)" />
          <path d="M16.8 19.6 L30.5 4.5 L22.5 30 Z" fill="#9cc3ff" />
        </svg>
        <span class="brand-name">WebCraft</span>
      </div>
      <div class="top-actions">
        <a v-if="!loggedIn" class="login-link" href="javascript:void(0)" @click="router.push('/login')">登录</a>
        <button v-else class="ghost-btn" @click="router.push('/workspace')">进入工作台</button>
      </div>
    </header>

    <main class="landing-main">
      <!-- 左侧文案区 -->
      <section class="hero-copy">
        <h1 class="hero-title">
          <span class="t-line">一键生成，</span>
          <span class="t-line">你的专属网站</span>
        </h1>
        <p class="hero-desc">
          <span class="d-line">借助先进的AI技术，</span>
          <span class="d-line">只需简单描述，</span>
          <span class="d-line">即可在几秒钟内构建出</span>
          <span class="d-line">专业级的高转化率网页。</span>
        </p>
        <button class="cta-btn" @click="startBuild">
          免费开始构建
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="4" y1="12" x2="20" y2="12"></line>
            <polyline points="13 5 20 12 13 19"></polyline>
          </svg>
        </button>
        <div class="hero-features">
          <span class="feature">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
            </svg>
            无需编写代码
          </span>
          <span class="feature">响应式设计</span>
          <span class="feature">
            极速部署
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"></path>
              <path d="M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"></path>
              <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"></path>
              <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"></path>
            </svg>
          </span>
        </div>
      </section>

      <!-- 右侧轮播区（Embla：循环 + 自动播放 + 滚轮/拖拽/圆点/键盘 + 进度条） -->
      <section class="hero-visual" ref="visualRef">
        <div class="hero-parallax" ref="parallaxRef">
          <div
            class="carousel"
            tabindex="0"
            aria-roledescription="carousel"
            @keydown="onKeyNav"
          >
            <div class="carousel__viewport" ref="emblaRef">
              <div class="carousel__container">
                <div
                  v-for="(slide, i) in slides"
                  :key="slide.image"
                  class="carousel__slide"
                  :aria-hidden="i !== selected"
                >
                  <img
                    class="carousel__image"
                    :src="slide.image"
                    :alt="slide.title"
                    :loading="i === 0 ? 'eager' : 'lazy'"
                    draggable="false"
                  />
                  <div class="carousel__overlay">
                    <span class="carousel__tag">{{ slide.tag }}</span>
                    <h2 class="carousel__title">{{ slide.title }}</h2>
                    <p class="carousel__caption">{{ slide.caption }}</p>
                  </div>
                </div>
              </div>
            </div>
            <div class="carousel__bar">
              <div class="carousel__dots">
                <button
                  v-for="(slide, i) in slides"
                  :key="'dot-' + slide.image"
                  type="button"
                  :class="['carousel__dot', { 'is-active': i === selected }]"
                  :aria-label="'跳转到第 ' + (i + 1) + ' 张'"
                  :aria-current="i === selected"
                  @click="scrollTo(i)"
                />
              </div>
              <span class="carousel__counter">
                {{ String(selected + 1).padStart(2, '0') }} / {{ String(slides.length).padStart(2, '0') }}
              </span>
            </div>
            <div class="carousel__progress">
              <div class="carousel__progress-fill" :style="{ transform: 'scaleX(' + (progress || 0) + ')' }"></div>
            </div>
          </div>
        </div>
        <span class="sparkle sparkle-1">✦</span>
        <span class="sparkle sparkle-2">✦</span>
        <span class="sparkle sparkle-3">✧</span>
        <span class="sparkle sparkle-4">✦</span>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import useEmblaCarousel from 'embla-carousel-vue'
import Autoplay from 'embla-carousel-autoplay'

const router = useRouter()
const rootRef = ref()
const visualRef = ref()
const parallaxRef = ref()
const loggedIn = ref(!!localStorage.getItem('accessToken'))

// 轮播内容：五张 WebCraft 作品展示图
const slides = [
  { tag: 'architecture', title: 'LUMINA 建筑事务所', caption: '米白极简 · 大图与大量留白，展示空间作品', image: '/carousel/slide-1.png' },
  { tag: 'illustration', title: 'DoodleCraft 涂鸦造物', caption: '手绘彩铅风格 · 文创周边电商首页', image: '/carousel/slide-2.png' },
  { tag: 'retail', title: 'NEON CHAOS 霓虹混沌', caption: '荧光撞色 · 拼贴粗野主义的潮流电商', image: '/carousel/slide-3.png' },
  { tag: 'studio', title: 'VORTEX 设计工作室', caption: '洋红 × 荧光绿 · BREAK THE GRID 视觉宣言', image: '/carousel/slide-4.png' },
  { tag: 'exhibition', title: '边界之外 Beyond Boundaries', caption: '黑白排版 × 蓝橙撞色 · 当代艺术展专题页', image: '/carousel/slide-5.png' }
]

// Embla：循环 + 自动播放（悬停暂停）
const autoplay = Autoplay({ delay: 4000, stopOnMouseEnter: true, stopOnInteraction: false })
const [emblaRef, emblaApi] = useEmblaCarousel({ loop: true }, [autoplay])

const selected = ref(0)
const progress = ref(0)

const scrollTo = (index) => emblaApi.value?.scrollTo(index)

const onKeyNav = (event) => {
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    emblaApi.value?.scrollPrev()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    emblaApi.value?.scrollNext()
  }
}

// 滚轮切换（节流，避免触控板惯性连跳）
const WHEEL_COOLDOWN = 500
let lastWheelAt = 0
const onWheel = (event) => {
  if (!event.deltaY) return
  event.preventDefault()
  const now = Date.now()
  if (now - lastWheelAt < WHEEL_COOLDOWN) return
  lastWheelAt = now
  if (event.deltaY > 0) emblaApi.value?.scrollNext()
  else emblaApi.value?.scrollPrev()
}

let wheelNode = null

watch(emblaApi, (api) => {
  if (!api) return
  const clamp = (value) => Math.min(1, Math.max(0, value))
  const onSelect = () => (selected.value = api.selectedScrollSnap())
  // loop 模式下 scrollProgress 到最后一页只到 (n-1)/n，补上一页的占比才是「当前页 / 总页数」
  const onScroll = () => (progress.value = clamp(api.scrollProgress() + 1 / slides.length))
  // 停稳后按页码校正，确保「01/05 → 20% … 05/05 → 100%」严格对齐
  const onSettle = () => (progress.value = clamp((api.selectedScrollSnap() + 1) / slides.length))
  api.on('select', onSelect)
  api.on('scroll', onScroll)
  api.on('settle', onSettle)
  onSelect()
  onScroll()
  wheelNode = api.rootNode()
  wheelNode.addEventListener('wheel', onWheel, { passive: false })
})

const startBuild = () => {
  router.push(loggedIn.value ? '/workspace' : '/login')
}

let ctx = null
let onPointerMove = null

onMounted(() => {
  ctx = gsap.context(() => {
    // 入场时间线：品牌 → 标题逐行 → 描述逐行 → 按钮 → 特性 → 轮播
    const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
    tl.from('.landing-top', { y: -24, opacity: 0, duration: 0.7 })
      .from('.t-line', { y: 46, opacity: 0, duration: 0.9, stagger: 0.16 }, '-=0.35')
      .from('.d-line', { y: 22, opacity: 0, duration: 0.6, stagger: 0.09 }, '-=0.55')
      .from('.cta-btn', { y: 22, opacity: 0, scale: 0.94, duration: 0.6 }, '-=0.35')
      .from('.feature', { y: 14, opacity: 0, duration: 0.5, stagger: 0.1 }, '-=0.3')
      .from(visualRef.value, { x: 90, opacity: 0, duration: 1.1 }, '-=1.0')
      .from('.sparkle', { scale: 0, opacity: 0, duration: 0.45, stagger: 0.12, ease: 'back.out(2)' }, '-=0.4')

    // 鼠标视差：轮播区随指针轻微漂移
    const pxTo = gsap.quickTo(parallaxRef.value, 'x', { duration: 1.1, ease: 'power3' })
    const pyTo = gsap.quickTo(parallaxRef.value, 'y', { duration: 1.1, ease: 'power3' })
    onPointerMove = (e) => {
      const nx = e.clientX / window.innerWidth - 0.5
      const ny = e.clientY / window.innerHeight - 0.5
      pxTo(nx * 28)
      pyTo(ny * 18)
    }
    window.addEventListener('pointermove', onPointerMove)
  }, rootRef.value)
})

onBeforeUnmount(() => {
  if (onPointerMove) window.removeEventListener('pointermove', onPointerMove)
  if (wheelNode) wheelNode.removeEventListener('wheel', onWheel)
  if (ctx) ctx.revert()
})
</script>

<style scoped>
.landing {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #17172b;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background:
    radial-gradient(42% 46% at 80% 38%, rgba(147, 180, 255, 0.16) 0%, transparent 70%),
    radial-gradient(34% 40% at 12% 86%, rgba(196, 181, 253, 0.14) 0%, transparent 72%),
    linear-gradient(160deg, #fbfcff 0%, #f2f5fc 55%, #e9edf8 100%);
}

/* ---------- 顶部品牌栏 ---------- */
.landing-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26px 56px 0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-logo {
  width: 34px;
  height: 34px;
  filter: drop-shadow(0 4px 10px rgba(37, 99, 235, 0.28));
}
.brand-name {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.3px;
  color: #17172b;
}
.login-link {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  text-decoration: none;
  padding: 8px 18px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px) saturate(160%);
  -webkit-backdrop-filter: blur(10px) saturate(160%);
  box-shadow: 0 2px 10px rgba(30, 41, 59, 0.06);
  transition: all 0.25s;
}
.login-link:hover {
  color: #2563eb;
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.16);
}
.ghost-btn {
  font-size: 14px;
  font-weight: 600;
  color: #2563eb;
  padding: 8px 18px;
  border-radius: 999px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(96, 141, 250, 0.45);
  backdrop-filter: blur(10px) saturate(160%);
  -webkit-backdrop-filter: blur(10px) saturate(160%);
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.1);
  transition: all 0.25s;
}
.ghost-btn:hover {
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 8px 22px rgba(37, 99, 235, 0.22);
}

/* ---------- 主体两栏 ---------- */
.landing-main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 0 56px 40px;
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}
.hero-copy {
  flex: 0 0 44%;
  max-width: 560px;
}
.hero-title {
  margin: 0 0 30px;
  font-size: 54px;
  line-height: 1.22;
  font-weight: 900;
  letter-spacing: 1px;
  color: #16162a;
}
.t-line {
  display: block;
}
.hero-desc {
  margin: 0 0 44px;
}
.d-line {
  display: block;
  font-size: 15.5px;
  line-height: 1.95;
  color: #4b5563;
}
.cta-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 15px 34px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 9px;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.25);
  transition: transform 0.25s, box-shadow 0.25s;
}
.cta-btn svg {
  width: 18px;
  height: 18px;
  transition: transform 0.25s;
}
.cta-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.25);
}
.cta-btn:hover svg {
  transform: translateX(4px);
}
.cta-btn:active {
  transform: translateY(-1px);
}
.hero-features {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 26px;
  margin-top: 46px;
}
.feature {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: #6b7280;
}
.feature svg {
  width: 15px;
  height: 15px;
  color: #94a3b8;
}

/* ---------- 右侧轮播 ---------- */
.hero-visual {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
}
.hero-parallax {
  width: min(680px, 100%);
  will-change: transform;
}
.carousel {
  position: relative;
  border-radius: 18px;
  outline: none;
}
.carousel:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 4px;
}
.carousel__viewport {
  overflow: hidden;
  border-radius: 18px;
  background: #e8edf7;
  cursor: grab;
  box-shadow: 0 30px 66px rgba(30, 41, 59, 0.18), inset 0 0 0 1px rgba(255, 255, 255, 0.6);
}
.carousel__viewport:active {
  cursor: grabbing;
}
.carousel__container {
  display: flex;
  touch-action: pan-y pinch-zoom;
}
.carousel__slide {
  position: relative;
  flex: 0 0 100%;
  min-width: 0;
  aspect-ratio: 16 / 10;
}
.carousel__image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
  user-select: none;
  -webkit-user-drag: none;
}
.carousel__overlay {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 96px 32px 26px;
  pointer-events: none;
  color: #fff;
  background: linear-gradient(
    to top,
    rgba(8, 12, 24, 0.84) 0%,
    rgba(8, 12, 24, 0.46) 46%,
    rgba(8, 12, 24, 0) 100%
  );
}
.carousel__tag {
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 999px;
}
.carousel__title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.2px;
}
.carousel__caption {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.78);
}
.carousel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 0 4px;
}
.carousel__dots {
  display: flex;
  align-items: center;
  gap: 8px;
}
.carousel__dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  background: rgba(30, 41, 59, 0.18);
  transition: width 0.3s ease, background 0.3s ease;
}
.carousel__dot:hover {
  background: rgba(30, 41, 59, 0.34);
}
.carousel__dot.is-active {
  width: 26px;
  background: #2563eb;
}
.carousel__counter {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.6px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}
.carousel__progress {
  height: 2px;
  margin-top: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(30, 41, 59, 0.1);
}
.carousel__progress-fill {
  width: 100%;
  height: 100%;
  border-radius: 999px;
  transform-origin: left center;
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}

/* 星光点缀（对应插画四周的闪烁星形） */
.sparkle {
  position: absolute;
  font-size: 22px;
  line-height: 1;
  pointer-events: none;
  animation: twinkle 2.6s ease-in-out infinite;
}
.sparkle-1 {
  top: 8%;
  right: 16%;
  color: #60a5fa;
}
.sparkle-2 {
  top: 34%;
  right: 4%;
  color: #fbbf24;
  font-size: 26px;
  animation-delay: 0.7s;
}
.sparkle-3 {
  bottom: 18%;
  left: 10%;
  color: #60a5fa;
  animation-delay: 1.3s;
}
.sparkle-4 {
  bottom: 8%;
  right: 26%;
  color: #f59e0b;
  font-size: 16px;
  animation-delay: 1.9s;
}
@keyframes twinkle {
  0%, 100% {
    opacity: 0.25;
    transform: scale(0.65) rotate(0deg);
  }
  50% {
    opacity: 1;
    transform: scale(1.2) rotate(24deg);
  }
}

/* ---------- 响应式 ---------- */
@media (max-width: 960px) {
  .landing-top {
    padding: 20px 24px 0;
  }
  .landing-main {
    flex-direction: column;
    padding: 32px 24px;
    gap: 40px;
  }
  .hero-copy {
    flex: none;
    max-width: 100%;
  }
  .hero-title {
    font-size: 38px;
  }
  .hero-visual {
    min-height: auto;
    width: 100%;
  }
  .hero-parallax {
    width: 100%;
  }
}
@media (max-width: 640px) {
  .carousel__overlay {
    padding: 64px 20px 18px;
  }
  .carousel__title {
    font-size: 17px;
  }
  .carousel__caption {
    font-size: 12px;
  }
}
</style>
