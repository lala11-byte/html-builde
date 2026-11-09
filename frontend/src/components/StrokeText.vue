<template>
  <span
    ref="rootRef"
    :class="['stroke-text', { 'stroke-text--hover': trigger === 'hover' }, className]"
    :style="rootStyle"
    role="img"
    :aria-label="String(text ?? '')"
  >
    <svg
      class="stroke-text__svg"
      :viewBox="viewBox"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      :style="box ? undefined : { visibility: 'hidden' }"
    >
      <defs>
        <clipPath v-if="fillMode === 'wipe' && wipeStart" :id="wipeId" clipPathUnits="userSpaceOnUse">
          <rect
            ref="wipeRectRef"
            :x="wipeStart.x"
            :y="wipeStart.y"
            :width="wipeStart.width"
            :height="wipeStart.height"
          />
        </clipPath>

        <filter v-if="dashArray" :id="erodeId" filterUnits="userSpaceOnUse" v-bind="region">
          <feMorphology operator="erode" :radius="Number(strokeWidth) / 2 + 0.75" />
        </filter>

        <mask v-if="dashArray" :id="revealId" maskUnits="userSpaceOnUse" v-bind="region">
          <text
            x="0"
            y="0"
            fill="none"
            stroke="#fff"
            :stroke-width="Number(strokeWidth) + 2"
            stroke-linejoin="round"
            stroke-linecap="round"
            :style="fontStyle"
          >
            <tspan v-for="(char, index) in characters" :key="'m-' + index" data-stroke-char>{{ char }}</tspan>
          </text>
          <text x="0" y="0" fill="#000" stroke="none" :filter="`url(#${erodeId})`" :style="fontStyle">
            <tspan v-for="(char, index) in characters" :key="'e-' + index">{{ char }}</tspan>
          </text>
        </mask>

        <filter v-if="castShadow" :id="shadowId" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB" v-bind="region">
          <feDropShadow
            ref="shadowRef"
            :dx="lift"
            :dy="lift"
            :stdDeviation="softness"
            :flood-color="shadowColor || strokeColor"
            :flood-opacity="SHADOW_OPACITY"
          />
        </filter>
      </defs>

      <text
        v-if="!dashArray"
        ref="strokeTextRef"
        class="stroke-text__stroke"
        x="0"
        y="0"
        fill="none"
        :stroke="strokeColor"
        :stroke-width="strokeWidth"
        stroke-linejoin="round"
        stroke-linecap="round"
        :style="fontStyle"
      >
        <tspan v-for="(char, index) in characters" :key="'s-' + index" data-stroke-char>{{ char }}</tspan>
      </text>

      <g :filter="castShadow ? `url(#${shadowId})` : undefined">
        <text
          class="stroke-text__fill"
          x="0"
          y="0"
          :fill="fillColor"
          stroke="none"
          :style="fontStyle"
          :clip-path="fillMode === 'wipe' && wipeStart ? `url(#${wipeId})` : undefined"
        >
          <tspan v-for="(char, index) in characters" :key="'f-' + index" data-fill-char>{{ char }}</tspan>
        </text>
      </g>

      <text
        v-if="dashArray"
        ref="strokeTextRef"
        class="stroke-text__stroke"
        x="0"
        y="0"
        fill="none"
        :stroke="strokeColor"
        :stroke-width="strokeWidth"
        stroke-linejoin="round"
        stroke-linecap="round"
        :stroke-dasharray="dashArray"
        :mask="`url(#${revealId})`"
        :style="fontStyle"
      >
        <tspan v-for="(char, index) in characters" :key="'sd-' + index">{{ char }}</tspan>
      </text>
    </svg>
  </span>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

if (typeof window !== 'undefined') {
  gsap.registerPlugin(ScrollTrigger)
}

const SHADOW_OPACITY = 0.5

const props = defineProps({
  text: { type: String, default: 'Draw Attention' },
  strokeColor: { type: String, default: '#60a5fa' },
  fillColor: { type: String, default: 'currentColor' },
  strokeWidth: { type: Number, default: 1.4 },
  drawDuration: { type: Number, default: 1.6 },
  fillDelay: { type: Number, default: 0.2 },
  stagger: { type: Number, default: 0.05 },
  ease: { type: String, default: 'power2.out' },
  trigger: { type: String, default: 'mount' },
  fillMode: { type: String, default: 'wipe' },
  wipeFrom: { type: String, default: 'left' },
  fontSize: { type: Number, default: 128 },
  fontWeight: { type: [Number, String], default: 800 },
  fontFamily: { type: String, default: 'inherit' },
  letterSpacing: { type: Number, default: -4 },
  reverse: { type: Boolean, default: false },
  strokeStyle: { type: String, default: 'solid' },
  dashLength: { type: Number, default: 10 },
  marching: { type: Boolean, default: false },
  hoverRedraw: { type: Boolean, default: false },
  shadow: { type: Boolean, default: false },
  shadowColor: { type: String, default: null },
  className: { type: String, default: '' },
  style: { type: Object, default: () => ({}) }
})

const rootRef = ref(null)
const strokeTextRef = ref(null)
const wipeRectRef = ref(null)
const shadowRef = ref(null)
const box = ref(null)

const uid = Math.random().toString(36).slice(2, 10)
const wipeId = `stroke-text-wipe-${uid}`
const revealId = `stroke-text-reveal-${uid}`
const erodeId = `stroke-text-erode-${uid}`
const shadowId = `stroke-text-shadow-${uid}`

const characters = computed(() => Array.from(String(props.text ?? '')))

const getDashPattern = () => {
  const width = Math.max(Number(props.strokeWidth) || 0, 0.1)
  const length = Math.max(Number(props.dashLength) || 0, 1)
  if (props.strokeStyle === 'dotted') {
    const gap = Math.max(length, width * 1.8)
    return { array: `0 ${gap}`, period: gap }
  }
  if (props.strokeStyle === 'dashed') {
    const on = Math.max(length - width, 0.5)
    const off = length * 0.6 + width
    return { array: `${on} ${off}`, period: on + off }
  }
  return null
}

const dash = computed(() => Math.max(props.fontSize * 7, 200))
const pattern = computed(() => getDashPattern())
const dashArray = computed(() => (pattern.value ? pattern.value.array : null))
const dashPeriod = computed(() => (pattern.value ? pattern.value.period : 0))
const lift = computed(() => props.fontSize * 0.06)
const softness = computed(() => props.fontSize * 0.016)
const castShadow = computed(() => props.shadow && props.fillMode !== 'none')

const fontStyle = computed(() => ({
  fontFamily: props.fontFamily,
  fontSize: `${props.fontSize}px`,
  fontWeight: props.fontWeight,
  letterSpacing: `${props.letterSpacing}px`
}))

const getWipeFrames = (target, wipeFrom) => {
  const end = { x: target.x, y: target.y, width: target.width, height: target.height }
  if (wipeFrom === 'right') return { start: { ...end, x: target.x + target.width, width: 0 }, end }
  if (wipeFrom === 'top') return { start: { ...end, height: 0 }, end }
  if (wipeFrom === 'bottom') return { start: { ...end, y: target.y + target.height, height: 0 }, end }
  return { start: { ...end, width: 0 }, end }
}

const viewBox = computed(() =>
  box.value
    ? `${box.value.x} ${box.value.y} ${box.value.width} ${box.value.height}`
    : `0 ${-props.fontSize} 600 ${props.fontSize * 1.3}`
)
const wipeStart = computed(() => (box.value ? getWipeFrames(box.value, props.wipeFrom).start : null))
const region = computed(() =>
  box.value
    ? {
        x: box.value.x - props.fontSize,
        y: box.value.y - props.fontSize,
        width: box.value.width + props.fontSize * 2,
        height: box.value.height + props.fontSize * 2
      }
    : {}
)

const rootStyle = computed(() => ({
  ...props.style,
  '--stroke-text-width': `${box.value ? box.value.width : 600}px`
}))

const measure = () => {
  const node = strokeTextRef.value
  if (!node) return
  let bbox
  try {
    bbox = node.getBBox()
  } catch {
    return
  }
  if (!bbox || !bbox.width) return

  const pad = Math.max(Number(props.strokeWidth) || 1, props.fontSize * 0.1)
  const next = {
    x: bbox.x - pad,
    y: bbox.y - pad,
    width: bbox.width + pad * 2,
    height: bbox.height + pad * 2
  }

  const prev = box.value
  if (
    prev &&
    Math.abs(prev.x - next.x) < 0.5 &&
    Math.abs(prev.width - next.width) < 0.5 &&
    Math.abs(prev.y - next.y) < 0.5 &&
    Math.abs(prev.height - next.height) < 0.5
  ) {
    return
  }
  box.value = next
}

let cleanupAnimation = () => {}
let resizeObserver = null

const setupAnimation = () => {
  const root = rootRef.value
  if (typeof window === 'undefined' || !root || !box.value) return () => {}

  const strokes = gsap.utils.toArray(root.querySelectorAll('[data-stroke-char]'))
  const fills = gsap.utils.toArray(root.querySelectorAll('[data-fill-char]'))
  const wipe = wipeRectRef.value
  const outline = strokeTextRef.value
  const drop = shadowRef.value
  if (!strokes.length) return () => {}

  const fillEnabled = props.fillMode !== 'none'
  const useWipe = fillEnabled && props.fillMode === 'wipe'
  const fillDuration = Math.max(0.4, props.drawDuration * 0.5)
  const staggerConfig = props.reverse ? { each: props.stagger, from: 'end' } : props.stagger
  const frames = getWipeFrames(box.value, props.wipeFrom)
  const lifted = { dx: lift.value, dy: lift.value, 'flood-opacity': SHADOW_OPACITY }
  const grounded = { dx: 0, dy: 0, 'flood-opacity': 0 }
  const targets = [...strokes, ...fills, wipe, drop].filter(Boolean)

  const setStart = () => {
    gsap.killTweensOf(targets)
    gsap.set(strokes, { strokeDasharray: dash.value, strokeDashoffset: dash.value, strokeOpacity: 1 })
    gsap.set(fills, { opacity: useWipe ? 1 : 0 })
    if (wipe) gsap.set(wipe, { attr: frames.start })
    if (drop) gsap.set(drop, { attr: grounded })
  }

  const setEnd = () => {
    gsap.killTweensOf(targets)
    gsap.set(strokes, { strokeDasharray: dash.value, strokeDashoffset: 0, strokeOpacity: 1 })
    gsap.set(fills, { opacity: fillEnabled ? 1 : 0 })
    if (wipe) gsap.set(wipe, { attr: fillEnabled ? frames.end : frames.start })
    if (drop) gsap.set(drop, { attr: fillEnabled ? lifted : grounded })
  }

  const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (prefersReducedMotion) {
    setEnd()
    return () => gsap.killTweensOf(targets)
  }

  const build = () => {
    setStart()
    const looping = props.trigger === 'loop'
    const tl = gsap.timeline({
      paused: true,
      repeat: looping ? -1 : 0,
      repeatDelay: looping ? 0.9 : 0,
      yoyo: looping,
      defaults: { overwrite: 'auto' }
    })

    tl.to(strokes, { strokeDashoffset: 0, duration: props.drawDuration, ease: props.ease, stagger: staggerConfig }, 0)

    if (useWipe && wipe) {
      tl.to(wipe, { attr: frames.end, duration: fillDuration, ease: 'power2.inOut' }, props.drawDuration + props.fillDelay)
    } else if (fillEnabled) {
      tl.to(
        fills,
        { opacity: 1, duration: fillDuration, ease: 'power2.out', stagger: staggerConfig },
        props.drawDuration + props.fillDelay
      )
    }

    if (drop && fillEnabled) {
      tl.to(
        drop,
        { attr: lifted, duration: fillDuration * 1.4, ease: 'power3.out' },
        props.drawDuration + props.fillDelay + fillDuration * 0.3
      )
    }

    return tl
  }

  let timeline = null
  let scrollTrigger = null
  let redraw = null
  const listeners = []

  const listen = (handler) => {
    root.addEventListener('pointerenter', handler)
    listeners.push(handler)
  }

  if (props.trigger === 'hover') {
    setEnd()
    listen(() => {
      timeline?.kill()
      timeline = build()
      timeline.play(0)
    })
  } else {
    timeline = build()
    if (props.trigger === 'scroll') {
      scrollTrigger = ScrollTrigger.create({
        trigger: root,
        start: 'top 82%',
        once: true,
        onEnter: () => timeline?.play(0)
      })
    } else {
      timeline.play(0)
    }

    if (props.hoverRedraw && props.trigger !== 'loop') {
      listen(() => {
        if (!timeline || timeline.progress() < 1 || redraw?.isActive()) return
        redraw?.kill()
        redraw = gsap
          .timeline()
          .to(strokes, { strokeOpacity: 0, duration: 0.2, ease: 'power1.out' })
          .set(strokes, { strokeDashoffset: dash.value, strokeOpacity: 1 })
          .to(strokes, { strokeDashoffset: 0, duration: props.drawDuration * 0.8, ease: props.ease, stagger: staggerConfig })
      })
    }
  }

  const march =
    dashArray.value && props.marching && outline
      ? gsap.fromTo(
          outline,
          { strokeDashoffset: 0 },
          { strokeDashoffset: -dashPeriod.value, duration: dashPeriod.value / (props.fontSize * 0.22), ease: 'none', repeat: -1 }
        )
      : null

  return () => {
    listeners.forEach((handler) => root.removeEventListener('pointerenter', handler))
    scrollTrigger?.kill()
    timeline?.kill()
    redraw?.kill()
    march?.kill()
    gsap.killTweensOf(targets)
  }
}

watch(
  () => [
    box.value,
    dash.value,
    props.drawDuration,
    props.fillDelay,
    props.stagger,
    props.ease,
    props.trigger,
    props.fillMode,
    props.wipeFrom,
    props.reverse,
    dashArray.value,
    dashPeriod.value,
    props.marching,
    props.hoverRedraw,
    castShadow.value,
    lift.value,
    props.fontSize
  ],
  () => {
    cleanupAnimation()
    cleanupAnimation = setupAnimation()
  },
  { flush: 'post' }
)

watch(
  () => [characters.value, props.fontFamily, props.fontSize, props.fontWeight, props.letterSpacing, props.strokeWidth],
  () => {
    nextTick(measure)
  },
  { flush: 'post' }
)

onMounted(() => {
  measure()
  document.fonts?.ready?.then(measure).catch(() => {})
  if (typeof ResizeObserver !== 'undefined' && rootRef.value) {
    resizeObserver = new ResizeObserver(measure)
    resizeObserver.observe(rootRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  cleanupAnimation()
})
</script>

<style scoped>
.stroke-text {
  display: block;
  width: 100%;
  line-height: 0;
}
.stroke-text--hover {
  cursor: pointer;
}
.stroke-text__svg {
  display: block;
  width: 100%;
  max-width: var(--stroke-text-width, 600px);
  height: auto;
  margin: 0 auto;
  overflow: visible;
}
.stroke-text__stroke,
.stroke-text__fill {
  user-select: none;
}
</style>