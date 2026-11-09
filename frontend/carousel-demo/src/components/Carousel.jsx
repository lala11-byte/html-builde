import { useCallback, useEffect, useRef, useState } from 'react'
import useEmblaCarousel from 'embla-carousel-react'
import Autoplay from 'embla-carousel-autoplay'

// 一次滚动手势的冷却时间，防止触控板惯性滚动连跳多张
const WHEEL_COOLDOWN = 500

/**
 * 通用图片轮播组件
 * 交互：滚轮切换 / 拖拽滑动 / 圆点跳转 / 键盘 ← → / 自动播放（悬停暂停）
 * @param {Array}  slides    [{ title, caption, tag, image }]
 * @param {number} interval  自动播放间隔（毫秒）
 */
export default function Carousel({ slides = [], interval = 4000 }) {
  // 用 ref 持有插件实例，避免每次渲染都创建新的 Autoplay
  const autoplayRef = useRef(Autoplay({ delay: interval, stopOnMouseEnter: true, stopOnInteraction: false }))

  const [viewportRef, emblaApi] = useEmblaCarousel({ loop: true }, [autoplayRef.current])

  const [selected, setSelected] = useState(0)
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    if (!emblaApi) return

    const onSelect = () => setSelected(emblaApi.selectedScrollSnap())
    const onScroll = () => setProgress(emblaApi.scrollProgress())

    emblaApi.on('select', onSelect)
    emblaApi.on('scroll', onScroll)
    onSelect()
    onScroll()

    return () => {
      emblaApi.off('select', onSelect)
      emblaApi.off('scroll', onScroll)
    }
  }, [emblaApi])

  // 滚轮驱动切换：一次滚动手势只推进一张，避免触控板惯性连跳
  useEffect(() => {
    if (!emblaApi) return
    const node = emblaApi.rootNode()
    let lastTrigger = 0

    const onWheel = (event) => {
      if (!event.deltaY) return
      event.preventDefault()

      const now = Date.now()
      if (now - lastTrigger < WHEEL_COOLDOWN) return
      lastTrigger = now

      if (event.deltaY > 0) emblaApi.scrollNext()
      else emblaApi.scrollPrev()
    }

    node.addEventListener('wheel', onWheel, { passive: false })
    return () => node.removeEventListener('wheel', onWheel)
  }, [emblaApi])

  const scrollTo = useCallback((index) => emblaApi?.scrollTo(index), [emblaApi])

  // 键盘左右方向键切换
  const onKeyDown = (event) => {
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      emblaApi?.scrollPrev()
    }
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      emblaApi?.scrollNext()
    }
  }

  if (!slides.length) return null

  return (
    <section className="carousel" tabIndex={0} onKeyDown={onKeyDown} aria-roledescription="carousel">
      <div className="carousel__viewport" ref={viewportRef}>
        <div className="carousel__container">
          {slides.map((slide, index) => (
            <div className="carousel__slide" key={slide.title} aria-hidden={index !== selected}>
              <img className="carousel__image" src={slide.image} alt={slide.title} loading={index === 0 ? 'eager' : 'lazy'} />
              <div className="carousel__overlay">
                <span className="carousel__tag">{slide.tag}</span>
                <h2 className="carousel__title">{slide.title}</h2>
                <p className="carousel__caption">{slide.caption}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="carousel__bar">
        <div className="carousel__dots">
          {slides.map((slide, index) => (
            <button
              type="button"
              key={slide.title}
              className={`carousel__dot${index === selected ? ' is-active' : ''}`}
              onClick={() => scrollTo(index)}
              aria-label={`跳转到第 ${index + 1} 张`}
              aria-current={index === selected}
            />
          ))}
        </div>
        <span className="carousel__counter">
          {String(selected + 1).padStart(2, '0')} / {String(slides.length).padStart(2, '0')}
        </span>
      </div>

      <div className="carousel__progress">
        <div className="carousel__progress-fill" style={{ transform: `scaleX(${progress || 0})` }} />
      </div>
    </section>
  )
}
