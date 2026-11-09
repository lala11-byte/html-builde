<template>
  <component :is="as" ref="containerRef" class="text-type" :class="className">
    <!-- 读屏用完整文本 -->
    <span class="text-type__sr">{{ prefix + textArray.join(' ') }}</span>
    <span class="text-type__content" aria-hidden="true">
      <span v-if="prefix" class="text-type__prefix" :style="{ color: prefixColor }">{{ prefix }}</span>
      <span :style="{ color: sentenceColor }">
        <span
          v-for="(item, i) in rendered"
          :key="item.key"
          :ref="(el) => setCharEl(el, i)"
          class="text-type__char"
          :class="{ 'text-type__char--selected': item.selected }"
          >{{ item.char }}</span
        >
      </span>
      <span ref="caretRef" class="text-type__caret">&#8203;</span>
    </span>
    <span
      v-if="showCursor"
      ref="cursorRef"
      aria-hidden="true"
      class="text-type__cursor"
      :class="['text-type__cursor--' + cursorStyle, { 'text-type__cursor--hidden': hideCursor }]"
      :style="cursorColorStyle"
    >
      <span ref="blinkRef" class="text-type__blink">
        <template v-if="cursorStyle === 'character'">{{ cursorCharacter }}</template>
        <span v-else class="text-type__shape"></span>
      </span>
    </span>
  </component>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import gsap from 'gsap'

const props = defineProps({
  /** 一句话，或多句循环打字 */
  text: { type: [String, Array], default: '' },
  /** 渲染的标签，如 p / h1 / span */
  as: { type: String, default: 'div' },
  className: { type: String, default: '' },
  /** 静态前缀文字 */
  prefix: { type: String, default: '' },
  prefixColor: { type: String, default: 'currentColor' },
  typingSpeed: { type: Number, default: 50 },
  initialDelay: { type: Number, default: 0 },
  pauseDuration: { type: Number, default: 2000 },
  deletingSpeed: { type: Number, default: 30 },
  /** backspace：逐字删除；select：先高亮再整体清除 */
  deleteMode: { type: String, default: 'backspace' },
  /** none / fade / blur / rise：单字出现效果 */
  typeEffect: { type: String, default: 'none' },
  variableSpeed: { type: Object, default: undefined },
  loop: { type: Boolean, default: true },
  showCursor: { type: Boolean, default: true },
  cursorStyle: { type: String, default: 'character' },
  cursorCharacter: { type: String, default: '|' },
  cursorColor: { type: String, default: 'currentColor' },
  cursorBlinkDuration: { type: Number, default: 0.5 },
  cursorGlide: { type: Boolean, default: true },
  hideCursorWhileTyping: { type: Boolean, default: false },
  /** 多句文本依次取色 */
  textColors: { type: Array, default: () => [] }
})

const EFFECTS = {
  fade: {
    keyframes: [{ opacity: 0 }, { opacity: 1 }],
    options: { duration: 360, easing: 'cubic-bezier(0.25, 1, 0.5, 1)' }
  },
  blur: {
    keyframes: [
      { opacity: 0, filter: 'blur(0.3em)' },
      { opacity: 1, filter: 'blur(0em)' }
    ],
    options: { duration: 520, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' }
  },
  rise: {
    keyframes: [
      { opacity: 0, top: '0.6em' },
      { opacity: 1, top: '0em' }
    ],
    options: { duration: 480, easing: 'cubic-bezier(0.34, 1.4, 0.64, 1)' }
  }
}

// 按字形切分（中文、emoji 都不会被拆坏）
const graphemes = (value) => {
  if (typeof Intl !== 'undefined' && Intl.Segmenter) {
    return Array.from(
      new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(value),
      (part) => part.segment
    )
  }
  return Array.from(value)
}

const containerRef = ref()
const caretRef = ref()
const cursorRef = ref()
const blinkRef = ref()

const textIndex = ref(0)
const count = ref(0)
const selected = ref(0)
const isDeleting = ref(false)

const textArray = computed(() =>
  Array.isArray(props.text) ? props.text.map(String) : [String(props.text ?? '')]
)
const index = computed(() => textIndex.value % textArray.value.length)
const chars = computed(() => graphemes(textArray.value[index.value] || ''))
const total = computed(() => chars.value.length)
const shown = computed(() => Math.min(count.value, total.value))

const sentenceColor = computed(() =>
  props.textColors.length ? props.textColors[index.value % props.textColors.length] : 'inherit'
)
const cursorColorStyle = computed(() =>
  props.cursorColor && props.cursorColor !== 'currentColor' ? { color: props.cursorColor } : undefined
)
const typing = computed(() => shown.value < total.value || isDeleting.value)
const hideCursor = computed(() => props.hideCursorWhileTyping && typing.value)

const rendered = computed(() => {
  const marked = Math.min(selected.value, shown.value)
  const list = []
  for (let i = 0; i < shown.value; i++) {
    list.push({
      key: `${index.value}-${i}`,
      char: chars.value[i],
      selected: i >= shown.value - marked
    })
  }
  return list
})

// ---------- 打字 / 删除节奏 ----------
let timer = null
const wait = (ms, next) => {
  timer = setTimeout(next, ms)
}

const step = () => {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  const minSpeed = props.variableSpeed?.min ?? props.typingSpeed
  const maxSpeed = props.variableSpeed?.max ?? props.typingSpeed
  const speed = () => minSpeed + Math.random() * (maxSpeed - minSpeed)

  if (!isDeleting.value) {
    if (shown.value < total.value) {
      const lead = shown.value === 0 ? props.initialDelay : 0
      wait(lead + speed(), () => {
        count.value = shown.value + 1
      })
    } else if (props.loop || index.value < textArray.value.length - 1) {
      wait(props.pauseDuration, () => {
        isDeleting.value = true
      })
    }
  } else if (shown.value > 0) {
    if (props.deleteMode === 'select') {
      if (selected.value < shown.value) {
        wait(props.deletingSpeed, () => {
          selected.value += 1
        })
      } else {
        wait(Math.max(160, props.deletingSpeed * 8), () => {
          selected.value = 0
          count.value = 0
        })
      }
    } else {
      wait(props.deletingSpeed, () => {
        count.value = shown.value - 1
      })
    }
  } else {
    selected.value = 0
    isDeleting.value = false
    textIndex.value = (index.value + 1) % textArray.value.length
  }
}

watch(
  () => [
    isDeleting.value,
    shown.value,
    total.value,
    selected.value,
    index.value,
    textArray.value,
    props.typingSpeed,
    props.initialDelay,
    props.pauseDuration,
    props.deletingSpeed,
    props.deleteMode,
    props.loop,
    props.variableSpeed
  ],
  step,
  { immediate: true, flush: 'post' }
)

// ---------- 单字出现动效 ----------
const charEls = []
const setCharEl = (el, i) => {
  if (el) charEls[i] = el
}

const animateChar = (node) => {
  const effect = EFFECTS[props.typeEffect]
  if (!node || !effect || typeof node.animate !== 'function') return
  node.animate(effect.keyframes, effect.options)
}

watch(shown, async (value, old) => {
  if (value <= old) return
  await nextTick()
  animateChar(charEls[value - 1])
})

// ---------- 光标跟随 + 闪烁 ----------
let lastPosition = null
let blinkTimeline = null
let resizeObserver = null

const placeCursor = (instant = false) => {
  const caret = caretRef.value
  const cursor = cursorRef.value
  if (!caret || !cursor) return

  const x = caret.offsetLeft
  const y = caret.offsetTop
  const last = lastPosition
  if (last && Math.abs(last.x - x) < 0.5 && Math.abs(last.y - y) < 0.5) return
  lastPosition = { x, y }

  if (instant || !last || !props.cursorGlide || Math.abs(last.y - y) > 1) {
    gsap.killTweensOf(cursor)
    gsap.set(cursor, { x, y })
  } else {
    gsap.to(cursor, { x, y, duration: 0.14, ease: 'power3.out', overwrite: true })
  }
}

watch(
  [shown, index, () => props.showCursor, () => props.cursorStyle],
  async () => {
    await nextTick()
    placeCursor()
  },
  { flush: 'post' }
)

onMounted(() => {
  placeCursor(true)

  const blink = blinkRef.value
  if (blink && props.showCursor && props.cursorBlinkDuration > 0) {
    gsap.set(blink, { opacity: 1 })
    const hold = props.cursorBlinkDuration * 0.7
    const fade = props.cursorBlinkDuration * 0.45
    blinkTimeline = gsap.timeline({ repeat: -1 })
    blinkTimeline
      .to(blink, { opacity: 0, duration: fade, ease: 'sine.inOut' }, hold)
      .to(blink, { opacity: 1, duration: fade, ease: 'sine.inOut' }, hold + fade + props.cursorBlinkDuration * 0.4)
  }

  if (containerRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => placeCursor(true))
    resizeObserver.observe(containerRef.value)
  }
})

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  if (blinkTimeline) blinkTimeline.kill()
  if (resizeObserver) resizeObserver.disconnect()
  if (cursorRef.value) gsap.killTweensOf(cursorRef.value)
  if (blinkRef.value) gsap.killTweensOf(blinkRef.value)
})
</script>

<style scoped>
.text-type {
  position: relative;
  display: inline-block;
  white-space: pre-wrap;
}

/* 读屏文本 */
.text-type__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.text-type__char {
  position: relative;
}

.text-type__char--selected {
  background-color: color-mix(in srgb, currentColor 25%, transparent);
}

.text-type__caret {
  display: inline-block;
  width: 0;
}

.text-type__cursor {
  position: absolute;
  top: 0;
  left: 0;
  white-space: pre;
  pointer-events: none;
  transition: opacity 0.2s ease;
  will-change: transform;
}

.text-type__cursor--hidden {
  opacity: 0;
}

.text-type__blink {
  display: inline-block;
}

.text-type__cursor--character .text-type__blink {
  margin-left: 0.25rem;
}

.text-type__shape {
  display: inline-block;
  margin-left: 0.06em;
  background-color: currentColor;
}

.text-type__cursor--bar .text-type__shape {
  width: max(2px, 0.07em);
  height: 1.1em;
  vertical-align: -0.2em;
  border-radius: 1px;
}

.text-type__cursor--block .text-type__shape {
  width: 0.56em;
  height: 1.1em;
  vertical-align: -0.2em;
  border-radius: 0.06em;
}

.text-type__cursor--underscore .text-type__shape {
  width: 0.56em;
  height: max(2px, 0.075em);
  vertical-align: -0.12em;
  border-radius: 1px;
}
</style>